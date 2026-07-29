# Executable Memory

This experiment demonstrates that a modern Java program can allocate native memory, write raw machine instructions into it, mark the memory as executable, and invoke the generated code directly through the Foreign Function & Memory API.

The example is intentionally small, but it exposes several mechanisms commonly associated with runtimes, JIT compilers, native interoperability, and operating-system memory management.

## What the Example Does

`ExecutableMemory.java` performs the following steps:

1. obtains downcall handles for Linux functions from the default native lookup,
2. determines the operating-system page size,
3. allocates one native memory page using `mmap()`,
4. initially gives the page Read/Write permissions,
5. copies x86-64 machine code into the page,
6. changes the permissions to Read/Execute using `mprotect()`,
7. treats the page address as a native function,
8. invokes the generated function,
9. and releases the mapping using `munmap()`.

The generated function returns the integer value `42`.

## Platform Requirements

This version of the experiment assumes:

- Linux,
- an x86-64 processor,
- the System V AMD64 ABI,
- JDK 22 or newer.

The supplied machine-code bytes are architecture-specific. They must not be executed on AArch64, ARM32, RISC-V, or another instruction-set architecture.

Check the current architecture with:

```bash
uname -m
```

Expected output for this example:

```text
x86_64
```

## Generated Machine Code

The generated native function is equivalent to:

```asm
mov eax, 42
ret
```

Its byte representation is:

```text
B8 2A 00 00 00 C3
```

The instructions mean:

| Bytes | Instruction | Purpose |
|---|---|---|
| `B8 2A 00 00 00` | `mov eax, 42` | Places the return value `42` into the register used for integer return values. |
| `C3` | `ret` | Returns control to the caller. |

Under the System V AMD64 ABI, a 32-bit integer return value is placed in the `EAX` register.

## Native Functions Used

The experiment calls four functions exposed by the Linux C runtime.

### `getpagesize()`

Returns the system memory-page size.

Conceptual C signature:

```c
int getpagesize(void);
```

### `mmap()`

Creates a new virtual-memory mapping.

Conceptual C signature:

```c
void *mmap(
    void *addr,
    size_t length,
    int prot,
    int flags,
    int fd,
    off_t offset
);
```

The example requests:

- no fixed address,
- one page,
- Read/Write permissions,
- a private anonymous mapping,
- no backing file.

### `mprotect()`

Changes the access permissions of an existing mapping.

Conceptual C signature:

```c
int mprotect(
    void *addr,
    size_t len,
    int prot
);
```

The experiment changes the page from:

```text
Read + Write
```

to:

```text
Read + Execute
```

### `munmap()`

Removes the virtual-memory mapping.

Conceptual C signature:

```c
int munmap(
    void *addr,
    size_t length
);
```

It is called from a `finally` block so that the mapping is released even if a later operation fails.

## Foreign Function & Memory Concepts

### Native Linker

```java
Linker linker = Linker.nativeLinker();
```

The native linker provides access to the native ABI of the current platform. It can create method handles for native functions and invoke a native function located at a specific memory address.

### Default Symbol Lookup

```java
SymbolLookup libc = linker.defaultLookup();
```

The default lookup resolves symbols exposed to the current process, including the Linux C runtime functions used by this experiment.

### Function Descriptors

A `FunctionDescriptor` describes the native function signature.

For example:

```java
FunctionDescriptor.of(
        ADDRESS,
        ADDRESS,
        JAVA_LONG,
        JAVA_INT,
        JAVA_INT,
        JAVA_INT,
        JAVA_LONG
)
```

describes the argument and return layouts used by `mmap()` on the target platform.

### Downcall Handles

A downcall handle is a Java `MethodHandle` that invokes a native function.

The experiment creates downcall handles for `mmap()`, `mprotect()`, `munmap()`, and `getpagesize()`.

It later creates another downcall handle directly from the address containing the generated machine code.

### Memory Segments

The address returned by `mmap()` is represented as a `MemorySegment`.

A raw address returned from a native function does not initially carry the size of the mapping. The example therefore reinterprets it as a segment spanning the allocated page:

```java
MemorySegment executablePage = address.reinterpret(pageSize);
```

The Java byte array containing the instructions is also exposed as a memory segment:

