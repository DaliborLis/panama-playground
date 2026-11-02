# Panama Playground

A collection of small, focused experiments exploring the boundary between modern Java, native code, operating-system APIs, and hardware.

The repository is primarily a learning laboratory for the Java **Foreign Function & Memory API** (FFM API), commonly associated with **Project Panama**. Each experiment is intended to demonstrate one low-level concept in isolation and explain not only how the code works, but also why it works.

## Goals

This repository explores how modern Java can:

- call native functions without JNI,
- access memory outside the Java heap,
- work with native pointers and data structures,
- interact with operating-system APIs,
- expose Java callbacks to native code,
- communicate with hardware through native libraries,
- and investigate mechanisms normally associated with systems programming.

The project is educational and experimental. It is not intended to provide a production-ready abstraction layer over native APIs.

## Requirements

General requirements:

- JDK 22 or newer,
- a platform supported by the individual experiment,
- explicit native access when required.

Some examples may need to be started with:

```bash
java --enable-native-access=ALL-UNNAMED ...
```

Platform and architecture requirements are documented separately for each experiment.

## Repository Structure

```text
panama-playground/
├── README.md
└── experiments/
    └── executable-memory/
        ├── ExecutableMemory.java
        └── README.md
```

Each experiment contains:

- a small, self-contained Java example,
- its own README with implementation details,
- platform assumptions,
- build and run instructions,
- expected output,
- and relevant safety considerations.

## Experiments

### Executable Memory

Allocates a native memory page through Linux `mmap()`, writes x86-64 machine code into it, changes the page protection from writable to executable, and invokes the generated function through the FFM API.

The generated function returns the integer value `42`.

[Read the Executable Memory documentation](experiments/executable-memory/README.md)

### Executable Memory — macOS ARM64

Allocates a native memory page using macOS `mmap()`, writes AArch64 machine instructions into it, synchronizes the instruction cache using `sys_icache_invalidate()`, changes the page protection from writable to executable, and invokes the generated function through the FFM API.

The generated function returns the integer value `42`.

Unlike the Linux x86-64 example, this experiment demonstrates architecture-specific instruction cache synchronization and the behavior of executable anonymous memory on Apple Silicon.

[Read the macOS ARM64 documentation](experiments/executable-memory-macos-arm64/README.md)

## Project Principles

Each experiment should:

- focus on one primary concept,
- remain small enough to understand,
- avoid unnecessary frameworks,
- document native APIs and ABI assumptions,
- explain relevant FFM concepts,
- include reproducible build and run instructions,
- identify architecture-specific behavior,
- and describe important safety risks.

The objective is not only to make the code work, but to build an accurate mental model of the interaction between Java, native code, the operating system, and the processor.

## Planned Topics

Possible future experiments include:

### Native Functions and Memory

- basic libc calls,
- native strings,
- structs and unions,
- pointers and arrays,
- memory-mapped files,
- shared memory,
- native error handling and `errno`.

### Callbacks

- passing Java callbacks to C,
- function pointers,
- native-to-Java upcalls,
- `qsort()` with a Java comparator.

### Linux APIs

- `epoll`,
- `eventfd`,
- `timerfd`,
- pipes,
- Unix domain sockets,
- process and system information.

### Hardware Access

- USB device enumeration through `libusb`,
- USB descriptors and control transfers,
- serial-port communication,
- GPIO through `libgpiod`,
- Video4Linux device access.

### Performance

- `MemorySegment` and the Vector API,
- native call overhead,
- FFM versus JNI,
- native memory-copy experiments,
- simple runtime code generation.

## Safety

Some experiments intentionally cross the normal safety boundary of the JVM.

Incorrect native calls, invalid pointers, incompatible machine code, or incorrect ABI assumptions may:

- crash the JVM process,
- corrupt process memory,
- trigger segmentation faults or illegal instructions,
- leak native resources,
- or create security vulnerabilities.

Run the examples only in a controlled development environment. Never execute machine code or load native data from an untrusted source.

## Status

The repository is a work in progress. APIs, examples, and directory structure may evolve as new experiments are added.

## License

This project is licensed under the [Apache License 2.0](LICENSE).

The license permits use, modification, and distribution of the source code, including commercial use, under the conditions described in the license. It also includes an explicit patent grant from contributors.

The examples are provided on an **AS IS** basis, without warranties or conditions of any kind.
