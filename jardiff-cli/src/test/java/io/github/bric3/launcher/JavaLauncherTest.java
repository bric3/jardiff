// SPDX-License-Identifier: MPL-2.0

package io.github.bric3.launcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

class JavaLauncherTest {
    @TempDir Path directory;
    private Path home;
    private Path bin;
    private Path launcher;
    private Path capture;
    private int minimum;
    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    void prepareIsolatedEnvironment() throws Exception {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")));
        home = Files.createDirectories(directory.resolve("home with spaces"));
        bin = Files.createDirectories(directory.resolve("commands"));
        capture = directory.resolve("arguments.txt");
        launcher = directory.resolve("reader with spaces.jar");
        minimum = Integer.parseInt(System.getProperty("launcher.minimum"));
        Files.copy(Path.of(System.getProperty("launcher.executable")), launcher);
        assertTrue(launcher.toFile().setExecutable(true));
        for (String utility : List.of("sed", "head")) {
            Path source = Path.of("/usr/bin", utility);
            if (!Files.exists(source)) {
                source = Path.of("/bin", utility);
            }
            Files.createSymbolicLink(bin.resolve(utility), source);
        }
        environment.put("HOME", home.toString());
        environment.put("PATH", bin.toString());
        environment.put("LAUNCHER_CAPTURE", capture.toString());
        environment.put("FAKE_JAVA_EXIT", "0");
    }

    @Test
    void prefersMiseOverAllOtherManagers() throws Exception {
        Path selected = javaHome("mise current", minimum, "mise");
        mise(selected, selected, Map.of());
        javaHomeAt(home.resolve(".sdkman/candidates/java/current"), minimum, "sdkman");
        javaHomeAt(home.resolve(".asdf/installs/java/current"), minimum, "asdf");
        javaHomeAt(home.resolve(".jenv/versions/current"), minimum, "jenv");
        javaHomeAt(home.resolve(".jabba/jdk/current"), minimum, "jabba");
        environment.put("JAVA_HOME", javaHome("explicit", minimum, "JAVA_HOME").toString());
        javaAt(bin.resolve("java"), minimum, "PATH");
        assertEquals(0, run());
        assertEquals("mise", arguments().get(0));
    }

    @Test
    void searchesInstalledMiseVersionsWhenCurrentAndRequestedMajorAreUnavailable()
            throws Exception {
        Path old = javaHome("mise old", 8, "old");
        Path newer = javaHome("mise newer", minimum + 1, "newer mise");
        mise(old, null, Map.of("java@newer", newer));
        assertEquals(0, run());
        assertEquals("newer mise", arguments().get(0));
    }

    @Test
    void usesCompatibleSdkmanCandidateWhenMiseAndSdkmanCurrentAreTooOld() throws Exception {
        Path old = javaHome("mise old", 8, "old");
        mise(old, null, Map.of());
        Path candidates = directory.resolve("custom sdk candidates");
        environment.put("SDKMAN_CANDIDATES_DIR", candidates.toString());
        javaHomeAt(candidates.resolve("java/current"), 8, "old sdkman");
        javaHomeAt(candidates.resolve("java/newer"), minimum, "sdkman");
        assertEquals(0, run());
        assertEquals("sdkman", arguments().get(0));
    }

    @Test
    void prefersAsdfBeforeJenv() throws Exception {
        Path asdf = javaHome("asdf selected", minimum, "asdf");
        script(bin.resolve("asdf"), "printf '%s\\n' " + quote(asdf.toString()) + "\n");
        javaHomeAt(home.resolve(".jenv/versions/current"), minimum, "jenv");
        assertEquals(0, run());
        assertEquals("asdf", arguments().get(0));
    }

    @Test
    void searchesCustomAsdfInstallDirectory() throws Exception {
        Path old = javaHome("asdf old", 8, "old");
        script(bin.resolve("asdf"), "printf '%s\\n' " + quote(old.toString()) + "\n");
        Path data = directory.resolve("custom asdf data");
        environment.put("ASDF_DATA_DIR", data.toString());
        javaHomeAt(data.resolve("installs/java/newer"), minimum, "asdf installed");
        assertEquals(0, run());
        assertEquals("asdf installed", arguments().get(0));
    }

    @Test
    void supportsJenvInstalledVersions() throws Exception {
        Path root = directory.resolve("jenv root");
        environment.put("JENV_ROOT", root.toString());
        javaHomeAt(root.resolve("versions/current"), minimum, "jenv");
        assertEquals(0, run());
        assertEquals("jenv", arguments().get(0));
    }

    @Test
    void supportsJabbaInstalledVersions() throws Exception {
        Path root = directory.resolve("jabba root");
        environment.put("JABBA_HOME", root.toString());
        javaHomeAt(root.resolve("jdk/current"), minimum, "jabba");
        assertEquals(0, run());
        assertEquals("jabba", arguments().get(0));
    }

