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
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ResourcePathTest {
    @Test
    fun `should keep resource separator when built from a file system path`() {
        val resourcePath = ResourcePath.fromPath(Path.of("META-INF", "versions", "9", "module-info.class"))

        assertThat(resourcePath.toString()).isEqualTo("META-INF/versions/9/module-info.class")
        assertThat(resourcePath.parentPath).isEqualTo("META-INF/versions/9")
        assertThat(resourcePath.nameWithoutExtension).isEqualTo("module-info")
        assertThat(resourcePath.extension).isEqualTo("class")
    }

    @Test
    fun `should reject platform separators in resource names`() {
        assertThatThrownBy {
            ResourcePath.of("""META-INF\MANIFEST.MF""")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
