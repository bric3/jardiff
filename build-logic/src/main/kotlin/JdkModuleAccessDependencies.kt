/*
 * jardiff
 *
 * Copyright (c) 2025 - Brice Dutheil
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler

fun DependencyHandler.jdkModuleAccessManifests(projectDependency: ProjectDependency): Dependency? =
    add(
        JDK_MODULE_ACCESS_MANIFESTS_CONFIGURATION_NAME,
        projectDependency.copy().apply {
            targetConfiguration = JDK_MODULE_ACCESS_MANIFEST_ELEMENTS_CONFIGURATION_NAME
        }
    )
