### Executable Memory — macOS ARM64

Demonstrates dynamic machine code execution on Apple Silicon using the Java Foreign Function & Memory API.

The example:

- Allocates writable native memory using `mmap()`.
- Writes AArch64 machine instructions (`mov w0, #42; ret`).
- Synchronizes the instruction cache using `sys_icache_invalidate()`.
- Changes memory protection from writable to executable using `mprotect()`.
- Invokes the generated function through an FFM downcall handle.
- Releases the allocated memory using `munmap()`.

**Platform:** macOS AArch64 (Apple Silicon)

**Requirements:** JDK 22+

**Run:**

```bash
java --enable-native-access=ALL-UNNAMED ExecutableMemoryMacArm64.java
```

**Alternatively:**
```bash
javac ExecutableMemoryMacArm64.java
java --enable-native-access=ALL-UNNAMED ExecutableMemoryMacArm64
```

**Expected output:**

```text
Page size: 16384
Result: 42
```

The example demonstrates the RW → RX memory protection transition without using `MAP_JIT`. This behavior has been verified on Apple Silicon but is not guaranteed for all macOS process configurations.
