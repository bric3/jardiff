/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.javap;

import com.sun.tools.javap.JavapTask;

import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Produces a javap verbose class-file listing using the JDK running jardiff.
 */
public final class JavapTextifier {
    private static final String MEMORY_CLASS_NAME = "jardiff-memory.class";
    private static final String MEMORY_CLASS_URI = "memory:///jardiff-memory.class";
    private static final List<String> JAVAP_OPTIONS = List.of(
            "-v",
            "-p",
            "-c",
            "-s",
            "-l",
            "-constants"
    );

    public List<String> toLines(InputStream inputStream) {
        return toText(inputStream).lines().collect(Collectors.toList());
    }

    public String toText(InputStream inputStream) {
        try {
            return textify(inputStream);
        } catch (Exception | LinkageError failure) {
            return warning("javap could not process this class", failure);
        }
    }

    private String textify(InputStream inputStream) throws IOException {
        byte[] classBytes = inputStream.readAllBytes();
        StringWriter output = new StringWriter();
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return warning("javap requires a JDK with the jdk.compiler module", (Throwable) null);
        }

        try (StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null)) {
            JavaFileManager fileManager = new MemoryClassFileManager(standardFileManager, classBytes);
            JavapTask task = new JavapTask(
                    output,
                    fileManager,
                    null,
                    JAVAP_OPTIONS,
                    List.of(MEMORY_CLASS_NAME)
            );
            if (!task.call()) {
                return warning("javap could not process this class", output.toString());
            }
        }

        return normalize(output.toString());
    }

    private static String normalize(String text) {
        return text.lines()
                .filter(line -> !line.startsWith("Classfile "))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String warning(String message, Throwable failure) {
        if (failure == null) {
            return "// WARNING: " + message;
        }
        return warning(message, failure.getClass().getSimpleName() + ": " + failure.getMessage());
    }

    private static String warning(String message, String detail) {
        StringBuilder warning = new StringBuilder("// WARNING: ").append(message);
        if (detail != null && !detail.isBlank()) {
            detail.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> warning.append(System.lineSeparator()).append("// ").append(line));
        }
        return warning.toString();
    }

    private static final class MemoryClassFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final byte[] classBytes;

        MemoryClassFileManager(StandardJavaFileManager fileManager, byte[] classBytes) {
            super(fileManager);
            this.classBytes = classBytes;
        }

        @Override
        public JavaFileObject getJavaFileForInput(
                Location location,
                String className,
                JavaFileObject.Kind kind
        ) throws IOException {
            if (location == StandardLocation.CLASS_PATH
                    && kind == JavaFileObject.Kind.CLASS
                    && MEMORY_CLASS_NAME.equals(className)) {
                return new MemoryClassFileObject(classBytes);
            }
            return super.getJavaFileForInput(location, className, kind);
        }

    }

    private static final class MemoryClassFileObject extends SimpleJavaFileObject {
        private final byte[] classBytes;

        MemoryClassFileObject(byte[] classBytes) {
            super(URI.create(MEMORY_CLASS_URI), Kind.CLASS);
            this.classBytes = classBytes;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(classBytes);
        }

        @Override
        public OutputStream openOutputStream() {
            throw new UnsupportedOperationException();
        }

        @Override
        public long getLastModified() {
            return -1L;
        }
    }
}
