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

import org.apache.tika.parser.txt.CharsetDetector
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.io.path.extension

object FileReader {
    fun readFileAsTextIfPossible(
        fileAccess: FileAccess?,
        additionalClassExtensions: Set<String> = emptySet()
    ): List<String> {
        if (fileAccess == null) {
            return emptyList()
        }

        val classExtensions = additionalClassExtensions + "class"

        // need to handle
        // * is class file?
        // * is text file?
        // * is other binary file? then limit to checksum
        fileAccess.bufferedInputStream.use {
            return runCatching {
                when (fileAccess.relativePath.extension) {
                    in classExtensions -> Result.success(asmTextifier(it))
                    else -> {
                        // detect charset
                        val detector = CharsetDetector().setText(it)
                        val match = detector.detect()
                        if (match.confidence > 40) {
                            Result.success(it.reader(Charset.forName(match.name)).readLines())
                        } else {
                            Result.failure(Exception("Unexpected character detected"))
                        }
                    }
                }
            }.recoverCatching { _ ->
                binaryToText(it)
            }.getOrDefault(emptyList())
        }
    }

    // For now just return the sha1 of the binary file
    // Next show binary diff? E.g.
    //  │00000000│ 23 21 2f 62 69 6e 2f 73 ┊ 68 0a 0a 23 0a 23 20 43 │#!/bin/s┊h__#_# C│
    private fun binaryToText(it: BufferedInputStream) = listOf("BINARY FILE SHA-1: ${sha1Hex(it)}")

    private fun sha1Hex(input: InputStream): String {
        return input.use {
            val buffer = ByteArray(8192)
            val digest = MessageDigest.getInstance("SHA-1")
            var read: Int
            while (it.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun asmTextifier(inputStream: InputStream): List<String> {
        return StringWriter().use { writer ->
            AnyInputStreamTextifier.textify(writer, inputStream)
            writer.toString().lines()
        }
    }

    /**
     * Similar to [runCatching], but take a block to return a Result instead
     */
    public inline fun <T, R> T.runCatching(block: T.() -> Result<R>): Result<R> {
        return try {
            block()
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}