    @Test
    void supportsMacOsJavaHomeLayout() throws Exception {
        javaHomeAt(home.resolve(".sdkman/candidates/java/current/Contents/Home"), minimum, "macOS");
        assertEquals(0, run());
        assertEquals("macOS", arguments().get(0));
    }

    @Test
    void usesJavaHomeAndPreservesJvmOptionsApplicationArgumentsAndExitCode() throws Exception {
        environment.put("JAVA_HOME", javaHome("explicit", minimum, "JAVA_HOME").toString());
        environment.put("JAVA_OPTS", "-Xmx64m -Dexample=value");
        environment.put("FAKE_JAVA_EXIT", "23");
        javaAt(bin.resolve("java"), minimum, "PATH");
        assertEquals(23, run("a file with spaces.adoc", "$(touch should-not-exist)"));
        assertEquals(
                List.of(
                        "JAVA_HOME",
                        "-Xmx64m",
                        "-Dexample=value",
                        "-jar",
                        launcher.toString(),
                        "a file with spaces.adoc",
                        "$(touch should-not-exist)"),
                arguments());
        assertFalse(Files.exists(directory.resolve("should-not-exist")));
    }

    @Test
    void usesPathWhenJavaHomeIsIncompatible() throws Exception {
        environment.put("JAVA_HOME", javaHome("legacy", 8, "legacy").toString());
        javaAt(bin.resolve("java"), minimum, "PATH");
        assertEquals(0, run());
        assertEquals("PATH", arguments().get(0));
    }

    @Test
    void failsClearlyWhenNoCompatibleJavaIsInstalled() throws Exception {
        environment.put("JAVA_HOME", javaHome("legacy", 8, "legacy").toString());
        assertEquals(1, run());
        assertTrue(
                Files.readString(directory.resolve("output.txt")).contains("No Java " + minimum));
        assertFalse(Files.exists(capture));
    }

    private int run(String... args) throws Exception {
        var command = new ArrayList<String>();
        command.add(launcher.toString());
        command.addAll(Arrays.asList(args));
        var builder =
                new ProcessBuilder(command)
                        .directory(directory.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(directory.resolve("output.txt").toFile());
        builder.environment().clear();
        builder.environment().putAll(environment);
        var process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Launcher timed out");
            return process.exitValue();
        } finally {
            process.destroyForcibly();
        }
    }

    private List<String> arguments() throws IOException {
        return Files.readAllLines(capture);
    }

    private Path javaHome(String name, int major, String label) throws IOException {
        Path result = directory.resolve(name);
        javaHomeAt(result, major, label);
        return result;
    }

    private void javaHomeAt(Path path, int major, String label) throws IOException {
        javaAt(path.resolve("bin/java"), major, label);
    }

    private void javaAt(Path path, int major, String label) throws IOException {
        String version = major == 8 ? "1.8.0_502" : major + ".0.1";
        script(
                path,
                String.join(
                        "\n",
                        "if [ \"$1\" = -version ]; then",
                        "    printf '%s\\n' 'openjdk version \"" + version + "\"' >&2",
                        "    exit 0",
                        "fi",
                        "printf '%s\\n' " + quote(label) + " \"$@\" > \"$LAUNCHER_CAPTURE\"",
                        "exit \"$FAKE_JAVA_EXIT\"",
                        ""));
    }

    private void mise(Path current, Path requested, Map<String, Path> installed)
            throws IOException {
        var lines = new ArrayList<String>();
        lines.add("[ \"$MISE_OFFLINE\" = 1 ] && [ \"$MISE_AUTO_INSTALL\" = 0 ] || exit 90");
        lines.add("case \"$1:$2\" in");
        lines.add("where:java) printf '%s\\n' " + quote(current.toString()) + " ;;");
        if (requested != null) {
            lines.add(
                    "where:java@"
                            + minimum
                            + ") printf '%s\\n' "
                            + quote(requested.toString())
                            + " ;;");
        }
        for (var version : installed.entrySet()) {
            lines.add(
                    "where:"
                            + version.getKey()
                            + ") printf '%s\\n' "
                            + quote(version.getValue().toString())
                            + " ;;");
        }
        String versions =
                installed.keySet().stream()
                        .map(v -> "java " + v.substring("java@".length()))
                        .collect(Collectors.joining("\n"));
        lines.add("ls:--installed) printf '%s\\n' " + quote(versions) + " ;;");
        lines.add("*) exit 1 ;;");
        lines.add("esac");
        script(bin.resolve("mise"), String.join("\n", lines) + "\n");
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void script(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, "#!/bin/sh\n" + contents, StandardCharsets.UTF_8);
        if (!path.toFile().setExecutable(true)) {
            throw new IOException("Cannot make executable: " + path);
        }
    }
}
