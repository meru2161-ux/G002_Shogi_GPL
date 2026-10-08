package com.melapplyworks.g002shogi.analysis.usi

import com.melapplyworks.g002shogi.analysis.UsiEngineQuery
import com.melapplyworks.g002shogi.analysis.poc.UsiScore
import com.melapplyworks.g002shogi.model.ShogiPositions
import com.melapplyworks.g002shogi.rules.SfenCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class UsiProtocolBackendTest {
    @Test fun handshakeOptionsAndNodeLimitedMultiPvSearchUseOnePersistentChannel() {
        val channel = FakeChannel(
            "id name fake",
            "usiok",
            "readyok",
            "readyok",
            "readyok",
            "info depth 8 nodes 12000 time 7 score cp 31 multipv 1 pv 7g7f 3c3d",
            "info depth 7 nodes 12000 time 7 score cp 18 multipv 2 pv 2g2f 8c8d",
            "bestmove 7g7f ponder 3c3d",
            "readyok",
            "info depth 9 nodes 160000 time 62 score cp 44 multipv 1 pv 2g2f 8c8d",
            "bestmove 2g2f"
        )
        var opens = 0
        val backend = UsiProtocolBackend(
            channelFactory = {
                opens++
                channel
            },
            config = UsiProtocolConfig(pollMillis = 1)
        )
        val sfen = SfenCodec.format(ShogiPositions.initial())
        val first = backend.analyze(UsiEngineQuery(sfen, 2, 12_000, 1_000) { false })
        val second = backend.analyze(UsiEngineQuery(sfen, 1, 160_000, 1_000) { false })

        assertEquals(1, opens)
        assertEquals(listOf(1, 2), first.infos.map { it.multiPv })
        assertEquals("7g7f", first.bestMove)
        assertEquals(44, (second.infos.single().score as UsiScore.Centipawn).value)
        assertEquals("2g2f", second.bestMove)
        assertEquals(
            listOf(
                "usi",
                "setoption name Threads value 4",
                "setoption name Hash value 32",
                "isready",
                "usinewgame",
                "isready",
                "setoption name MultiPV value 2",
                "isready",
                "position sfen $sfen",
                "go nodes 12000",
                "setoption name MultiPV value 1",
                "isready",
                "position sfen $sfen",
                "go nodes 160000"
            ),
            channel.writes
        )
    }

    @Test fun cancellationSendsOneStopAndDrainsBestMove() {
        val channel = FakeChannel(
            "usiok",
            "readyok",
            "readyok",
            "readyok",
            "info depth 4 nodes 100 score cp 0 multipv 1 pv 7g7f",
            "bestmove 7g7f"
        )
        val backend = UsiProtocolBackend({ channel }, UsiProtocolConfig(pollMillis = 1))
        val snapshot = backend.analyze(
            UsiEngineQuery(SfenCodec.format(ShogiPositions.initial()), 1, 160_000, 1_000) { true }
        )

        assertEquals(1, channel.writes.count { it == "stop" })
        assertEquals("7g7f", snapshot.bestMove)
    }

    @Test fun failedHandshakeClosesBrokenChannelAndRetriesWithFreshOne() {
        var now = 0L
        val broken = FakeChannel()
        val healthy = FakeChannel("usiok", "readyok", "readyok", "readyok", "bestmove 7g7f")
        val channels = ArrayDeque(listOf(broken, healthy))
        val backend = UsiProtocolBackend(
            channelFactory = { channels.removeFirst() },
            config = UsiProtocolConfig(startupTimeoutMillis = 3, pollMillis = 1),
            nowMillis = { now++ }
        )
        val query = UsiEngineQuery(SfenCodec.format(ShogiPositions.initial()), 1, 100, 100) { false }

        assertTrue(backend.analyze(query).infos.isEmpty())
        assertTrue(broken.closed)
        assertEquals("7g7f", backend.analyze(query).bestMove)
    }

    @Test fun closeRequestsQuitAndClosesChannel() {
        val channel = FakeChannel("usiok", "readyok", "readyok", "readyok")
        // An empty result is sufficient to initialize and then time out this fake search.
        var now = 0L
        val timed = UsiProtocolBackend(
            { channel },
            UsiProtocolConfig(stopGraceMillis = 2, pollMillis = 1),
            nowMillis = { now++ }
        )
        timed.analyze(UsiEngineQuery(SfenCodec.format(ShogiPositions.initial()), 1, 1, 1) { false })
        timed.close()
        assertTrue(channel.closed)
        assertTrue(channel.writes.contains("quit") || channel.writes.contains("stop"))
    }

    private class FakeChannel(vararg initialLines: String) : UsiCommandChannel {
        private val lines = ArrayDeque(initialLines.toList())
        val writes = mutableListOf<String>()
        var closed = false

        override fun writeLine(value: String) {
            check(!closed)
            writes += value
        }

        override fun readLine(timeoutMillis: Long): String? = if (lines.isEmpty()) null else lines.removeFirst()

        override fun close() {
            closed = true
        }
    }
}
