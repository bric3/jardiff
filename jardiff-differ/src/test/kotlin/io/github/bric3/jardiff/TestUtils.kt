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

import kotlin.reflect.KClass

val KClass<*>.path: String?
    get() {
        val simpleClassNameWithHostName = qualifiedName?.substring(java.packageName.length + 1)
            ?: return null

        return buildString {
            append(java.packageName.replace('.', '/'))
            append("/")
            append(simpleClassNameWithHostName.replace('.', '$'))
            append(".class")
        }
    }
