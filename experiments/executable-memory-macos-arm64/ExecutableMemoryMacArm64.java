
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.ByteOrder;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/*
 * ============================================================
 * Example:
 *     ExecutableMemoryMacArm64
 *
 * Demonstrates:
 *     - mmap()
 *     - mprotect()
 *     - AArch64 machine instructions
 *     - sys_icache_invalidate()
 *     - calling generated machine code using Java FFM
 *     - munmap()
 *
 * Platform:
 *     macOS on Apple Silicon (AArch64)
 *
 * Requirements:
 *     JDK 22+
 *
 * Generated function:
 *
 *     int function(void) {
 *         return 42;
 *     }
 *
 * Assembly:
 *
 *     mov w0, #42
 *     ret
 *
 * Note:
 *     This example uses RW -> RX page protection.
 *     macOS security policy may reject executable
 *     anonymous memory in some processes.
 *
 * ============================================================
 */
public class ExecutableMemoryMacArm64 {

    private static final int PROT_READ = 0x01;
    private static final int PROT_WRITE = 0x02;
    private static final int PROT_EXEC = 0x04;

    private static final int MAP_PRIVATE = 0x0002;
    private static final int MAP_ANONYMOUS = 0x1000;

    private static final byte[] MACHINE_CODE = {
            // mov w0, #42
            (byte) 0x40, (byte) 0x05,
            (byte) 0x80, (byte) 0x52,

            // ret
            (byte) 0xC0, (byte) 0x03,
            (byte) 0x5F, (byte) 0xD6
    };

    public static void main(String[] args) throws Throwable {

        verifyPlatform();

        Linker linker = Linker.nativeLinker();
        SymbolLookup libc = linker.defaultLookup();

        /*
         * Native function declarations.
         */

        MethodHandle getpagesize = linker.downcallHandle(
                findSymbol(libc, "getpagesize"),
                FunctionDescriptor.of(JAVA_INT)
        );

        MethodHandle mmap = linker.downcallHandle(
                findSymbol(libc, "mmap"),
                FunctionDescriptor.of(
                        ADDRESS,
                        ADDRESS,
                        JAVA_LONG,
                        JAVA_INT,
                        JAVA_INT,
                        JAVA_INT,
                        JAVA_LONG
                )
        );

        MethodHandle mprotect = linker.downcallHandle(
                findSymbol(libc, "mprotect"),
                FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        JAVA_LONG,
                        JAVA_INT
                )
        );

        MethodHandle munmap = linker.downcallHandle(
                findSymbol(libc, "munmap"),
                FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        JAVA_LONG
                )
        );

        /*
         * macOS instruction cache synchronization:
         *
         * void sys_icache_invalidate(
         *     void *start,
         *     size_t length
         * );
         */
        MethodHandle invalidateCache = linker.downcallHandle(
                findSymbol(libc, "sys_icache_invalidate"),
                FunctionDescriptor.ofVoid(
                        ADDRESS,
                        JAVA_LONG
                )
        );

        int pageSize = (int) getpagesize.invokeExact();

        if (pageSize <= 0) {
            throw new IllegalStateException("Invalid page size");
        }

        System.out.println("Page size: " + pageSize);

        /*
         * Step 1:
         * Allocate readable and writable memory.
         *
         * macOS uses MAP_ANONYMOUS = 0x1000.
         *
         * The page is initially not executable.
         */
        MemorySegment address = (MemorySegment) mmap.invokeExact(
                MemorySegment.NULL,
                (long) pageSize,
                PROT_READ | PROT_WRITE,
                MAP_PRIVATE | MAP_ANONYMOUS,
                -1,
                0L
        );

        if (address.address() == -1L) {
            throw new IllegalStateException("mmap failed");
        }

        /*
         * Step 2:
         * Establish the accessible memory bounds.
         *
         * mmap memory must be explicitly released
         * with munmap().
         */
        MemorySegment memory = address.reinterpret(pageSize);

        try {

            /*
             * Step 3:
             * Copy the ARM64 machine instructions
             * into the writable memory page.
             */
            MemorySegment.copy(
                    MemorySegment.ofArray(MACHINE_CODE),
                    0,
                    memory,
                    0,
                    MACHINE_CODE.length
            );

            /*
             * Step 4:
             * Synchronize the instruction cache.
             *
             * On Apple Silicon, the instruction
             * cache is not automatically coherent
             * with previously written data.
             */
            invalidateCache.invokeExact(
                    address,
                    (long) MACHINE_CODE.length
            );

            /*
             * Step 5:
             * Change memory permissions from RW to RX.
             *
             * Some macOS process configurations may
             * prohibit executable anonymous memory.
             */
            int protectResult = (int) mprotect.invokeExact(
                    address,
                    (long) pageSize,
                    PROT_READ | PROT_EXEC
            );

            if (protectResult != 0) {
                throw new IllegalStateException(
                        "mprotect(RX) failed. "
                        + "macOS may restrict executable memory."
                );
            }

            /*
             * Step 6:
             * Create a MethodHandle for the generated
             * native function.
             *
             * Signature:
             *     int function(void);
             */
            MethodHandle function = linker.downcallHandle(
                    address,
                    FunctionDescriptor.of(JAVA_INT)
            );

            /*
             * Step 7:
             * Execute the generated ARM64 instructions.
             */
            int result = (int) function.invokeExact();

            System.out.println("Result: " + result);

        } finally {

            /*
             * Step 8:
             * Release the native memory mapping.
             */
            int unmapResult = (int) munmap.invokeExact(
                    address,
                    (long) pageSize
            );

            if (unmapResult != 0) {
                throw new IllegalStateException(
                        "munmap failed"
                );
            }
        }
    }

    private static MemorySegment findSymbol(
            SymbolLookup lookup,
            String name
    ) {
        return lookup.find(name).orElseThrow(
                () -> new UnsatisfiedLinkError(
                        "Native symbol not found: " + name
                )
        );
    }

    private static void verifyPlatform() {

        String os = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT);

        String arch = System.getProperty("os.arch")
                .toLowerCase(Locale.ROOT);

        if (!os.contains("mac") || !arch.equals("aarch64")) {
            throw new UnsupportedOperationException(
                    "Requires macOS AArch64. Detected: "
                            + os + " / " + arch
            );
        }

        if (ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) {
            throw new UnsupportedOperationException(
                    "Requires little-endian AArch64"
            );
        }
    }
}

