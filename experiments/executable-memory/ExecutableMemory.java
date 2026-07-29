import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/*
 * ============================================================
 * Example:
 *     ExecutableMemory
 *
 * Demonstrates:
 *     - mmap()
 *     - mprotect()
 *     - executable memory pages
 *     - FFM downcall handles
 *     - executing generated machine code
 *
 * Platform:
 *     Linux x86-64
 *
 * Requires:
 *     JDK 22+
 *     --enable-native-access=ALL-UNNAMED
 *
 * Tested with:
 *     JDK 25
 * ============================================================
 */
public final class ExecutableMemory {

    // Linux memory protection flags
    private static final int PROT_READ  = 0x1;
    private static final int PROT_WRITE = 0x2;
    private static final int PROT_EXEC  = 0x4;

    // Linux mmap flags
    private static final int MAP_PRIVATE   = 0x02;
    private static final int MAP_ANONYMOUS = 0x20;

    /*
     * mmap() returns MAP_FAILED, defined as (void *) -1,
     * when the mapping cannot be created.
     */
    private static final long MAP_FAILED_ADDRESS = -1L;

    /*
     * x86-64 machine code:
     *
     *   B8 2A 00 00 00    mov eax, 42
     *   C3                ret
     *
     * According to the System V AMD64 ABI, an integer return
     * value is passed to the caller through the EAX register.
     */
    private static final byte[] CODE = {
            (byte) 0xB8,
            0x2A, 0x00, 0x00, 0x00,
            (byte) 0xC3
    };

    private ExecutableMemory() {
    }

    public static void main(String[] args) throws Throwable {
        verifyPlatform();

        Linker linker = Linker.nativeLinker();
        SymbolLookup libc = linker.defaultLookup();

        MethodHandle mmap = linker.downcallHandle(
                libc.findOrThrow("mmap"),
                FunctionDescriptor.of(
                        ADDRESS,   // void* return value
                        ADDRESS,   // void* addr
                        JAVA_LONG, // size_t length
                        JAVA_INT,  // int prot
                        JAVA_INT,  // int flags
                        JAVA_INT,  // int fd
                        JAVA_LONG  // off_t offset
                )
        );

        MethodHandle mprotect = linker.downcallHandle(
                libc.findOrThrow("mprotect"),
                FunctionDescriptor.of(
                        JAVA_INT,  // int return value
                        ADDRESS,   // void* addr
                        JAVA_LONG, // size_t len
                        JAVA_INT   // int prot
                )
        );

        MethodHandle munmap = linker.downcallHandle(
                libc.findOrThrow("munmap"),
                FunctionDescriptor.of(
                        JAVA_INT,  // int return value
                        ADDRESS,   // void* addr
                        JAVA_LONG  // size_t length
                )
        );

        MethodHandle getpagesize = linker.downcallHandle(
                libc.findOrThrow("getpagesize"),
                FunctionDescriptor.of(JAVA_INT)
        );

        int pageSize = (int) getpagesize.invokeExact();

        /*
         * Allocate the page as Read/Write only.
         *
         * This follows the W^X principle:
         *
         *     RW -> write machine code -> RX
         *
         * The page is never writable and executable at the same time.
         */
        MemorySegment address = (MemorySegment) mmap.invokeExact(
                MemorySegment.NULL,
                (long) pageSize,
                PROT_READ | PROT_WRITE,
                MAP_PRIVATE | MAP_ANONYMOUS,
                -1,
                0L
        );

        if (address.address() == MAP_FAILED_ADDRESS) {
            throw new IllegalStateException("mmap() failed");
        }

        /*
         * mmap() returns a raw native address represented by a
         * zero-length MemorySegment.
         *
         * Reinterpret it as a segment spanning the allocated page so
         * that bounds-checked FFM access can be performed within the
         * declared region.
         *
         * The programmer remains responsible for ensuring that the
         * address and declared size correspond to a valid native mapping.
         */
        MemorySegment executablePage = address.reinterpret(pageSize);

        try {
            executablePage
                    .asSlice(0, CODE.length)
                    .copyFrom(MemorySegment.ofArray(CODE));

            int protectResult = (int) mprotect.invokeExact(
                    address,
                    (long) pageSize,
                    PROT_READ | PROT_EXEC
            );

            if (protectResult != 0) {
                throw new IllegalStateException("mprotect() failed");
            }

            /*
             * Generated native function signature:
             *
             *     int function(void)
             */
            MethodHandle function = linker.downcallHandle(
                    address,
                    FunctionDescriptor.of(JAVA_INT)
            );

            int result = (int) function.invokeExact();

            System.out.println("Result: " + result);
        } finally {
            int unmapResult = (int) munmap.invokeExact(
                    address,
                    (long) pageSize
            );

            if (unmapResult != 0) {
                System.err.println("munmap() failed");
            }
        }
    }

    /*
     * The generated bytes contain x86-64 instructions and assume
     * the Linux System V AMD64 ABI.
     *
     * Executing them on another architecture could terminate the
     * process with an illegal-instruction fault.
     */
    private static void verifyPlatform() {
        String operatingSystem = System.getProperty("os.name", "");
        String architecture = System.getProperty("os.arch", "");

        boolean isLinux = "Linux".equalsIgnoreCase(operatingSystem);
        boolean isX86_64 =
                "amd64".equalsIgnoreCase(architecture)
                        || "x86_64".equalsIgnoreCase(architecture);

        if (!isLinux || !isX86_64) {
            throw new UnsupportedOperationException(
                    "This example requires Linux x86-64, but detected "
                            + operatingSystem + " " + architecture
            );
        }
    }
}