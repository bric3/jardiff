/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.jcod

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class JcodTextifierTest {
    @Test
    fun `textifies a class file as jcod`() {
        val text = JcodTextifier().toText(ByteArrayInputStream(classBytes()))

        assertThat(text).contains(
            "class io/github/bric3/jardiff/jcod/JcodTextifierTest {",
            "  0xCAFEBABE;",
            " // Constant Pool",
            "  } // end of Constant Pool",
            " // Methods",
            "  } // end of Methods",
            "Attr(#",
            " // Code",
            " // Traps",
            " at 0x"
        )
    }

    @Test
    fun `textifies modern constant pool entries`() {
        val text = JcodTextifier().toText(ByteArrayInputStream(classBytes()))

        assertThat(text).contains("MethodHandle ", "InvokeDynamic ")
    }

    @Test
    fun `textifies malformed input as raw jcod bytes`() {
        val text = JcodTextifier().toText(ByteArrayInputStream(byteArrayOf(0x12, 0x34, 0x56)))

        assertThat(text).contains(
            "// JCodTextifier could not parse this class structurally.",
            "// Raw bytes preserve the original class file for AsmTools round-trip.",
            "file \"class-bytes.class\" Bytes[3]z {",
            "  0x12 0x34 0x56;",
            "}"
        )
    }

    private fun classBytes(): ByteArray {
        val resourceName = "${JcodTextifierTest::class.java.name.replace('.', '/')}.class"
        return checkNotNull(JcodTextifierTest::class.java.classLoader.getResourceAsStream(resourceName)) {
            "Could not load test class resource $resourceName"
        }.use { it.readBytes() }
    }
}
