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

import io.github.bric3.jardiff.classes.ClassTextifierProducer
import org.apache.tika.parser.txt.CharsetDetector
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.extension
import kotlin.io.path.name


object FileReader {
    private const val CHARSET_CONFIDENCE_THRESHOLD = 40

    @JvmOverloads
    fun readFileAsTextIfPossible(
        fileAccess: FileAccess?,
        classTextifierProducer: ClassTextifierProducer,
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
                    in classExtensions -> Result.success(classTextifierProducer.instance.toLines(it))
                    else -> {
                        // Mark the stream so we can reset if needed
                        it.mark(Int.MAX_VALUE)

                        // detect charset
                        val detector = CharsetDetector().setText(it)
                        val match = detector.detect()
                        val encodingHint = encodingHint(fileAccess.relativePath)
                        Logger.debugLog("Charset ${match.name} with ${match.confidence} confidence on $fileAccess")

                        // Reset stream to beginning after charset detection consumed it
                        it.reset()

                        if (match.confidence >= CHARSET_CONFIDENCE_THRESHOLD) {
                            Result.success(it.reader(Charset.forName(match.name)).readLines())
                        } else if(match.confidence < CHARSET_CONFIDENCE_THRESHOLD && encodingHint != null) {
                            Logger.debugLog("Charset detection below $CHARSET_CONFIDENCE_THRESHOLD trying $encodingHint")
                            Result.success(it.reader(Charset.forName(encodingHint)).readLines())
                        } else {
                            Logger.debugLog("Charset detection below $CHARSET_CONFIDENCE_THRESHOLD and no hint, assume binary file")
                            Result.success(produceHash(it))
                        }
                    }
                }
            }.recoverCatching { t ->
                Logger.debugLog("Error reading file $fileAccess.${t.message}")
                produceHash(it)
            }.getOrDefault(emptyList())
        }
    }

    private fun encodingHint(relativePath: Path): String? {
        return when {
            // MANIFEST.MF is UTF-8 : https://docs.oracle.com/javase/8/docs/technotes/guides/jar/jar.html#Name-Value_pairs_and_Sections
            relativePath.name == "MANIFEST.MF" -> "UTF-8"
            else -> null
        }
    }

    // For now just return the sha1 of the binary file
    // Next show binary diff? E.g.
    //  │00000000│ 23 21 2f 62 69 6e 2f 73 ┊ 68 0a 0a 23 0a 23 20 43 │#!/bin/s┊h__#_# C│
    private fun produceHash(it: BufferedInputStream) = listOf("FILE SHA-1: ${sha1Hex(it)}")

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