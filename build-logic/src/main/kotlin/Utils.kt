/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the


// Workaround for using version catalogs in precompiled script plugins.
// See https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
val Project.libs
    get() = the<LibrariesForLibs>()

/**
 * It's not possible to use the [JavaPlugin.JAVADOC_JAR_TASK_NAME] constant.
 */
val JAVADOC_JAR_TASK_NAME = "javadocJar"