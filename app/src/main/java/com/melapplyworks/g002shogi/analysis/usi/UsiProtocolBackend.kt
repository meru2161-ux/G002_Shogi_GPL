package com.melapplyworks.g002shogi.analysis.usi

import com.melapplyworks.g002shogi.analysis.UsiEngineBackend
import com.melapplyworks.g002shogi.analysis.UsiEngineQuery
import com.melapplyworks.g002shogi.analysis.poc.UsiAnalysisParser
import com.melapplyworks.g002shogi.analysis.poc.UsiAnalysisSession
import com.melapplyworks.g002shogi.analysis.poc.UsiAnalysisSnapshot
import com.melapplyworks.g002shogi.analysis.poc.UsiEngineEvent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

interface UsiCommandChannel : AutoCloseable {
    fun writeLine(value: String)
    fun readLine(timeoutMillis: Long): String?
}

fun interface UsiCommandChannelFactory {
    fun open(): UsiCommandChannel
}

data class UsiProtocolConfig(
    val threads: Int = 4,
    val hashMegabytes: Int = 32,
    val startupTimeoutMillis: Long = 5_000,
    val stopGraceMillis: Long = 1_000,
    val pollMillis: Long = 25
)

/**
 * Persistent, single-flight USI driver. It contains no engine-specific binary or model.
 * A timed-out or malformed process is discarded so the next request starts cleanly.
 */
class UsiProtocolBackend(
    private val channelFactory: UsiCommandChannelFactory,
    private val config: UsiProtocolConfig = UsiProtocolConfig(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) : UsiEngineBackend, AutoCloseable {
    private val parser = UsiAnalysisParser()
    private var channel: UsiCommandChannel? = null

    @Synchronized
    override fun analyze(query: UsiEngineQuery): UsiAnalysisSnapshot {
        val active = runCatching { readyChannel() }.getOrElse {
            resetChannel()
            return UsiAnalysisSnapshot(emptyList())
        }
        return runCatching { runSearch(active, query) }.getOrElse {
            resetChannel()
            UsiAnalysisSnapshot(emptyList())
        }
    }

    private fun readyChannel(): UsiCommandChannel {
        channel?.let { return it }
        val opened = channelFactory.open()
        try {
            opened.writeLine("usi")
            await(opened, "usiok", config.startupTimeoutMillis)
            opened.writeLine("setoption name Threads value ${config.threads.coerceAtLeast(1)}")
            opened.writeLine("setoption name Hash value ${config.hashMegabytes.coerceAtLeast(1)}")
            opened.writeLine("isready")
            await(opened, "readyok", config.startupTimeoutMillis)
            opened.writeLine("usinewgame")
            opened.writeLine("isready")
            await(opened, "readyok", config.startupTimeoutMillis)
            channel = opened
            return opened
        } catch (failure: Throwable) {
            opened.close()
            throw failure
        }
    }

    private fun runSearch(active: UsiCommandChannel, query: UsiEngineQuery): UsiAnalysisSnapshot {
        active.writeLine("setoption name MultiPV value ${query.multiPv.coerceAtLeast(1)}")
        active.writeLine("isready")
        await(active, "readyok", config.startupTimeoutMillis)
        active.writeLine("position sfen ${query.sfen}")
        active.writeLine("go nodes ${query.nodeLimit.coerceAtLeast(1)}")

        val session = UsiAnalysisSession()
        val searchDeadline = nowMillis() + query.timeLimitMillis.coerceAtLeast(1)
        var stopDeadline: Long? = null
        while (true) {
            val now = nowMillis()
            if ((query.isCancelled() || now >= searchDeadline) && stopDeadline == null) {
                active.writeLine("stop")
                stopDeadline = now + config.stopGraceMillis.coerceAtLeast(1)
            }
            if (stopDeadline != null && now >= stopDeadline) {
                throw IllegalStateException("USI engine did not answer bestmove after stop")
            }
            val nextBoundary = stopDeadline ?: searchDeadline
            val wait = minOf(config.pollMillis.coerceAtLeast(1), (nextBoundary - now).coerceAtLeast(1))
            val line = active.readLine(wait) ?: continue
            val event = parser.parseLine(line) ?: continue
            session.accept(event)
            if (event is UsiEngineEvent.BestMove) return session.snapshot()
        }
    }

    private fun await(active: UsiCommandChannel, expected: String, timeoutMillis: Long) {
        val deadline = nowMillis() + timeoutMillis.coerceAtLeast(1)
        while (nowMillis() < deadline) {
            val remaining = (deadline - nowMillis()).coerceAtLeast(1)
            if (active.readLine(minOf(config.pollMillis.coerceAtLeast(1), remaining))?.trim() == expected) return
        }
        throw IllegalStateException("USI engine did not answer $expected")
    }

    @Synchronized
    override fun close() {
        channel?.let { active -> runCatching { active.writeLine("quit") } }
        resetChannel()
    }

    private fun resetChannel() {
        runCatching { channel?.close() }
        channel = null
    }
}

/** Process transport used only after a product-approved executable path is supplied. */
class ProcessUsiCommandChannel(
    command: List<String>,
    workingDirectory: File? = null
) : UsiCommandChannel {
    private sealed interface OutputEvent {
        data class Line(val value: String) : OutputEvent
        data object End : OutputEvent
    }

    private val validatedCommand = command.also { require(it.isNotEmpty()) { "USI command must not be empty" } }
    private val process = ProcessBuilder(validatedCommand)
        .directory(workingDirectory)
        .redirectErrorStream(true)
        .start()
    private val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
    private val output = LinkedBlockingQueue<OutputEvent>()
    private val readerThread = Thread({
        try {
            reader.useLines { lines -> lines.forEach { output.put(OutputEvent.Line(it)) } }
        } finally {
            output.offer(OutputEvent.End)
        }
    }, "g002-usi-output").apply {
        isDaemon = true
        start()
    }

    @Synchronized
    override fun writeLine(value: String) {
        check(process.isAlive) { "USI process is not running" }
        writer.write(value)
        writer.newLine()
        writer.flush()
    }

    override fun readLine(timeoutMillis: Long): String? = when (
        val event = output.poll(timeoutMillis.coerceAtLeast(1), TimeUnit.MILLISECONDS)
    ) {
        is OutputEvent.Line -> event.value
        OutputEvent.End, null -> null
    }

    override fun close() {
        runCatching { writer.close() }
        runCatching { reader.close() }
        if (process.isAlive) process.destroy()
        runCatching {
            if (!process.waitFor(250, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
        readerThread.interrupt()
    }
}
