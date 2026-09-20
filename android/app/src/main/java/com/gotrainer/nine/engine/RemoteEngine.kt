package com.gotrainer.nine.engine

import android.util.Log
import com.gotrainer.nine.game.Candidate
import com.gotrainer.nine.game.CandidateSelector
import com.gotrainer.nine.game.GoBoard
import com.gotrainer.nine.game.MoveRec
import com.gotrainer.nine.game.PoolEntry
import com.gotrainer.nine.game.Rank
import com.gotrainer.nine.game.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the go-trainer Node server (server/src/index.ts) over HTTP — the same
 * backend the web app uses, with the real CUDA KataGo behind it.
 *
 * Used two ways: (1) everyday play over `adb reverse` / LAN while the on-device
 * engine is unproven, (2) reference behavior to cross-check on-device results.
 * Loud on any failure: no mock fallback anywhere in this app.
 */
class RemoteEngine(private val baseUrl: String) : GoEngine {
    companion object {
        private const val TAG = "RemoteEngine"
        private const val TIMEOUT_MS = 20_000
        const val DEFAULT_URL = "http://127.0.0.1:3001"
    }

    private fun sideOf(color: Int): String = if (color == 1) "B" else "W"

    private fun historyJson(history: List<MoveRec>): JSONArray = JSONArray().apply {
        for (h in history) {
            put(JSONObject().put("x", h.x).put("y", h.y).put("color", h.color))
        }
    }

    private fun boardJson(board: List<List<Int>>): JSONArray = JSONArray().apply {
        for (row in board) put(JSONArray(row))
    }

    private suspend fun post(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", "GoTrainer-Android/1.0")
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText() ?: ""
            if (code !in 200..299) throw IOException("server $path -> HTTP $code: ${text.take(300)}")
            JSONObject(text)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "$path failed ($baseUrl)", e)
            throw IOException("engine server unreachable at $baseUrl — is it running? (${e.message})", e)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun candidateOf(o: JSONObject): Candidate = Candidate(
        x = o.getInt("x"),
        y = o.getInt("y"),
        label = o.optString("label", "?"),
        humanPolicy = o.optDouble("humanPolicy", 0.0),
        strongWinrate = o.optDouble("strongWinrate", 0.5),
        strongScore = o.optDouble("strongScore", 0.0),
        scoreGap = o.optDouble("scoreGap", 0.0),
        tag = o.optString("tag", "ok"),
    )

    override suspend fun candidates(
        board: List<List<Int>>,
        toMove: Int,
        rank: Rank,
        n: Int,
        strategy: Strategy,
        history: List<MoveRec>,
    ): List<Candidate> {
        val res = post(
            "/candidates",
            JSONObject()
                .put("board", boardJson(board))
                .put("toMove", sideOf(toMove))
                .put("rank", rank.id)
                .put("n", n)
                .put("strategy", strategy.id)
                .put("history", historyJson(history))
                .put("maxVisits", 400),
        )
        val arr = res.optJSONArray("moves") ?: JSONArray()
        return List(arr.length()) { i -> candidateOf(arr.getJSONObject(i)) }
    }

    override suspend fun genMove(
        board: List<List<Int>>,
        toMove: Int,
        rank: Rank,
        history: List<MoveRec>,
    ): EngineMove {
        val res = post(
            "/genmove",
            JSONObject()
                .put("board", boardJson(board))
                .put("toMove", sideOf(toMove))
                .put("rank", rank.id)
                .put("history", historyJson(history))
                .put("maxVisits", 400),
        )
        val m = res.getJSONObject("move")
        if (m.optBoolean("pass", false)) {
            return EngineMove(4, 4, pass = true, winrate = res.optDouble("winrate", 0.5), scoreLead = res.optDouble("scoreLead", 0.0))
        }
        return EngineMove(m.getInt("x"), m.getInt("y"), winrate = res.optDouble("winrate", 0.5), scoreLead = res.optDouble("scoreLead", 0.0))
    }

    override suspend fun evaluate(
        board: List<List<Int>>,
        move: Pair<Int, Int>,
        toMove: Int,
        rank: Rank,
        history: List<MoveRec>,
    ): Evaluation {
        val res = post(
            "/evaluate",
            JSONObject()
                .put("board", boardJson(board))
                .put("move", JSONObject().put("x", move.first).put("y", move.second))
                .put("toMove", sideOf(toMove))
                .put("rank", rank.id)
                .put("history", historyJson(history))
                .put("maxVisits", 400),
        )
        return Evaluation(res.optDouble("winrate", 0.5), res.optDouble("scoreLead", 0.0))
    }

    override suspend fun score(board: List<List<Int>>, history: List<MoveRec>): ScoreResult {
        val res = post(
            "/score",
            JSONObject()
                .put("board", boardJson(board))
                .put("history", historyJson(history))
                .put("maxVisits", 500),
        )
        val ownArr = res.optJSONArray("ownership")
        val ownership: List<List<Double>>? = ownArr?.takeIf { it.length() == 81 }?.let { arr ->
            List(9) { y -> List(9) { x -> arr.optDouble(y * 9 + x, 0.0) } }
        }
        return ScoreResult(res.optDouble("scoreLead", Double.NaN).takeIf { !it.isNaN() }
            ?: throw IOException("server /score returned no scoreLead"), ownership)
    }
}
