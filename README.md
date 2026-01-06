# Jardiff

Jardiff is a command-line tool for comparing the contents of JAR files and directories. It provides detailed, line-based diffs of class files and resources, making it easy to spot changes between builds, releases, or different versions of Java/Kotlin projects.
         
Example output in `diff` mode (the default):

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

Or with the `stat` mode

```
D  foo/bar/qux/Zuul.class
 D foo/bar/qux/Zig.class
M  foo/bar/qux/Baz.class
   foo/bar/qux/Zorg.class
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

  _(fallback mechanism, for non class-files, non-decoded text files)_

Unsupported at this time, if ever...
* Nested jars
* Ignoring debug information in class files (like line numbers, local variable names, etc.)
* More advanced binary diffs, i.e. showing bytes differences.

  _This one is unlikely_

## Usage
                 
> [!CAUTION] 
> This tool needs a JDK11 to build and run. Example with `mise`
> ```shell
> $ mise exec java@corretto-11 -- java -jar jardiff-0.1.0-SNAPSHOT.jar
> ```

Build it `./gradlew build`, then run it:

```shell
$ jardiff --help
Usage: jardiff [-hVv] [--exit-code] [--class-text-producer=<tool>]
               [-c=<extension>[,<extension>...]]... [-e=<glob>[,<glob>...]]...
               [-i=<glob>[,<glob>...]]... [--status | --stat] <left> <right>
Compares two JAR files or directories and reports differences.
      <left>        The JAR file or directory to compare.
      <right>       The JAR file or directory to compare.
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
  -e, --exclude=<glob>[,<glob>...]
                    Glob exclude patterns (comma separated), e.g.
                    '**/raw*/**', or '**/*.bin'.
      --exit-code   Make jardiff exit with codes similar to diff(1).
                    That is, it exits with 1 if there were differences
                    and 0 means no differences.
  -h, --help        Show this help message and exit.
  -i, --include=<glob>[,<glob>...]
                    Glob include patterns (comma separated), e.g.
                    '**/raw*/**', or '**/*.bin'.
      --stat        Show statistics output (like 'git diff --stat').
                    Displays file-by-file statistics with additions/deletions.
      --status      Show short status output (like 'git status --short').
                    Displays two-column XY status for each file.
  -v                Specify multiple -v options to increase verbosity.
                    For example, '-v -v' or '-vv'.
  -V, --version     Print version information and exit.
```

> [!TIP]
> Use shell features, e.g. in Bash, ZSH instead of typing twice long folders use the [brace expansion](https://www.gnu.org/software/bash/manual/html_node/Brace-Expansion.html#Brace-Expansion-1) :
> ```shell
> $ java -jar jardiff-0.1.0-SNAPSHOT.jar /Users/brice.dutheil/path/to/repositories/project{-original,-with-changes}/submodule/submodule/submodule/build/classes/java/main
> ```


Also, you can run it from gradle:

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

Mozilla Public License, v. 2.0. See [LICENSE](LICENSE) for details.

