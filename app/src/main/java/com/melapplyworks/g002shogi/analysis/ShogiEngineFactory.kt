package com.melapplyworks.g002shogi.analysis

/**
 * Product engine selection point. With no approved USI backend, G002 keeps its
 * existing local engine. Supplying a backend adds the stronger engine while
 * retaining the local engine as a safe runtime fallback.
 */
object ShogiEngineFactory {
    fun create(approvedUsiBackend: UsiEngineBackend? = null): ShogiAnalysisEngine {
        val local = LocalShogiAnalysisEngine()
        return approvedUsiBackend?.let { backend ->
            FallbackShogiAnalysisEngine(UsiShogiAnalysisEngine(backend), local)
        } ?: local
    }
}
