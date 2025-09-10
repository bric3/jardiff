# Jardiff

Jardiff is a command-line tool for comparing the contents of JAR files and directories. It provides detailed, line-based diffs of class files and resources, making it easy to spot changes between builds, releases, or different versions of Java/Kotlin projects.

## Features

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
                 
> [!CAUTION] This tool needs a JDK24 to build and run
> Example with `mise`
> ```shell
> $ mise exec java@corretto-24 -- java -jar jardiff-0.1.0-SNAPSHOT.jar
> ```

Build it `./gradlew build`, then run it:

```shell
$ java -jar jardiff/build/shadowed-app/jardiff-0.1.0-SNAPSHOT.jar -h
Usage: jardiff [-hV] [--class-file-extensions=<extension>[,<extension>...]]...
               [-e=<glob>]... <left> <right>
Compares two JAR files or directories and reports differences.
      <left>             The JAR file or directory to compare.
      <right>            The JAR file or directory to compare.
      --class-file-extensions=<extension>[,<extension>...]
                         A comma separated list of class file extension, e.g.
                          'classdata' or 'raw,bin,clazz'
  -e, --exclude=<glob>   A glob exclude pattern, e.g.
                         '**/raw*/**', or '**/*.bin'
  -h, --help             Show this help message and exit.
  -V, --version          Print version information and exit.
```



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

