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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class JavapTextifierTest {
    @Test
    void textifiesClassBytesWithVerboseJavapOutput() throws IOException {
        String text;
        try (var inputStream = Objects.requireNonNull(
                JavapTextifierTest.class.getResourceAsStream("JavapTextifierTest.class")
        )) {
            text = new JavapTextifier().toText(inputStream);
        }

        assertThat(text)
                .contains(
                        "SHA-256 checksum",
                        "class io.github.bric3.jardiff.javap.JavapTextifierTest",
                        "minor version:",
                        "major version:",
                        "Constant pool:"
                )
                .doesNotStartWith("Classfile memory:///jardiff-memory.class")
                .doesNotContain("\n  Last modified ");
    }

    @Test
    void emitsWarningWhenJavapCannotProcessClassBytes() {
        String text = new JavapTextifier().toText(new ByteArrayInputStream(new byte[] {0x12, 0x34, 0x56}));

        assertThat(text).contains(
                "// WARNING: javap could not process this class",
                "Bad magic number"
        );
    }
}
