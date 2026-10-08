package com.melapplyworks.g002shogi.analysis.usi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class EngineRuntimeFilesTest {
    @Test fun verifiedModelIsInstalledOnceAndReused() {
        val directory = Files.createTempDirectory("g002-engine-runtime").toFile()
        try {
            val target = File(directory, "nn.bin")
            val bytes = "verified model".toByteArray()
            val expected = sha256(bytes)
            assertTrue(EngineRuntimeFiles.ensureFile(target, expected) { it.write(bytes) })
            assertTrue(EngineRuntimeFiles.matches(target, expected))
            assertTrue(EngineRuntimeFiles.ensureFile(target, expected) { error("verified file must not be rewritten") })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun corruptedModelIsRejectedAndNotInstalled() {
        val directory = Files.createTempDirectory("g002-engine-runtime").toFile()
        try {
            val target = File(directory, "nn.bin")
            assertFalse(EngineRuntimeFiles.ensureFile(target, sha256("expected".toByteArray())) { it.write("wrong".toByteArray()) })
            assertFalse(target.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02X".format(it) }
}