```java
MemorySegment.ofArray(CODE)
```

The bytes are then copied into the mapped page.

## Execution Flow

```text
Java program
     │
     │ linker.downcallHandle(...)
     ▼
Linux libc
     │
     │ mmap(PROT_READ | PROT_WRITE)
     ▼
Native Read/Write memory page
     │
     │ copy machine-code bytes
     ▼
Page contains B8 2A 00 00 00 C3
     │
     │ mprotect(PROT_READ | PROT_EXEC)
     ▼
Native Read/Execute memory page
     │
     │ create downcall handle for its address
     ▼
CPU executes generated instructions
     │
     ▼
Java receives the integer 42
     │
     │ munmap(...)
     ▼
Mapping is released
```

## Why the Page Is Not RWX

The page is not created with Read/Write/Execute permissions.

Instead, the experiment follows this sequence:

```text
RW → write code → RX
```

This follows the **W^X** principle:

> A memory page should be writable or executable, but not both at the same time.

Keeping writable and executable permissions separate reduces the opportunity for writable data to be interpreted as executable code.

Some hardened environments may impose additional restrictions on executable mappings.

## Building and Running

From the experiment directory:

```bash
cd experiments/executable-memory
```

Compile the source:

```bash
javac ExecutableMemory.java
```

Run it with native access enabled:

```bash
java --enable-native-access=ALL-UNNAMED ExecutableMemory
```

Expected output:

```text
Result: 42
```

## Relation to JIT Compilation

This example is not a JIT compiler, but it manually demonstrates part of the same basic execution model.

A JIT compiler typically:

1. analyzes bytecode or another intermediate representation,
2. generates architecture-specific machine instructions,
3. stores the instructions in native memory,
4. makes the memory executable,
5. and transfers execution to the generated code.

This experiment manually performs the last three steps.

It therefore provides a small and tangible demonstration of how generated bytes can become instructions executed directly by the processor.

## Safety Considerations

This experiment deliberately leaves the normal managed safety boundary of the JVM.

Once the generated function is invoked, the JVM cannot validate the behavior of the machine code.

Incorrect code may:

- execute an illegal instruction,
- read or write an invalid address,
- violate the calling convention,
- corrupt registers or the stack,
- fail to return,
- or terminate the entire JVM process.

Do not replace the byte array with code from an untrusted source.

A Java exception cannot reliably recover from arbitrary native memory corruption or a fatal processor exception.

## ABI Assumptions

The generated code must obey the platform calling convention expected by the FFM linker.

This example is intentionally simple:

- it accepts no arguments,
- it returns one integer,
- it modifies only the register used for the return value,
- and it immediately returns.

More complex generated functions would need to respect rules for:

- argument registers,
- callee-saved registers,
- stack alignment,
- stack frames,
- floating-point and vector registers,
- and return-value representation.

## Limitations

The example does not currently:

- inspect `errno` after a failed native call,
- support non-Linux operating systems,
- generate code for multiple CPU architectures,
- flush an instruction cache on architectures that require it,
- provide automated tests,
- or isolate execution from the main JVM process.

These limitations are acceptable for the initial educational experiment but should be addressed before generalizing the approach.

## Troubleshooting

### Compilation fails because the FFM API is missing

Use JDK 22 or newer and verify:

```bash
java -version
javac -version
```

### Native access warning or failure

Start the program with:

```bash
--enable-native-access=ALL-UNNAMED
```

### Illegal instruction

Confirm that the machine is x86-64:

```bash
uname -m
```

The supplied instruction bytes are not portable to other architectures.

### Segmentation fault

A segmentation fault indicates that execution crossed into invalid native behavior. Verify:

- the machine-code bytes,
- the function descriptor,
- the memory permissions,
- the returned `mmap()` address,
- and the target architecture.

### `mprotect()` fails

The operating system or a hardened security policy may reject executable mappings. The example currently reports the failure but does not read and display `errno`.

## Educational Purpose

The value of this experiment is not the returned number `42`.

Its purpose is to make several normally hidden runtime mechanisms visible:

- native symbol lookup,
- native calling conventions,
- virtual-memory allocation,
- page permissions,
- raw machine-code representation,
- and the transfer of execution from Java to generated native instructions.
