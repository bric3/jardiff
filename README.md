# Jardiff

Jardiff is a command-line tool for comparing the contents of JAR files and directories. It provides detailed, line-based diffs of class files and resources, making it easy to spot changes between builds, releases, or different versions of Java/Kotlin projects.

## Features

* Compare JARs and directories recursively
* Line-based diffs for each files
* Class file comparison using ASM's Textify
* Binary diff as sha-1 hashes 

  _(fallback mechnism, for non class-files, non-decoded text files)_

Unsupported at this time, if ever...
* Filter patterns to include/exclude paths
* Nested jars
* More advanced binary diffs, i.e. showing bytes differences.

  _This one is unlikely_

## Usage

Currently, the tool is not packaged so one need to run it via Gradle:

```sh
./gradlew run --args="<left> <right>"
```

- `<left>` and `<right>` can be paths to JAR files or directories.
- The tool outputs a summary and detailed diff of all differing files.

## Building

To build the project:

```sh
./gradlew build
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

