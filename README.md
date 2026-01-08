# Jardiff

[![Maven Central](https://img.shields.io/maven-central/v/io.github.bric3.jardiff/jardiff.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.bric3.jardiff/jardiff)
[![GitHub release](https://img.shields.io/github/release/bric3/jardiff.svg?label=Github%20Release)](https://github.com/bric3/jardiff/releases/latest)
[![Build Status](https://img.shields.io/github/actions/workflow/status/bric3/jardiff/ci.yml?branch=main&label=build)](https://github.com/bric3/jardiff/actions?query=branch%3Amain)
[![License](https://img.shields.io/github/license/bric3/jardiff.svg)](LICENSE)

Jardiff is a command-line tool for comparing the contents of JAR files and directories. It provides detailed, line-based diffs of class files and resources, making it easy to spot changes between builds, releases, or different versions of Java/Kotlin projects.

> [!NOTE]
> This project is not affiliated with Lightbend or the original jardiff project.
> 
> While [lightbend-labs/jardiff](https://github.com/lightbend-labs/jardiff) and this tool share the same name and similar goals, they have different features:
> 
> The **lightbend-labs/jardiff** focuses on Scala projects, and it has a some useful flags to tweak the bytecode output (ordering, suppress private members). This tool also has a mode to create a git repository to leverage git diff capabilities.
> 
> While this **bric3/jardiff** tool offers a more versatile tool to inspect class differences
> * Additional to the usual diff output, it provides statistics (`--stat`) and status (`--status`)
>   modes similar to git diff/status
> * Supports class extension coalescing, in case classes are renamed to other extensions like `.bin`, `.clazz`, etc.
> * Various text representation modes for class files
> * Flexible glob pattern filtering for including/excluding files
> * Offers limited support for non-binary text files

Example output in default mode:

```diff
--- foo/bar/qux/Baz.class
+++ foo/bar/qux/Baz.class
@@ -6,1548 +6,17 @@
   // access flags 0x9
   public static create()Lfoo/bar/ZuulMatcher;
     NEW foo/bar/ZuulMatcher
     DUP
-    LDC 15
+    LDC 3
     ANEWARRAY foo/bar/Zuul
     DUP
     LDC 0
     NEW foo/bar/Zuul
     DUP
-    LDC 14
-    ANEWARRAY java/lang/String
-    DUP
     LDC 0
-    LDC "foo.bar.qux.T:18"
-    AASTORE
-    DUP
-    LDC 1
-    LDC "foo.bar.qux.U:33"
-    AASTORE
-    DUP
-    LDC 2
-    LDC "foo.bar.qux.V:49"
-    AASTORE
-    DUP
-    LDC 3
-    LDC "foo.bar.qux.W:68"
-    AASTORE
-    DUP
 ...
+    LDC 2
     NEW foo/bar/Zuul
     DUP
     LDC 0
     ANEWARRAY java/lang/String
```

Or with the `--stat` mode

```
D  foo/bar/qux/Zuul.class
 D foo/bar/qux/Zig.class
M  foo/bar/qux/Baz.class
   foo/bar/qux/Zorg.class
```

Or with the `--status` mode

```
 foo/bar/qux/Zuul.class | 42 ++++++++++++++++++++++++++++++++----------
 foo/bar/qux/Zig.class  |  1 -
 foo/bar/qux/Baz.class  | 34 ++++++++++++++++++++++------------
 foo/bar/qux/Zorg.class |  0 
 4 files changed, 54 insertions(+), 23 deletions(-)
```

## Features

Other tools didn't have the feature I wanted, or they were impractical to use, so I made my own.

* Compare JARs and directories recursively
* Line-based diffs for each files
* Class file comparison using different strategy to produce text
   * ASM's Textify (_default_)
   * Class outline (version, is kotlin/groovy class, synthetic or bridge members)
   * Class File Version only
* Binary diff as sha-1 hashes
* Include glob patterns (for the relative paths inside the jars/directories)
* Exclude glob patterns (for the relative paths inside the jars/directories)
* Supports `--exit-code` for CI/CD pipelines

Features planned for future releases... :
* Ignoring debug information in class files (like line numbers, local variable names, etc.)
* Append Koltin/Scala/Groovy detection to regular class text output
* Sort members alphabetically
* Replace ASM by the Class file API (Need JDK 24+)
* Better terminal integration, ideas: pager support, colors configuration, auto-detection of `delta`, etc.

## Usage

> [!CAUTION] 
> This tool needs a JDK11 to build and run. Example with `mise`
> ```shell
> $ mise exec java@corretto-11 -- java -jar jardiff-0.1.0-SNAPSHOT.jar
> ```

> [!TIP]
> The jar on the Github release is an executable jar, so you can run it directly with `./jardiff-0.1.0-SNAPSHOT.jar {left} {right}` after downloading it (`chmod`ing it executable as needed).

Build it `./gradlew build`, then run it:

```shell
$ jardiff --help
Usage: jardiff [-hVv] [--exit-code] [--class-text-producer=<tool>]
               [--color=<when>] [-c=<extension>[,<extension>...]]... [-e=<glob>
               [,<glob>...]]... [-i=<glob>[,<glob>...]]... [--status | --stat]
               <left> <right>
Compares two JAR files or directories and reports differences.
      <left>           The JAR file or directory to compare.
      <right>          The JAR file or directory to compare.
  -c, --class-exts, --coalesce-classe-exts=<extension>[,<extension>...]
                       Coalesce class files with the given extensions, in
                       addition to the usual 'class', i.e. makes classes
                       named 'Foo.class' and 'Foo.bin' aliased to the same
                       file same entry. Also this enables the file to be
                       compared on bytecode level Takes a comma separated
                       list, e.g. 'classdata' or 'raw,bin,clazz'.
      --class-text-producer=<tool>
                       Tool used to produce class text, possible values:
                       asm-textifier, class-file-version, class-outline
                       Default: 'asm-textifier'
      --color=<when>   Control when to use color output:
                       always, auto, never
                       Default: 'auto'
  -e, --exclude=<glob>[,<glob>...]
                       Glob exclude patterns (comma separated), e.g.
                       '**/raw*/**', or '**/*.bin'.
      --exit-code      Make jardiff exit with codes similar to diff(1).
                       That is, it exits with 1 if there were differences
                       and 0 means no differences.
  -h, --help           Show this help message and exit.
  -i, --include=<glob>[,<glob>...]
                       Glob include patterns (comma separated), e.g.
                       '**/raw*/**', or '**/*.bin'.
      --stat           Show statistics output (like 'git diff --stat').
                       Displays file-by-file statistics with
                         additions/deletions.
      --status         Show short status output (like 'git status --short').
                       Displays two-column XY status for each file.
  -v                   Specify multiple -v options to increase verbosity.
                       For example, '-v -v' or '-vv'.
  -V, --version        Print version information and exit.
```

> [!TIP]
> Use shell features, e.g. in Bash, ZSH instead of typing twice long folders use the [brace expansion](https://www.gnu.org/software/bash/manual/html_node/Brace-Expansion.html#Brace-Expansion-1) :
> ```shell
> $ java -jar jardiff-0.1.0-SNAPSHOT.jar /Users/brice.dutheil/path/to/repositories/project{-original,-with-changes}/submodule/submodule/submodule/build/classes/java/main
> ```


Also, you can run it from Gradle:

```shell
$ ./gradlew run --args="{left} {right}"
```

- `{left}` and `{right}` can be paths to JAR files or directories.
- The tool outputs a summary and detailed diff of all differing files.

## Building

To build the project:

```shell
$ ./gradlew build
```

## Dependencies

- Kotlin
- Picocli
- ASM
- java-diff-utils
- Byte Buddy
- Apache Tika (for charset detection)

## License

Copyright 2025 Brice Dutheil

Unless otherwise noted, all components are licenced under the [Mozilla Public License Version 2.0](./LICENSE).
