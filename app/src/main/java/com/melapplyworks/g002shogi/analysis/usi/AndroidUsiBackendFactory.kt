package com.melapplyworks.g002shogi.analysis.usi

import android.content.Context
import android.util.Log
import com.melapplyworks.g002shogi.analysis.UsiEngineBackend
import java.io.File

/**
 * Supplies G002's bundled ARM64 USI engine without exposing a writable engine
 * path to the UI. A missing or damaged runtime deliberately returns null so
 * [com.melapplyworks.g002shogi.analysis.ShogiEngineFactory] can use its tested
 * local fallback instead of leaving a game stuck.
 */
object AndroidUsiBackendFactory {
    private const val LogTag = "G002Engine"
    private const val NativeEngineName = "libg002_yaneuraou.so"
    private const val ModelAssetPath = "engine/nn.bin"
    private const val ModelFileName = "nn.bin"
    private const val ModelSha256 = "CF7645F64BF6BAA5C74612799CE562752F7985923B1F0FC2E6092C998ED867F9"

    fun create(context: Context): UsiEngineBackend? = runCatching {
        val executable = File(context.applicationInfo.nativeLibraryDir, NativeEngineName)
        if (!executable.isFile) {
            Log.w(LogTag, "Bundled ARM64 engine is unavailable; using local fallback")
            return null
        }
        val workDirectory = File(context.filesDir, "g002-usi")
        val model = File(workDirectory, ModelFileName)
        val modelReady = context.assets.open(ModelAssetPath).use { source ->
            EngineRuntimeFiles.ensureFile(model, ModelSha256) { destination -> source.copyTo(destination) }
        }
        if (!modelReady) {
            Log.e(LogTag, "Bundled evaluation model verification failed; using local fallback")
            return null
        }
        UsiProtocolBackend(
            channelFactory = UsiCommandChannelFactory {
                ProcessUsiCommandChannel(listOf(executable.absolutePath), workDirectory)
            },
            config = UsiProtocolConfig(threads = 4, hashMegabytes = 32)
        ).also { Log.i(LogTag, "Bundled KP256 USI engine prepared") }
    }.getOrElse { failure ->
        Log.e(LogTag, "Could not prepare bundled USI engine; using local fallback", failure)
        null
    }
}
