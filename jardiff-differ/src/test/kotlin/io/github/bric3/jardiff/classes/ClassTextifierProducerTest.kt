/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff.classes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class ClassTextifierProducerTest {
    @Test
    fun `jcod producer adapts the standalone jcod textifier`() {
        val text = ClassTextifierProducer.jcod.create().toText(ByteArrayInputStream(classBytes()))

        assertThat(text).contains(
            "class io/github/bric3/jardiff/classes/ClassTextifierProducerTest {",
            "  0xCAFEBABE;",
            " // Constant Pool"
        )
    }

    private fun classBytes(): ByteArray {
        val resourceName = "${ClassTextifierProducerTest::class.java.name.replace('.', '/')}.class"
        return checkNotNull(ClassTextifierProducerTest::class.java.classLoader.getResourceAsStream(resourceName)) {
            "Could not load test class resource $resourceName"
        }.use { it.readBytes() }
    }
}
