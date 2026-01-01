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
import io.github.bric3.jardiff.TestClassWithSynthetics
import io.github.bric3.jardiff.fixtureClassInputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

// TODO tests for class outline missing features:
//  1. Groovy class detection
//  2. Throws
//  3. Field constant values
//  4. implement/extends
//  5. Class types:
//    - Enum
//    - Interface
//    - @interface (annotation)
//    - Abstract class
class ClassOutlineTest {
    @Test
    fun `outline class by InputStream`() {
        fixtureClassInputStream(FooFixtureClass::class).use {
            assertThat(ClassOutline.toText(it)).isEqualToIgnoringNewLines(
                """
                package io.github.bric3.jardiff;

                // class version: 68 (Java 24)
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

    @Test
    fun `outline marks synthetic bridge methods`() {
        // Test the GenericBridge inner class which has synthetic bridge methods from generics
        fixtureClassInputStream(TestClassWithSynthetics.GenericBridge::class).use {
            assertThat(ClassOutline.toText(it)).isEqualToIgnoringNewLines(
                """
                package io.github.bric3.jardiff;

                // class version: 68 (Java 24)
                public class TestClassWithSynthetics${'$'}GenericBridge implements java.lang.Comparable {
                  private java.lang.Comparable value
                  public TestClassWithSynthetics${'$'}GenericBridge(java.lang.Comparable)
                  public int compareTo(io.github.bric3.jardiff.TestClassWithSynthetics${'$'}GenericBridge)
                  public volatile int compareTo(java.lang.Object) // synthetic, bridge
                }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `outline marks synthetic fields from inner classes`() {
        // Test the InnerClass which has synthetic this$0 field
        fixtureClassInputStream(TestClassWithSynthetics.InnerClass::class).use {
            assertThat(ClassOutline.toText(it)).isEqualToIgnoringNewLines(
                """
                package io.github.bric3.jardiff;

                // class version: 68 (Java 24)
                public class TestClassWithSynthetics${'$'}InnerClass {
                  final io.github.bric3.jardiff.TestClassWithSynthetics this${'$'}0 // synthetic
                  public TestClassWithSynthetics${'$'}InnerClass(io.github.bric3.jardiff.TestClassWithSynthetics)
                  public void accessOuterField()
                }
                """.trimIndent()
            )
        }
    }
}