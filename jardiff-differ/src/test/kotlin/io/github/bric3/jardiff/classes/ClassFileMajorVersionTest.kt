package io.github.bric3.jardiff.classes

import io.github.bric3.jardiff.FooFixtureClass
import io.github.bric3.jardiff.fixtureClassInputStream
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class ClassFileMajorVersionTest {
    @Test
    fun `textify classes by InputStream with class version`() {
        fixtureClassInputStream(FooFixtureClass::class).use {
            assertThat(ClassFileMajorVersion.toText(it)).isEqualToIgnoringNewLines(
                """
                |class version: 68 (Java 24)
                """.trimMargin()
            )
        }
    }

    @Test
    fun `textify fails if InputStream is closed`() {
        fixtureClassInputStream(FooFixtureClass::class).use {
            it.close()

            assertThatCode { ClassFileMajorVersion.toText(it) }
                .isInstanceOf(IOException::class.java)
        }
    }

    @Test
    fun `textify fails if InputStream not a class`() {
        ByteArrayInputStream(byteArrayOf(0x12, 0x34, 0x56)).use {
            assertThatCode { ClassFileMajorVersion.toText(it) }
                .isInstanceOf(Exception::class.java)
        }
    }
}