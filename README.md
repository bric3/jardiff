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

Or with the `simple` mode

```
⨯ foo/bar/qux/Baz.class
✔ foo/bar/qux/Zorg.class
```

## Features

Other tools didn't have the feature I wanted, or they were impractical to use, so I made my own.

* Compare JARs and directories recursively
* Line-based diffs for each files
* Class file comparison using ASM's Textify
* Binary diff as sha-1 hashes 
* Exclude glob patterns (for the relative paths inside the jars/directories)

  _(fallback mechanism, for non class-files, non-decoded text files)_

Unsupported at this time, if ever...
* Nested jars
* Include glob patterns
* More advanced binary diffs, i.e. showing bytes differences.

  _This one is unlikely_

## Usage
                 
> [!CAUTION] 
> This tool needs a JDK24 to build and run. Example with `mise`
> ```shell
> $ mise exec java@corretto-24 -- java -jar jardiff-0.1.0-SNAPSHOT.jar
> ```

Build it `./gradlew build`, then run it:

```shell
$ java -jar jardiff/build/shadowed-app/jardiff-0.1.0-SNAPSHOT.jar -h
Usage: jardiff [-hVv] [-m=<mode>] [-ce=<extension>[,<extension>...]]...
               [-e=<glob>]... <left> <right>
Compares two JAR files or directories and reports differences.
      <left>                 The JAR file or directory to compare.
      <right>                The JAR file or directory to compare.
      -ce, --class-exts, --coalesce-classe-exts=<extension>[,<extension>...]
                             Coalesce class files with the given extensions, in
                             addition to the usual 'class', i.e. makes classes
                             named 'Foo.class' and 'Foo.bin' aliased to the same
                             file same entry. Also this enables the file to be
                             compared on bytecode level Takes a comma separated
                             list, e.g. 'classdata' or 'raw,bin,clazz'.
  -e, --exclude=<glob>       A glob exclude pattern, e.g.
                             '**/raw*/**', or '**/*.bin'
  -h, --help                 Show this help message and exit.
  -m, --output-mode=<mode>   Output mode, default: diff)
                             Possible outputs: simple, diff.
  -v                         Specify multiple -v options to increase verbosity.
                             For example, `-v -v` or `-vv`
  -V, --version              Print version information and exit.
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

Mozila Public License, v. 2.0. See [LICENSE](LICENSE) for details.

