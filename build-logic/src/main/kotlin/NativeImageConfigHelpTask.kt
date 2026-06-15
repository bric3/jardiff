/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class NativeImageConfigHelpTask : DefaultTask() {
    @get:Input
    abstract val instructions: Property<String>

    @TaskAction
    fun printInstructions() {
        logger.lifecycle(instructions.get())
    }
}
