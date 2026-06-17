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
import java.io.Writer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
        try {
            return textify(inputStream);
        } catch (Exception | LinkageError failure) {
            return warningLines("javap could not process this class", failure);
        }
    }

    public String toText(InputStream inputStream) {
        return String.join(System.lineSeparator(), toLines(inputStream));
    }

    private List<String> textify(InputStream inputStream) throws IOException {
        byte[] classBytes = inputStream.readAllBytes();
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return warningLines("javap requires a JDK with the jdk.compiler module", (Throwable) null);
        }

        try (StandardJavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null);
             LineCollectingWriter output = new LineCollectingWriter()) {
            JavaFileManager fileManager = new MemoryClassFileManager(standardFileManager, classBytes);
            JavapTask task = new JavapTask(
                    output,
                    fileManager,
                    null,
                    JAVAP_OPTIONS,
                    List.of(MEMORY_CLASS_NAME)
            );
            if (!task.call()) {
                return warningLines("javap could not process this class", output.lines());
            }
            return output.lines();
        }
    }

    private static List<String> warningLines(String message, Throwable failure) {
        if (failure == null) {
            return List.of("// WARNING: " + message);
        }
        return warningLines(message, List.of(failure.getClass().getSimpleName() + ": " + failure.getMessage()));
    }

    private static List<String> warningLines(String message, List<String> detailLines) {
        ArrayList<String> warning = new ArrayList<>();
        warning.add("// WARNING: " + message);
        if (detailLines != null) {
            detailLines.stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(line -> warning.add("// " + line));
        }
        return warning;
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

    private static final class LineCollectingWriter extends Writer {
        private final ArrayList<String> lines = new ArrayList<>();
        private final StringBuilder currentLine = new StringBuilder();
        private boolean closed;

        @Override
        public void write(char[] buffer, int offset, int length) {
            ensureOpen();
            append(buffer, offset, length);
        }

        @Override
        public void write(String text, int offset, int length) {
            ensureOpen();
            for (int index = offset; index < offset + length; index++) {
                appendCharacter(text.charAt(index));
            }
        }

        @Override
        public void flush() {
            ensureOpen();
        }

        @Override
        public void close() {
            if (!closed) {
                flushCurrentLine();
                closed = true;
            }
        }

        List<String> lines() {
            if (!closed) {
                flushCurrentLine();
            }
            return lines;
        }

        private void append(char[] buffer, int offset, int length) {
            for (int index = offset; index < offset + length; index++) {
                appendCharacter(buffer[index]);
            }
        }

        private void appendCharacter(char character) {
            if (character == '\n') {
                appendCurrentLine();
            } else {
                currentLine.append(character);
            }
        }

        private void flushCurrentLine() {
            if (currentLine.length() > 0) {
                appendCurrentLine();
            }
        }

        private void appendCurrentLine() {
            int lineLength = currentLine.length();
            if (lineLength > 0 && currentLine.charAt(lineLength - 1) == '\r') {
                currentLine.setLength(lineLength - 1);
            }
            String line = currentLine.toString();
            currentLine.setLength(0);
            if (!line.startsWith("Classfile ")) {
                lines.add(line);
            }
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Writer is closed");
            }
        }
    }
}
