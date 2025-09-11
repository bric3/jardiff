/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package io.github.bric3.jardiff

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

class AnyInputStreamTextifierTest {
    @Test
    fun `textify classes by InputStream`() {
        StringWriter().use { writer ->
            this::class.java.classLoader.getResourceAsStream(FooFixtureClass::class.path).use {
                AnyInputStreamTextifier.textify(writer, it)
                assertThat(writer.toString()).isEqualToIgnoringNewLines(
                    """
                    |// class version 68.0 (68)
                    |// access flags 0x31
                    |public final class io/github/bric3/jardiff/FooFixtureClass {
                    |
                    |  // compiled from: FooFixtureClass.kt
                    |
                    |  @Lkotlin/Metadata;(mv={2, 2, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0003\n\u0002\u0010\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0006\u0010\u0004\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"Lio/github/bric3/jardiff/FooFixtureClass;", "", "<init>", "()V", "bar", "", "jardiff-differ_testFixtures"})
                    |
                    |  // access flags 0x1
                    |  public <init>()V
                    |   L0
                    |    LINENUMBER 13 L0
                    |    ALOAD 0
                    |    INVOKESPECIAL java/lang/Object.<init> ()V
                    |    RETURN
                    |   L1
                    |    LOCALVARIABLE this Lio/github/bric3/jardiff/FooFixtureClass; L0 L1 0
                    |    MAXSTACK = 1
                    |    MAXLOCALS = 1
                    |
                    |  // access flags 0x11
                    |  public final bar()V
                    |   L0
                    |    LINENUMBER 15 L0
                    |    LDC "Hello, World!"
                    |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
                    |    SWAP
                    |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/Object;)V
                    |   L1
                    |    LINENUMBER 16 L1
                    |    RETURN
                    |   L2
                    |    LOCALVARIABLE this Lio/github/bric3/jardiff/FooFixtureClass; L0 L2 0
                    |    MAXSTACK = 2
                    |    MAXLOCALS = 1
                    |}
                    """.trimMargin()
                )
            }
        }
    }

    @Test
    fun `textify classes by InputStream with noDebug`() {
        StringWriter().use { writer ->
            this::class.java.classLoader.getResourceAsStream(FooFixtureClass::class.path).use {
                AnyInputStreamTextifier.textify(writer, it, noDebug = true)
                assertThat(writer.toString()).isEqualToIgnoringNewLines(
                    """
                    |// class version 68.0 (68)
                    |// access flags 0x31
                    |public final class io/github/bric3/jardiff/FooFixtureClass {
                    |
                    |
                    |  @Lkotlin/Metadata;(mv={2, 2, 0}, k=1, xi=48, d1={"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0008\u0003\n\u0002\u0010\u0002\n\u0000\u0018\u00002\u00020\u0001B\u0007\u00a2\u0006\u0004\u0008\u0002\u0010\u0003J\u0006\u0010\u0004\u001a\u00020\u0005\u00a8\u0006\u0006"}, d2={"Lio/github/bric3/jardiff/FooFixtureClass;", "", "<init>", "()V", "bar", "", "jardiff-differ_testFixtures"})
                    |
                    |  // access flags 0x1
                    |  public <init>()V
                    |    ALOAD 0
                    |    INVOKESPECIAL java/lang/Object.<init> ()V
                    |    RETURN
                    |    MAXSTACK = 1
                    |    MAXLOCALS = 1
                    |
                    |  // access flags 0x11
                    |  public final bar()V
                    |    LDC "Hello, World!"
                    |    GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
                    |    SWAP
                    |    INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/Object;)V
                    |    RETURN
                    |    MAXSTACK = 2
                    |    MAXLOCALS = 1
                    |}
                    """.trimMargin()
                )
            }
        }
    }

    @Test
    fun `textify fails if InputStream is closed`() {
        StringWriter().use { writer ->
            this::class.java.classLoader.getResourceAsStream(FooFixtureClass::class.path).use {
                it.close()

                assertThatCode { AnyInputStreamTextifier.textify(writer, it, noDebug = true) }
                    .isInstanceOf(IOException::class.java)
            }
        }
    }
}