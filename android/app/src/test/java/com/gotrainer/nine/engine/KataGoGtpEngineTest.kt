package com.gotrainer.nine.engine

import com.gotrainer.nine.game.Rank
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GTP transport parsing + BadukAI-consistency pins. The phone proves the search;
 * the PC pins the contract: profile strings, packet shapes, HumanSL budgets.
 * If BadukAI's recipe changes, these fail first — that's the point.
 */
class KataGoGtpEngineTest {
    @Test fun `profiles clamp our 30k ladder to katago 20k floor`() {
        assertEquals("rank_20k", KataGoGtpEngine.profileFor(Rank.R30K))
        assertEquals("rank_20k", KataGoGtpEngine.profileFor(Rank.R21K))
        assertEquals("rank_20k", KataGoGtpEngine.profileFor(Rank.R20K))
        assertEquals("rank_10k", KataGoGtpEngine.profileFor(Rank.R10K))
        assertEquals("rank_1k", KataGoGtpEngine.profileFor(Rank.R1K))
        assertEquals("rank_1d", KataGoGtpEngine.profileFor(Rank.R1D))
        assertEquals("rank_9d", KataGoGtpEngine.profileFor(Rank.R9D))
    }

    @Test fun `genmove packet parses a stone`() {
        val m = KataGoGtpEngine.parseGenmovePacket("= E5")
        assertEquals(4 to 4, m.x to m.y)
        assertEquals(false, m.pass)
    }

    @Test fun `genmove packet maps pass and resign to pass`() {
        assertTrue(KataGoGtpEngine.parseGenmovePacket("= pass").pass)
        assertTrue(KataGoGtpEngine.parseGenmovePacket("= resign").pass)
    }

    @Test fun `genmove_analyze play lines parse`() {
        val m = KataGoGtpEngine.parseGenmovePacket("play E5")
        assertEquals(4 to 4, m.x to m.y)
        assertTrue(KataGoGtpEngine.parseGenmovePacket("play pass").pass)
        assertTrue(KataGoGtpEngine.parseGenmovePacket("play resign").pass)
    }

    @Test fun `genmove packet surfaces gtp errors loudly`() {
        try {
            KataGoGtpEngine.parseGenmovePacket("? illegal move")
            throw AssertionError("should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("illegal"))
        }
    }

    @Test fun `final_score parses black lead, white lead and draw`() {
        assertEquals(3.5, KataGoGtpEngine.parseFinalScore("= B+3.5").scoreLeadBlack, 1e-9)
        assertEquals(-12.0, KataGoGtpEngine.parseFinalScore("= W+12").scoreLeadBlack, 1e-9)
        assertEquals(0.0, KataGoGtpEngine.parseFinalScore("= 0").scoreLeadBlack, 1e-9)
    }

    @Test fun `humanSL bundle pins badukai play budgets`() {
        val p = KataGoGtpEngine.humanSlParams()
        assertEquals("10", p["maxVisits"])
        assertEquals("0.70", p["chosenMoveTemperature"])
        assertEquals("0.85", p["chosenMoveTemperatureEarly"])
        assertEquals("1.0", p["humanSLChosenMoveProp"])
        assertEquals("0.0", p["humanSLRootExploreProbWeightless"])
        assertEquals("0.0", p["humanSLOppExploreProbWeightful"])
        assertEquals("0.50", p["humanSLCpuctExploration"])
    }

    @Test fun `gtp analyze text parses moves and root`() {
        val line = "info move E5 visits 10 utility -0.12 winrate 0.46 scoreMean -2.1 " +
            "scoreStdev 15.6 scoreLead -2.1 scoreSelfplay -2.0 prior 0.19 lcb 0.41 " +
            "utilityLcb -0.25 weight 6.9 order 0 pv E5 C3 " +
            "info move pass visits 0 utility 0 winrate 0.1 scoreMean -20 scoreStdev 0 " +
            "scoreLead -20 scoreSelfplay -20 prior 0.0 lcb 0 utilityLcb 0 weight 0 order 9 pv pass " +
            "rootInfo visits 10 utility -0.1 winrate 0.46 scoreMean -2.1 scoreStdev 15 " +
            "scoreLead -2.1 scoreSelfplay -2.0 weight 10"
        val rep = KataGoGtpEngine.parseAnalyzeLine(line)
        assertEquals(1, rep.moves.size) // pass skipped
        val m = rep.moves[0]
        assertEquals("E5", m.move)
        assertEquals(10, m.visits)
        assertEquals(0.46, m.winrate, 1e-9)
        assertEquals(-2.1, m.scoreLead, 1e-9)
        assertEquals(0.19, m.prior, 1e-9)
        assertEquals(0, m.order)
        assertEquals(10, rep.root!!.visits)
        assertEquals(0.46, rep.root.winrate, 1e-9)
    }

    @Test fun `raw human policy grid maps points`() {
        val rows = (0 until 9).joinToString("\n") { r ->
            (0 until 9).joinToString(" ") { c -> if (r == 4 && c == 4) "0.190000" else "0.001000" }
        }
        val resp = "= symmetry 0\nwhiteWin 0.5\nwhiteLoss 0.5\nnoResult 0.0\n" +
            "whiteScore 0.0\nwhiteScoreSq 0.0\nshorttermWinlossError 0.1\n" +
            "shorttermScoreError 1.0\npolicy\n$rows\npolicyPass 0.0005\n"
        val grid = KataGoGtpEngine.parseRawHumanPolicy(resp)
        assertEquals(0.19, grid[4 to 4]!!, 1e-6)
        assertEquals(81, grid.size)
    }

    @Test fun `gtp config asset is the gtp one`() {
        assertEquals("gtp.cfg", Staging.CONFIG_ASSET)
    }

    @Test fun `split-net selection pairs b10 search with human steer`() {
        val dir = Files.createTempDirectory("models-sel").toFile()
        val main = File(dir, "b10.bin").apply { writeBytes(byteArrayOf(1)) }
        val human = File(dir, "b18c384nbt-humanv0.bin.gz").apply { writeBytes(byteArrayOf(2)) }
        val (m, h) = KataGoGtpEngine.selectModels(dir.listFiles()!!.toList())
        assertEquals(main, m)
        assertEquals(human, h)
    }

    /**
     * aapt2 silently decompresses ".gz" assets and strips the extension:
     * shipping models/b10.bin.gz made the APK contain b10.bin, and every
     * assets.open("...bin.gz") threw. Pin the invariant, not the incident.
     */
    @Test fun `bundled net asset name survives aapt2 packaging rules`() {
        assertTrue(
            "aapt2 strips .gz asset names — use a plain .bin name: ${KataGoGtpEngine.B10_ASSET}",
            !KataGoGtpEngine.B10_ASSET.endsWith(".gz"),
        )
    }

    @Test fun `split-net selection fails loudly without the search net`() {
        val dir = Files.createTempDirectory("models-sel2").toFile()
        File(dir, "b18c384nbt-humanv0.bin.gz").apply { writeBytes(byteArrayOf(2)) }
        try {
            KataGoGtpEngine.selectModels(dir.listFiles()!!.toList())
            throw AssertionError("should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("b10"))
        }
    }

    @Test fun `on-device setup needs only the human download (b10 is bundled)`() {
        assertEquals(listOf(ModelManager.HUMAN), ModelManager.MODELS)
    }
}
