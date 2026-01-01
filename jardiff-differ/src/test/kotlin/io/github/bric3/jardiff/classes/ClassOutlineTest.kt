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

import io.github.bric3.jardiff.FooFixtureClass
import io.github.bric3.jardiff.fixtureClassInputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ClassOutlineTest {
    @Test
    fun `outline class by InputStream`() {
        fixtureClassInputStream(FooFixtureClass::class).use {
            assertThat(ClassOutline.toText(it)).isEqualToIgnoringNewLines(
                """
                package io.github.bric3.jardiff;

                // Kotlin class
                public final class FooFixtureClass {
                  public FooFixtureClass()
                  public final void bar()
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `outline contains only ABI information`() {
        fixtureClassInputStream(FooFixtureClass::class).use {
            val lines = ClassOutline.toLines(it)

            // Should have: class declaration, constructor, method, closing brace
            assertThat(lines).hasSizeGreaterThanOrEqualTo(4)

            // No debug info
            assertThat(lines.none { it.contains("LINENUMBER") }).isTrue()
            assertThat(lines.none { it.contains("LOCALVARIABLE") }).isTrue()

            // No bytecode instructions
            assertThat(lines.none { it.contains("ALOAD") }).isTrue()
            assertThat(lines.none { it.contains("INVOKE") }).isTrue()
        }
    }


    @Test
    fun `outline fails if InputStream is closed`() {
        fixtureClassInputStream(FooFixtureClass::class).use {
            it.close()

            assertThatCode { ClassOutline.toText(it) }
                .isInstanceOf(IOException::class.java)
        }
    }

    @Test
    fun `outline fails if InputStream not a class`() {
        ByteArrayInputStream(byteArrayOf(0x12, 0x34, 0x56)).use {
            assertThatCode { ClassOutline.toText(it) }
                .isInstanceOf(Exception::class.java)
        }
    }
}