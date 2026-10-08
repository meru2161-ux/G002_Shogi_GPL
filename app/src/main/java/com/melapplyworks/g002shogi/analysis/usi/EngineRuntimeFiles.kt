package com.melapplyworks.g002shogi.analysis.usi

import java.io.File
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Installs the checked model into the app-private engine directory atomically.
 * The model itself remains immutable in the APK assets; the engine only reads
 * this verified runtime copy from its working directory.
 */
internal object EngineRuntimeFiles {
    fun ensureFile(
        target: File,
        expectedSha256: String,
        writeSource: (OutputStream) -> Unit
    ): Boolean {
        if (matches(target, expectedSha256)) return true
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val staging = File(parent, "${target.name}.part")
        return runCatching {
            staging.outputStream().buffered().use(writeSource)
            check(matches(staging, expectedSha256)) { "Engine model checksum mismatch" }
            if (target.exists() && !target.delete()) return false
            check(staging.renameTo(target)) { "Could not install engine model" }
            true
        }.getOrElse {
            staging.delete()
            false
        }
    }

    fun matches(file: File, expectedSha256: String): Boolean {
        if (!file.isFile) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02X".format(byte) } == expectedSha256.uppercase()
    }
}
