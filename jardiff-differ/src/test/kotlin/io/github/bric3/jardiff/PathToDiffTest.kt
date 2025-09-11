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
import org.junit.jupiter.api.Test
import java.nio.file.Path

class PathToDiffTest {
    @Test
    fun `create the right instance`() {
        assertThat(PathToDiff.of(PathToDiff.LeftOrRight.LEFT, Path.of("src")))
            .isInstanceOf(PathToDiff.Directory::class.java)
        assertThat(PathToDiff.of(PathToDiff.LeftOrRight.LEFT, Path.of("the.jar")))
            .isInstanceOf(PathToDiff.Jar::class.java)
    }
}