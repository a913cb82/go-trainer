package com.gotrainer.nine.engine

import android.content.Context
import android.util.Log
import com.gotrainer.nine.game.Candidate
import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.PoolEntry
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * KataGo GTP engine via /system/bin/linker64 + libkatago.so (gtp mode).
 *
 * BadukAI-consistent play (decompiled aki65 v1.23.0, /tmp/aki65/lzwrapper.py):
 * persistent GTP board (tree reuse across moves), HumanSL bundle via
 * kata-set-params + kata-set-param humanSLProfile, bot moves via
 * `time_settings 0 <think>s 1` + kata-genmove_analyze, candidates via
 * kata-analyze (HumanSL stays ACTIVE — we need humanPrior on moveInfos;
 * BadukAI deactivates it for its unbiased panel, which we don't have),
 * scoring via final_score.
 *
 * Spawn/launch contract (load-bearing, verified by probe): cwd=/data/data
 * (package-gate requirement — the NOP'd length check reduces to a "/data/data"
 * prefix match), absolute argv, HOME=filesDir, stderr to a file.
 * See docs/ON_DEVICE.md.
 *
 * KataGo is REQUIRED. Every call throws loudly (IllegalStateException) when the
 * binary or models are missing or a command fails — there are no mock fallbacks.
 */
class KataGoGtpEngine(private val appContext: Context) : GoEngine {
    companion object {
        private const val TAG = "KataGo"
        private const val START_TIMEOUT_MS = 45_000L
        private const val CMD_TIMEOUT_MS = 10_000L
        // BadukAI default think_time is 10s; the visit cap binds long before it.
        private const val THINK_TIME_SEC = 10L
        private const val GENMOVE_TIMEOUT_MS = 30_000L
        private const val ANALYZE_TIMEOUT_MS = 20_000L
        private const val SCORE_TIMEOUT_MS = 15_000L
        /** BadukAI HumanSL play budget: 10 visits/move; analysis needs much more. */
        private const val PLAY_VISITS = 10
        // 150 visits with wide-root-noise reliably yields 10-19 DISTINCT root moves
        // (measured); at 25-60 visits the search funnels ~99% of visits into 1-4
        // moves, which starved the candidate pool down to 3 pills (user report).
        private const val ANALYZE_VISITS = 150
        // KataGo's documented knob for analysis breadth: explore the top moves less
        // deeply but give evaluations to a greater variety of moves.
        private const val WIDE_ROOT_NOISE = "0.20"
        /**
         * Bundled b10 SEARCH net. Asset name MUST NOT end in ".gz": aapt2
         * silently decompresses .gz assets and STRIPS the extension during
         * packaging (b10.bin.gz on disk arrived as assets/models/b10.bin in the
         * APK), so assets.open("...bin.gz") threw FileNotFoundException and the
         * setup screen showed a bare "models/b10.bin.gz" failure. The file is the
         * UNCOMPRESSED KataGo model (gzip magic "g170" header, 12,003,218B).
         */
        const val B10_ASSET = "models/b10.bin"
        private const val B10_ASSET_BYTES = 12_003_218L
        // v6 = split-net asset renamed b10.bin (aapt2 .gz gotcha, rev v5 asset lost).
        // v5 = split-net play (b10 SEARCH + b18 HUMAN steer, BadukAI's recipe).
        // v4 = GTP transport cutover; v3 analysis threads 8->2; v2 NOP gate.
        private const val ENGINE_PATCH_REV = "v6"

        /**
         * Split-net selection (pure, unit-tested): the small b10 SEARCHES,
         * b18-human STEERS (HumanSL profile + raw policy). Both REQUIRED —
         * loud failure names the missing half.
         */
        fun selectModels(files: List<File>): Pair<File, File> {
            val main = files.firstOrNull { it.name == B10_ASSET.substringAfterLast('/') }
                ?: throw IllegalStateException("search net models/b10.bin missing — reinstall the app (bundled asset)")
            val human = files.firstOrNull { it.name.contains("human") }
                ?: throw IllegalStateException("human net missing — download it from the setup screen")
            return main to human
        }
        /**
         * Gate cwd: short enough (10 chars) that the prefix loop only compares
         * "/data/data", which every resolved app path starts with on this
         * device. Longer cwds (filesDir!) overrun the 27B check copy and fail.
         */
        private const val GATE_CWD = "/data/data"

        /**
         * BadukAI's HUMAN_SL_PARAMS, DEACTIVATED column (lzwrapper.py) plus wide
         * root noise: used for ANALYSIS only. BadukAI turns HumanSL off before its
         * analysis panel; we do the same for candidates, because HumanSL's focused
         * search params collapse the root to a handful of visited moves, and the
         * candidate selector needs a broad, scored move pool.
         */
        fun humanSlParamsOff(): Map<String, String> = mapOf(
            "analysisIgnorePreRootHistory" to "true",
            "rootNumSymmetriesToSample" to "1",
            "useLcbForSelection" to "true",
            "staticScoreUtilityFactor" to "0.1",
            "dynamicScoreUtilityFactor" to "0.3",
            "useUncertainty" to "true",
            "subtreeValueBiasFactor" to "0.45",
            "useNoisePruning" to "true",
            "chosenMoveTemperatureEarly" to "0.5",
            "chosenMoveTemperature" to "0.1",
            "chosenMoveTemperatureHalflife" to "19",
            "chosenMoveTemperatureOnlyBelowProb" to "1.0",
            "chosenMovePrune" to "1",
            "analysisWideRootNoise" to WIDE_ROOT_NOISE,
            "maxVisits" to ANALYZE_VISITS.toString(),
        )

        /**
         * BadukAI's HUMAN_SL_PARAMS, activated column (lzwrapper.py). Sent via
         * kata-set-params when HumanSL play is (re)armed. maxVisits here is the
         * PLAY budget; analysis uses humanSlParamsOff() at a wider budget.
         */
        fun humanSlParams(): Map<String, String> = mapOf(
            "analysisIgnorePreRootHistory" to "false",
            "rootNumSymmetriesToSample" to "2",
            "useLcbForSelection" to "false",
            "staticScoreUtilityFactor" to "0.3",
            "dynamicScoreUtilityFactor" to "0.0",
            "useUncertainty" to "false",
            "subtreeValueBiasFactor" to "0.0",
            "useNoisePruning" to "false",
            "chosenMoveTemperatureEarly" to "0.85",
            "chosenMoveTemperature" to "0.70",
            "chosenMoveTemperatureHalflife" to "80",
            "chosenMoveTemperatureOnlyBelowProb" to "0.01",
            "chosenMovePrune" to "0",
            "humanSLChosenMoveProp" to "1.0",
            "humanSLChosenMoveIgnorePass" to "true",
            "humanSLChosenMovePiklLambda" to "100000000",
            "humanSLRootExploreProbWeightless" to "0.0",
            "humanSLRootExploreProbWeightful" to "0.0",
            "humanSLPlaExploreProbWeightless" to "0.0",
            "humanSLPlaExploreProbWeightful" to "0.0",
            "humanSLOppExploreProbWeightless" to "0.0",
            "humanSLOppExploreProbWeightful" to "0.0",
            "humanSLCpuctExploration" to "0.50",
            "humanSLCpuctPermanent" to "0.2",
            "maxVisits" to PLAY_VISITS.toString(),
        )

        /**
         * Rank -> HumanSL profile. KataGo profiles span 20k..9d only
         * (preaz_/rank_); our ladder runs to 30k, so 21k+ clamp to 20k —
         * BadukAI's spinner likewise offers 20k..9d. Style stays modern
         * (rank_); preaz_ (pre-AI-era openings) is a follow-up selector.
         */
        fun profileFor(rank: Rank): String {
            val id = rank.id
            return if (id.endsWith("k")) {
                val num = id.dropLast(1).toIntOrNull() ?: 10
                "rank_${minOf(num, 20)}k"
            } else {
                "rank_$id"
            }
        }

        private val PLAY_LINE = Regex("^play\\s+([A-HJ][1-9]|pass|resign)\\s*$")

        /**
         * Plain `genmove` answers `= E5`; `genmove_analyze` (v1.16 source,
         * handleGenMoveResult: `response = "play " + response`) answers with a
         * bare `=` header up front and the bare line `play E5` when done.
         * "= pass" / "play pass" / resign -> pass (bot gives up).
         */
        fun parseGenmovePacket(line: String, fallbackWinrate: Double = 0.5, fallbackScoreLead: Double = 0.0): EngineMove {
            val t = line.trim()
            if (t.startsWith("?")) throw IllegalStateException("KataGo GTP error: $t")
            val move = Regex("^(?:=\\s*)?(?:play\\s+)?(\\S+)").find(t)?.groupValues?.get(1)
                ?: throw IllegalStateException("KataGo GTP bad genmove response: $t")
            if (move == "pass" || move == "resign") {
                return EngineMove(4, 4, pass = true, winrate = fallbackWinrate, scoreLead = fallbackScoreLead)
            }
            val p = parsePoint(move) ?: throw IllegalStateException("KataGo GTP bad move: $move")
            return EngineMove(p.first, p.second, winrate = fallbackWinrate, scoreLead = fallbackScoreLead)
        }

        /** "= B+3.5" -> +3.5 (Black leads, komi in); "= W+12.0" -> -12.0; "= 0" -> 0. */
        fun parseFinalScore(line: String): ScoreResult {
            val t = line.trim()
            if (t.startsWith("?")) throw IllegalStateException("KataGo GTP error: $t")
            if (Regex("^=\\s*0\\b").containsMatchIn(t)) return ScoreResult(0.0, null)
            val m = Regex("^=\\s*([BW])\\+([0-9.]+)").find(t)
                ?: throw IllegalStateException("KataGo GTP bad final_score: $t")
            val margin = m.groupValues[2].toDoubleOrNull()
                ?: throw IllegalStateException("KataGo GTP bad final_score: $t")
            return ScoreResult(if (m.groupValues[1] == "B") margin else -margin, null)
        }

        fun parsePoint(coord: String): Pair<Int, Int>? {
            if (coord == "pass" || !coord.matches(Regex("[A-HJ][1-9]"))) return null
            val letters = "ABCDEFGHJKLMNOPQRST"
            val xi = letters.indexOf(coord[0])
            if (xi < 0 || xi >= 9) return null
            val y = 9 - coord.substring(1).toInt()
            if (y !in 0 until 9) return null
            return xi to y
        }

        /**
         * GTP analyze is TEXT, not JSON (KataGo source cpp/command/gtp.cpp —
         * verified against master; format stable since BadukAI parses it the
         * same way). One report = one line:
         *   info move E5 visits 10 ... winrate 0.46 ... scoreLead -2.1 ... prior 0.19 ... order 0 pv ...
         *   info move C3 ... ... rootInfo visits 25 winrate 0.46 ... scoreLead -2.1 ...
         * There is NO humanPrior field — only the main-net `prior`. The human
         * signal comes from kata-raw-human-nn (see parseRawHumanPolicy).
         */
        data class MoveStat(
            val move: String,
            val visits: Int,
            val winrate: Double,
            val scoreLead: Double,
            val prior: Double,
            val order: Int,
            /** Symmetry-padded filler ("isSymmetryOf") — not a real distinct move. */
            val padded: Boolean = false,
        )
        data class RootStat(val visits: Int, val winrate: Double, val scoreLead: Double)
        data class AnalyzeReport(val moves: List<MoveStat>, val root: RootStat?)

        fun parseAnalyzeLine(line: String): AnalyzeReport {
            val moves = ArrayList<MoveStat>()
            var root: RootStat? = null
            // Split into segments: "info ..." per move, one "rootInfo ...".
            val segs = line.split(Regex("\\s(?=info |rootInfo )"))
            for (seg in segs) {
                val t = seg.trim().split(Regex("\\s+"))
                if (t.isEmpty()) continue
                fun valOf(key: String): String? {
                    val i = t.indexOf(key)
                    return if (i >= 0 && i + 1 < t.size) t[i + 1] else null
                }
                when (t[0]) {
                    "info" -> {
                        val mv = valOf("move") ?: continue
                        if (mv == "pass") continue
                        if (!mv.matches(Regex("[A-HJ][1-9]"))) continue
                        moves.add(
                            MoveStat(
                                move = mv,
                                visits = valOf("visits")?.toIntOrNull() ?: 0,
                                winrate = valOf("winrate")?.toDoubleOrNull() ?: 0.5,
                                scoreLead = valOf("scoreLead")?.toDoubleOrNull() ?: 0.0,
                                prior = valOf("prior")?.toDoubleOrNull() ?: 0.0,
                                order = valOf("order")?.toIntOrNull() ?: 999,
                                padded = t.contains("isSymmetryOf"),
                            )
                        )
                    }
                    "rootInfo" -> {
                        root = RootStat(
                            visits = valOf("visits")?.toIntOrNull() ?: 0,
                            winrate = valOf("winrate")?.toDoubleOrNull() ?: 0.5,
                            scoreLead = valOf("scoreLead")?.toDoubleOrNull() ?: 0.0,
                        )
                    }
                }
            }
            return AnalyzeReport(moves, root)
        }

        /**
         * Human-net raw policy grid from `kata-raw-human-nn <C> 0` (multi-line
         * `=` response). Profile-aware: KataGo evaluates with the currently set
         * humanSLProfile — this is the TRUE humanPrior the JSON engine used to
         * hand us. Returns (point -> prob); pass prob is dropped (candidates
         * never offer pass, same as before).
         */
        fun parseRawHumanPolicy(response: String, boardSize: Int = 9): Map<Pair<Int, Int>, Double> {
            val lines = response.lines()
            val pi = lines.indexOfFirst { it.trim() == "policy" }
            if (pi < 0) {
                val head = response.replace("\n", " | ").take(300)
                throw IllegalStateException("kata-raw-human-nn: no policy grid in response ($head)")
            }
            val out = HashMap<Pair<Int, Int>, Double>()
            for (r in 0 until boardSize) {
                val row = lines.getOrNull(pi + 1 + r)?.trim()?.split(Regex("\\s+")) ?: break
                for (c in 0 until minOf(boardSize, row.size)) {
                    val p = row[c].toDoubleOrNull() ?: continue
                    if (p > 0) out[c to r] = p
                }
            }
            if (out.isEmpty()) throw IllegalStateException("kata-raw-human-nn: empty policy grid")
            return out
        }
    }

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private val writeLock = Any()
    private val responses = LinkedBlockingQueue<String>()
    private val analyzeLines = LinkedBlockingQueue<String>()
    private val analyzeCollecting = java.util.concurrent.atomic.AtomicBoolean(false)
    // kata-raw-human-nn answers multi-line (policy grid); framed by blank line.
    private val rawCollecting = java.util.concurrent.atomic.AtomicBoolean(false)
    private val rawBuf = StringBuilder()
    private var readerThread: Thread? = null
    private val startMutex = Mutex()
    /**
     * Single-flight for ALL engine ops. GTP responses are anonymous (`= ...`
     * lines), so two concurrent commands could consume each other's replies.
     * Abandoned polls (fetchJob cancellation) consume-and-discard, and payload
     * ops drain landed stales — failures stay loud, never misattributed.
     */
    private val engineMutex = Mutex()
    /** Plies the engine's persistent board holds (genmove/play/undo tracked). */
    private var enginePlies = 0

    /** Staged engine binary under filesDir (copied from the APK's bundled jniLibs). */
    fun stagedBinary(): File = File(File(appContext.filesDir, "katago"), "libkatago.so")

    fun binaryPresent(): Boolean = stagedBinary().exists()

    fun bundledBinary(): File? {
        val dir = appContext.applicationInfo.nativeLibraryDir ?: return null
        return File(dir, "libkatago.so").takeIf { it.exists() }
    }

    /**
     * Stage the APK-bundled engine (libkatago.so AND its DT_NEEDED companions)
     * plus the GTP config into filesDir. The cfg is ALWAYS rewritten: absolute
     * write paths (logDir/homeDataDir) and the thread count depend on this
     * install, and values change without changing the asset size.
     */
    suspend fun stageIfNeeded(): Unit = withContext(Dispatchers.IO) {
        val katagoDir = File(appContext.filesDir, "katago").apply { mkdirs() }
        val nativeLibDir = appContext.applicationInfo.nativeLibraryDir
            ?: throw IllegalStateException("nativeLibraryDir unavailable — reinstall the app")
        val engineSrc = File(nativeLibDir, "libkatago.so")
        if (!engineSrc.exists()) {
            throw IllegalStateException("libkatago.so missing from the APK — rebuild with app/src/main/jniLibs (see scripts/vendor-engine.sh)")
        }
        val engineDst = File(katagoDir, "libkatago.so")
        val revFile = File(katagoDir, ".patchrev")
        val stagedRev = try {
            revFile.takeIf { it.exists() }?.readText()?.trim()
        } catch (_: Exception) {
            null
        }
        if (!engineDst.exists() || engineDst.length() != engineSrc.length() ||
            stagedRev != ENGINE_PATCH_REV
        ) {
            Log.i(TAG, "staging libkatago.so rev $ENGINE_PATCH_REV (${engineSrc.length()} bytes)")
            engineSrc.copyTo(engineDst, overwrite = true)
            try {
                revFile.writeText(ENGINE_PATCH_REV)
            } catch (_: Exception) {
            }
        }
        val depNames = Staging.REQUIRED_LIBS.filter { it != "libkatago.so" }
        val bundledSizes = depNames.associateWith { name ->
            val f = File(nativeLibDir, name)
            if (!f.exists()) {
                throw IllegalStateException("$name missing from the APK — vendor it (see scripts/vendor-engine.sh)")
            }
            f.length()
        }
        val stagedSizes = depNames.associateWith { name ->
            File(katagoDir, name).takeIf { it.exists() }?.length()
        }.mapValues { it.value ?: -1L }
        for (name in Staging.planCopies(bundledSizes, stagedSizes)) {
            Log.i(TAG, "staging $name (${bundledSizes.getValue(name)} bytes)")
            File(nativeLibDir, name).copyTo(File(katagoDir, name), overwrite = true)
        }
        // NPU detection (BadukAI's choose_kg_binary logic): a .dlc next to the
        // model means the SNPE build can use the accelerator -> 8 search threads
        // (BadukAI's dynamic value). Eigen CPU stays at 2 (measured: 8T is
        // SLOWER than 2T on Eigen — oversubscription; see ON_DEVICE.md).
        val modelDir = File(appContext.filesDir, "models")
        val hasDlc = modelDir.listFiles()?.any { it.name.endsWith(".dlc") } == true
        val threads = if (hasDlc) 8 else 2
        val base = appContext.assets.open(Staging.CONFIG_ASSET).use { it.readBytes().decodeToString() }
        val cfgText = buildString {
            append(base)
            if (!base.endsWith("\n")) append("\n")
            append("\n# --- appended by KataGoGtpEngine.stageIfNeeded (rev $ENGINE_PATCH_REV) ---\n")
            append("logDir = ${File(appContext.filesDir, "gtp_logs").absolutePath}\n")
            append("homeDataDir = ${appContext.filesDir.absolutePath}\n")
            append("numSearchThreads = $threads\n")
        }
        File(appContext.filesDir, "gtp.cfg").writeText(cfgText)
        Log.i(TAG, "staged gtp.cfg rev $ENGINE_PATCH_REV threads=$threads dlc=$hasDlc")
        // The b10 SEARCH net ships inside the APK (12MB, offline-first): stage
        // it like the binary. The b28 strong net + the v5 asset name are obsolete.
        val mainDst = File(modelDir, B10_ASSET.substringAfterLast('/'))
        if (!mainDst.exists() || mainDst.length() != B10_ASSET_BYTES || stagedRev != ENGINE_PATCH_REV) {
            Log.i(TAG, "staging $B10_ASSET")
            appContext.assets.open(B10_ASSET).use { input ->
                mainDst.outputStream().use { output -> input.copyTo(output) }
            }
            try {
                revFile.writeText(ENGINE_PATCH_REV)
            } catch (_: Exception) {
            }
        }
        File(modelDir, "strong.bin.gz").takeIf { it.exists() }?.let {
            Log.i(TAG, "removing obsolete strong.bin.gz (${it.length()} bytes)")
            it.delete()
        }
        File(modelDir, "b10.bin.gz").takeIf { it.exists() }?.let {
            Log.i(TAG, "removing obsolete b10.bin.gz (${it.length()} bytes)")
            it.delete()
        }
    }

    /**
     * Start the engine. Throws IllegalStateException with install instructions when
     * the binary or models are absent — the game cannot run without KataGo.
     */
    suspend fun ensureStarted(): Boolean = startMutex.withLock {
        withContext(Dispatchers.IO) {
            if (_engineReady.value && process != null) return@withContext true
            if (!binaryPresent()) {
                throw IllegalStateException(
                    "KataGo engine not found — push libkatago.so, models/ (human net) and gtp.cfg " +
                        "into the app filesDir, then restart"
                )
            }
            try {
                stageIfNeeded()
                val filesDir = appContext.filesDir
                val bin = stagedBinary()
                val modelDir = File(filesDir, "models")
                // Split-net play: b10 SEARCHES (fast), b18-human STEERS.
                val (main, human) = selectModels(modelDir.listFiles()?.toList() ?: emptyList())
                val cfg = File(filesDir, "gtp.cfg")
                if (!cfg.exists()) {
                    throw IllegalStateException("KataGo gtp.cfg missing — reinstall the app")
                }
                val nativeLibDir = appContext.applicationInfo.nativeLibraryDir ?: ""
                val katagoDir = File(filesDir, "katago")
                val cmd = listOf(
                    "/system/bin/linker64", bin.absolutePath, "gtp",
                    "-model", main.absolutePath,
                    "-human-model", human.absolutePath,
                    "-config", cfg.absolutePath,
                )
                val ldPath = Staging.ldPath(listOf(katagoDir.absolutePath, nativeLibDir, "/system/lib64", "/vendor/lib64"))
                Log.i(TAG, "build versionCode=${com.gotrainer.nine.BuildConfig.VERSION_CODE}")
                Log.i(TAG, "spawning: ${cmd.joinToString(" ")}")
                Log.i(TAG, "cwd=$GATE_CWD (package-gate requirement) LD_LIBRARY_PATH=$ldPath")
                Log.i(TAG, "bin=${bin.length()}B main=${main.length()}B human=${human.length()}B cfg=${cfg.length()}B")
                val pb = ProcessBuilder(cmd).apply {
                    directory(File(GATE_CWD))
                    environment().clear()
                    environment()["LD_LIBRARY_PATH"] = ldPath
                    environment()["PATH"] = "/system/bin:/system/xbin"
                    environment()["HOME"] = filesDir.absolutePath
                }
                val stderrFile = File(filesDir, "katago-stderr.log")
                if (stderrFile.exists()) stderrFile.delete()
                pb.redirectError(ProcessBuilder.Redirect.to(stderrFile))
                process = pb.start()
                writer = process!!.outputStream.bufferedWriter()
                startReader(process!!.inputStream.bufferedReader())
                startDeathWatch(process!!, stderrFile)
                // GTP prints nothing spontaneously: `version` forces a response,
                // which only arrives after the model is loaded. This doubles as
                // the ready check (BadukAI just sleeps 2s; we fail fast instead).
                val t0 = System.currentTimeMillis()
                send("version")
                val ver = withTimeout(START_TIMEOUT_MS) {
                    responses.poll(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } ?: throw IllegalStateException("KataGo GTP version timeout — model load stuck? check filesDir/katago-stderr.log")
                if (ver.trimStart().startsWith("?")) {
                    throw IllegalStateException("KataGo GTP error on version: $ver")
                }
                Log.i(TAG, "KataGo ready after ${System.currentTimeMillis() - t0}ms ($ver)")
                // Board setup. A default HumanSL profile is armed here because
                // searches (even the warmup) with -human-model but NO profile
                // die silently (exit 1, observed). Per-game/per-call setProfile
                // re-arms the real rank afterwards (cheap, idempotent).
                command("boardsize 9")
                command("clear_board")
                command("komi 7")
                command("kata-set-rules japanese")
                setProfile(Rank.R10K)
                enginePlies = 0
                // Warmup: one throwaway genmove pays the one-time NN-cache fill
                // here, invisible at launch, so move 1 feels like move N.
                // Cleared right after. (Plain `kata-genmove` is NOT a v1.16
                // command — `? unknown command` in 0ms. The analyze variant is.)
                try {
                    val w0 = System.currentTimeMillis()
                    command("kata-set-param maxVisits $PLAY_VISITS")
                    command("time_settings 0 $THINK_TIME_SEC 1")
                    val warmupMove = awaitMoveResult("B")
                    parseGenmovePacket(warmupMove) // validates, result discarded
                    command("clear_board")
                    enginePlies = 0
                    Log.i(TAG, "KataGo warmup genmove took ${System.currentTimeMillis() - w0}ms")
                } catch (e: Exception) {
                    Log.w(TAG, "warmup genmove failed (non-fatal)", e)
                    try {
                        command("clear_board")
                    } catch (_: Exception) {
                    }
                    enginePlies = 0
                }
                _engineReady.value = true
                Log.i(TAG, "KataGo GTP engine started")
                true
            } catch (e: Exception) {
                Log.e(TAG, "KataGo start failed", e)
                _engineReady.value = false
                throw e as? IllegalStateException ?: IllegalStateException("KataGo start failed: ${e.message}", e)
            }
        }
    }

    /** Logs when the child dies and how long it lived. */
    private fun startDeathWatch(proc: Process, stderrFile: File) {
        Thread({
            val t0 = System.currentTimeMillis()
            try {
                while (proc.isAlive && System.currentTimeMillis() - t0 <= START_TIMEOUT_MS + 30_000) {
                    Thread.sleep(500)
                }
                if (proc.isAlive) return@Thread
                val lived = System.currentTimeMillis() - t0
                val exit = try {
                    proc.exitValue()
                } catch (_: Exception) {
                    null
                }
                val tail = try {
                    stderrFile.takeIf { it.exists() }?.readText()?.takeLast(800)
                } catch (_: Exception) {
                    null
                }
                Log.e(TAG, "child died after ${lived}ms exit=$exit stderrTail=${tail ?: "<none>"}")
            } catch (e: Exception) {
                Log.e(TAG, "death watch ended", e)
            }
        }, "katago-deathwatch").apply { isDaemon = true; start() }
    }

    /**
     * GTP routing: single-line `=`/`?` responses complete immediately;
     * kata-analyze text lines (`info ...`, one report per line) route to the
     * analyze buffer while collecting; kata-raw-human-nn's multi-line grid is
     * framed by its terminating blank line. Everything else is diagnostics.
     */
    private fun startReader(reader: BufferedReader) {
        readerThread = Thread({
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val raw = line!!
                    val l = raw.trim()
                    if (rawCollecting.get()) {
                        if (l.isEmpty()) {
                            // A blank with an EMPTY buffer is a stray terminator from
                            // the previous streaming command (kata-analyze emits one
                            // when the next command stops it) — not our payload's
                            // end. Observed: raw-nn "succeeded" with 0 bytes in 7ms.
                            val isEmpty: Boolean
                            val combined: String
                            synchronized(rawBuf) {
                                isEmpty = rawBuf.isEmpty()
                                combined = rawBuf.toString()
                                rawBuf.clear()
                            }
                            if (!isEmpty) {
                                rawCollecting.set(false)
                                responses.offer(combined)
                            }
                        } else {
                            synchronized(rawBuf) { rawBuf.append(raw).append("\n") }
                        }
                        continue
                    }
                    if (l.isEmpty()) continue
                    if (l.startsWith("=") || l.startsWith("?") || PLAY_LINE.matches(l)) {
                        // `play <move>`: the genmove_analyze result line (v1.16
                        // prints it bare, AFTER the upfront `=` header). No other
                        // GTP output starts with "play ".
                        responses.offer(l)
                    } else if (analyzeCollecting.get()) {
                        analyzeLines.offer(l)
                    } else {
                        Log.i(TAG, "[katago-out] ${l.take(300)}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "KataGo reader ended", e)
            }
        }, "katago-reader").apply { isDaemon = true; start() }
    }

    private fun send(cmd: String) {
        val w = writer ?: throw IllegalStateException("engine not started")
        synchronized(writeLock) {
            w.write(cmd)
            w.newLine()
            w.flush()
        }
    }

    /** One command, one "= ..." line back (throws on "? ..." or timeout). */
    private suspend fun command(cmd: String, timeoutMs: Long = CMD_TIMEOUT_MS): String =
        withContext(Dispatchers.IO) {
            send(cmd)
            val got = withTimeout(timeoutMs) {
                responses.poll(timeoutMs, TimeUnit.MILLISECONDS)
            } ?: throw IllegalStateException("KataGo GTP timeout on '$cmd' (dead engine?)")
            if (got.trimStart().startsWith("?")) {
                throw IllegalStateException("KataGo GTP error on '$cmd': $got")
            }
            // A dead engine surfaces as a bare timeout — attach the exit code.
            val proc = process
            if (proc != null && !proc.isAlive) {
                throw IllegalStateException("KataGo process dead (exit=${proc.exitValue()}) on '$cmd'")
            }
            got
        }

    /** (Re)arm HumanSL play: profile for this rank + BadukAI's param bundle. */
    private suspend fun setProfile(rank: Rank) {
        command("kata-set-param humanSLProfile ${profileFor(rank)}")
        val params = JSONObject(humanSlParams().toMap()).toString()
        command("kata-set-params $params")
    }

    /**
     * Analysis mode for candidates: HumanSL OFF (profile `_`) + unbiased params
     * + wide root noise. The raw human policy is fetched BEFORE this (it needs
     * the profile armed). BadukAI does the same before its analysis panel.
     */
    private suspend fun armAnalysisMode() {
        command("kata-set-param humanSLProfile _")
        val params = JSONObject(humanSlParamsOff().toMap()).toString()
        command("kata-set-params $params")
    }

    /** Reconcile the persistent board with our history (prefix-aware, self-healing). */
    private suspend fun syncTo(history: List<MoveRec>) {
        ensureStarted()
        if (enginePlies == history.size) return
        // Desync (missed play/undo, or a restart): rebuild by replay. `play`
        // returns instantly (no search), so even a full 81-move replay is ms.
        command("clear_board")
        for (h in history) {
            val c = if (h.color == 1) "B" else "W"
            val m = if (h.x < 0) "pass" else GoBoard.gtpCoord(h.x, h.y)
            command("play $c $m")
        }
        enginePlies = history.size
    }

    /** Multi-line responses (kata-raw-human-nn policy grid), blank-line framed. */
    private suspend fun rawCommand(cmd: String, timeoutMs: Long = CMD_TIMEOUT_MS): String =
        withContext(Dispatchers.IO) {
            // Streaming commands (kata-analyze) print their "= " header up front
            // and never terminate it — drain any such stale header so THIS
            // command's payload can't be confused with it (observed: raw-nn
            // returned the analyze header, "no policy grid in response").
            responses.clear()
            synchronized(rawBuf) { rawBuf.clear() }
            rawCollecting.set(true)
            try {
                send(cmd)
                withTimeout(timeoutMs) {
                    responses.poll(timeoutMs, TimeUnit.MILLISECONDS)
                } ?: throw IllegalStateException("KataGo GTP timeout on '$cmd'")
            } finally {
                rawCollecting.set(false)
            }
        }

    /**
     * Run kata-analyze and keep the richest report (most root visits). One
     * report = one text line (`info ...` segments + `rootInfo ...`). The
     * analysis keeps streaming until the next command, so the terminator
     * doubles as the budget restore (see callers).
     */
    private suspend fun collectAnalyze(color: String, needVisits: Int, deadlineMs: Long): AnalyzeReport? =
        withContext(Dispatchers.IO) {
            analyzeLines.clear()
            analyzeCollecting.set(true)
            try {
                send("kata-analyze $color 5 rootInfo true")
                val tAnalyze = System.currentTimeMillis()
                var best: AnalyzeReport? = null
                var bestVisits = -1
                val t0 = System.currentTimeMillis()
                while (System.currentTimeMillis() - t0 < deadlineMs) {
                    val line = analyzeLines.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    try {
                        val rep = parseAnalyzeLine(line)
                        val v = rep.root?.visits ?: 0
                        if (rep.moves.isNotEmpty() && v >= bestVisits) {
                            bestVisits = v
                            best = rep
                        }
                        if (v >= needVisits) {
                            Log.i(TAG, "analyze reached $v visits in ${System.currentTimeMillis() - tAnalyze}ms")
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
                if (bestVisits < needVisits) {
                    Log.w(TAG, "analyze stalled at $bestVisits/$needVisits visits after ${System.currentTimeMillis() - tAnalyze}ms")
                }
                best
            } finally {
                analyzeCollecting.set(false)
                // The analyze header ("= ") is printed at stream start and has no
                // terminator: drop it now, or the NEXT command's wait consumes it.
                responses.clear()
            }
        }

    // ---- GoEngine: board sync (persistent GTP tree) ----

    override suspend fun newGame(rank: Rank) = engineMutex.withLock {
        ensureStarted()
        command("clear_board")
        command("komi 7")
        command("kata-set-rules japanese")
        setProfile(rank)
        enginePlies = 0
    }

    override suspend fun playMove(color: Int, x: Int, y: Int) {
        engineMutex.withLock {
            if (!_engineReady.value) return // syncTo() self-heals on the next query
            val c = if (color == 1) "B" else "W"
            val m = if (x < 0) "pass" else GoBoard.gtpCoord(x, y)
            command("play $c $m")
            enginePlies++
        }
    }

    override suspend fun undoMoves(n: Int) = engineMutex.withLock {
        if (!_engineReady.value || n <= 0) return // syncTo() self-heals
        repeat(n) {
            command("undo")
            enginePlies--
        }
    }

    /**
     * Shared `kata-genmove_analyze` driver: sends, collects analyze lines for
     * the graph, skips the upfront bare `=` header, returns the `play <move>`
     * (or `= <move>`) result line. Single-flight via engineMutex (callers).
     */
    private suspend fun awaitMoveResult(color: String): String = withContext(Dispatchers.IO) {
        analyzeLines.clear()
        analyzeCollecting.set(true)
        try {
            send("kata-genmove_analyze $color 5 rootInfo true")
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < GENMOVE_TIMEOUT_MS) {
                val got = withTimeout(GENMOVE_TIMEOUT_MS) {
                    responses.poll(GENMOVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } ?: break
                val g = got.trim()
                if (g == "=" || g.isEmpty()) continue // upfront header
                return@withContext g
            }
            throw IllegalStateException("KataGo genmove timeout (dead engine?)")
        } finally {
            analyzeCollecting.set(false)
        }
    }

    // ---- GoEngine: queries ----

    override suspend fun candidates(
        board: List<List<Int>>,
        toMove: Int,
        rank: Rank,
        n: Int,
        strategy: Strategy,
        history: List<MoveRec>,
    ): List<Candidate> = engineMutex.withLock {
        val t0 = System.currentTimeMillis()
        try {
            responses.clear() // drop stales from cancelled ops (see engineMutex)
            syncTo(history)
            setProfile(rank)
            // Human priors from the human net (profile-aware): the TRUE
            // humanlikeness signal. Falls back to search prior loudly-logged.
            // v1.16 raw-human-nn takes ONLY the symmetry (no color arg;
            // the color-first form is newer and errors here).
            val grid: Map<Pair<Int, Int>, Double> = try {
                val tRaw = System.currentTimeMillis()
                val raw = rawCommand("kata-raw-human-nn 0")
                Log.i(TAG, "raw human policy took ${System.currentTimeMillis() - tRaw}ms (${raw.length}B)")
                parseRawHumanPolicy(raw)
            } catch (e: Exception) {
                Log.e(TAG, "raw human policy failed, falling back to search prior", e)
                emptyMap()
            }
            // Broad, scored analysis (HumanSL off + wide root noise), then restore
            // the play profile for the next bot reply.
            armAnalysisMode()
            val color = if (toMove == 1) "B" else "W"
            val best = collectAnalyze(color, ANALYZE_VISITS, ANALYZE_TIMEOUT_MS)
            setProfile(rank)
            // Symmetry-padded filler (minmoves/getAnalysisData) is not a real
            // distinct candidate; keep it only if the real pool is too small.
            val all = best?.moves ?: emptyList()
            val real = all.filter { !it.padded }
            val stats = if (real.size >= n) real else real + all.filter { it.padded }
            Log.i(TAG, "candidates pool: ${real.size} real / ${all.size} total (n=$n)")
            if (stats.isEmpty()) throw IllegalStateException("KataGo analyze returned no legal moves")
            // Orientation sanity (log-only): the grid's top move must at least BE
            // one of the analyzed moves. Equality with the search's order-0 move
            // is NOT expected — HumanSL move choice is PIK sampling over the human
            // policy, not visit-argmax. Absent membership = transposed grid.
            try {
                val topGrid = grid.maxByOrNull { it.value }?.key
                val searchPts = stats.mapNotNull { parsePoint(it.move) }.toSet()
                if (topGrid != null && topGrid !in searchPts) {
                    Log.w(TAG, "raw policy top $topGrid absent from analyzed moves (orientation?)")
                }
            } catch (_: Exception) {
            }
            fun entryOf(st: MoveStat): PoolEntry? {
                val p = parsePoint(st.move) ?: return null
                val hp = grid[p] ?: st.prior
                return PoolEntry(p.first, p.second, hp, st.winrate, st.scoreLead)
            }
            // Pools need real scores for gap math: analyzed moves only.
            val h = stats.sortedByDescending { grid[it.move.let { m -> parsePoint(m) }] ?: it.prior }.mapNotNull { entryOf(it) }
            val s = stats.sortedByDescending { it.scoreLead }.mapNotNull { entryOf(it) }
            if (h.isEmpty()) throw IllegalStateException("KataGo analyze returned no playable moves")
            val out = CandidateSelector.select(h, s.ifEmpty { h }, strategy, rank, n)
            Log.i(TAG, "candidates took ${System.currentTimeMillis() - t0}ms (${stats.size} moves)")
            out
        } catch (e: Exception) {
            Log.e(TAG, "candidates failed", e)
            throw e as? IllegalStateException ?: IllegalStateException("KataGo candidates failed: ${e.message}", e)
        }
    }

    override suspend fun genMove(
        board: List<List<Int>>,
        toMove: Int,
        rank: Rank,
        history: List<MoveRec>,
    ): EngineMove {
        val tEnter = System.currentTimeMillis()
        return engineMutex.withLock {
            val tLocked = System.currentTimeMillis()
            val t0 = tLocked
            try {
                responses.clear() // drop stales from cancelled ops (see engineMutex)
                analyzeLines.clear()
                syncTo(history)
                val tSync = System.currentTimeMillis()
                setProfile(rank)
                val tProf = System.currentTimeMillis()
                command("kata-set-param maxVisits $PLAY_VISITS")
                // BadukAI's exact play command: byo-yomi cap + visit cap, analysis
                // lines for the graph + `= move` played into the persistent tree.
                command("time_settings 0 $THINK_TIME_SEC 1")
                val tSetup = System.currentTimeMillis()
                val color = if (toMove == 1) "B" else "W"
                val move = awaitMoveResult(color)
            enginePlies++ // genmove (incl. pass/resign) registers on the board
            // Richest report gives the graph its winrate/scoreLead.
            var winrate = 0.5
            var scoreLead = 0.0
            var bestVisits = -1
            for (l in analyzeLines.toList()) {
                try {
                    val rep = parseAnalyzeLine(l)
                    val v = rep.root?.visits ?: 0
                    if (v >= bestVisits && rep.root != null) {
                        bestVisits = v
                        winrate = rep.root.winrate
                        scoreLead = rep.root.scoreLead
                    }
                } catch (_: Exception) {
                }
            }
            val em = parseGenmovePacket(move, winrate, scoreLead)
            val tDone = System.currentTimeMillis()
            Log.i(TAG, "genMove took ${tDone - t0}ms (mutexWait=${tLocked - tEnter} sync=${tSync - t0} profile=${tProf - tSync} setup=${tSetup - tProf} search=${tDone - tSetup}) -> $move")
            em
        } catch (e: Exception) {
            Log.e(TAG, "genMove failed", e)
            throw e as? IllegalStateException ?: IllegalStateException("KataGo genMove failed: ${e.message}", e)
        }
        }
    }

    override suspend fun evaluate(
        board: List<List<Int>>,
        move: Pair<Int, Int>,
        toMove: Int,
        rank: Rank,
        history: List<MoveRec>,
    ): Evaluation = engineMutex.withLock {
        try {
            responses.clear()
            syncTo(history)
            setProfile(rank)
            armAnalysisMode()
            armAnalysisMode()
            val color = if (toMove == 1) "B" else "W"
            val best = collectAnalyze(color, ANALYZE_VISITS, ANALYZE_TIMEOUT_MS)
            setProfile(rank)
            val root = best?.root
            Evaluation(root?.winrate ?: 0.5, root?.scoreLead ?: 0.0)
        } catch (e: Exception) {
            Log.e(TAG, "evaluate failed", e)
            throw e as? IllegalStateException ?: IllegalStateException("KataGo evaluate failed: ${e.message}", e)
        }
    }

    /**
     * Final score via GTP final_score under Japanese rules (komi 7): proper
     * counting, instant, no 250-visit analysis needed. No ownership grid —
     * ScoreResult.ownership stays null (the UI already handles that: it shows
     * the local estimate until the engine answers).
     */
    override suspend fun score(board: List<List<Int>>, history: List<MoveRec>): ScoreResult = engineMutex.withLock {
        try {
            responses.clear() // final_score's payload must be its own (see engineMutex)
            syncTo(history)
            val line = withContext(Dispatchers.IO) {
                send("final_score")
                withTimeout(SCORE_TIMEOUT_MS) {
                    responses.poll(SCORE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
            } ?: throw IllegalStateException("KataGo final_score timeout")
            parseFinalScore(line)
        } catch (e: Exception) {
            Log.e(TAG, "score failed", e)
            throw e as? IllegalStateException ?: IllegalStateException("KataGo score failed: ${e.message}", e)
        }
    }

    fun stop() {
        try {
            send("quit")
        } catch (_: Exception) {
        }
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        process?.destroy()
        process = null
        _engineReady.value = false
    }
}
