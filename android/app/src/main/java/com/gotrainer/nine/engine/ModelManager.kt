package com.gotrainer.nine.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlin.math.min

/**
 * Owns the KataGo engine files under filesDir and downloads the big neural nets.
 *
 * Layout:
 *   filesDir/katago/libkatago.so                  (staged from the APK by KataGoGtpEngine)
 *   filesDir/gtp.cfg                                (staged from app assets)
 *   filesDir/models/b10.bin.gz                    (staged from app assets, 11MB SEARCH net)
 *   filesDir/models/b18c384nbt-humanv0.bin.gz     (downloaded, ~99MB HUMAN steering net)
 *
 * Split-net play (BadukAI's recipe): the small b10 does the searching
 * (fast evals), b18-human steers it via the HumanSL profile + raw policy.
 * The old b28 strong net is obsolete on-device (nothing uses it since the
 * GTP cutover) and is deleted on stage.
 *
 * The nets are the big, flaky downloads, so every file downloads with:
 * resume via HTTP Range, retries with exponential backoff, stall timeouts,
 * size verification and atomic rename (.part -> final). Nothing is trusted
 * until the final size matches the server-declared length.
 */
class ModelManager(
    private val filesDir: File,
    private val models: List<EngineFile> = MODELS,
    private val logger: EngineLogger = AndroidEngineLogger,
) {
    companion object {
        private const val TAG = "Models"
        private const val UA = "Mozilla/5.0 (Linux; Android 14) GoTrainer/1.0"
        private const val MAX_ATTEMPTS = 6
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val BUFFER = 262_144 // 256KB streaming writes, never hoarded in RAM

        val HUMAN = EngineFile(
            key = "human",
            displayName = "Human network",
            detail = "b18 · plays like a human at your rank",
            // Same file KaTrain downloads; the old katagotraining /modelsextra/ path 403s bots.
            url = "https://github.com/lightvector/KataGo/releases/download/v1.15.0/b18c384nbt-humanv0.bin.gz",
            fileName = "b18c384nbt-humanv0.bin.gz",
            approxSize = 99_066_230L,
        )
        val MODELS = listOf(HUMAN)
    }

    data class EngineFile(
        val key: String,
        val displayName: String,
        val detail: String,
        val url: String,
        val fileName: String,
        val approxSize: Long,
    )

    data class FileRow(val file: EngineFile, val present: Boolean, val bytes: Long)

    /** Injectable log sink — android.util.Log crashes plain JVM unit tests, so tests pass a silent one. */
    interface EngineLogger {
        fun i(msg: String)
        fun w(msg: String)
    }

    object AndroidEngineLogger : EngineLogger {
        override fun i(msg: String) {
            android.util.Log.i(TAG, msg)
        }
        override fun w(msg: String) {
            android.util.Log.w(TAG, msg)
        }
    }

    object SilentLogger : EngineLogger {
        override fun i(msg: String) = Unit
        override fun w(msg: String) = Unit
    }

    sealed interface DownloadState {
        data class Downloading(
            val fileIndex: Int,
            val fileCount: Int,
            val fileName: String,
            val doneBytes: Long,
            /** Null until the server declares a length (some hosts block HEAD). */
            val totalBytes: Long?,
            val overallDone: Long,
            val overallTotal: Long,
        ) : DownloadState

        data class Failed(val error: String) : DownloadState
        data object Done : DownloadState
    }

    private fun modelsDir(): File = File(filesDir, "models").apply { mkdirs() }

    fun destOf(file: EngineFile): File = File(modelsDir(), file.fileName)

    fun rows(): List<FileRow> = models.map { f ->
        val d = destOf(f)
        FileRow(f, d.exists() && d.length() > 0, if (d.exists()) d.length() else 0L)
    }

    fun isReady(): Boolean = models.isNotEmpty() && rows().all { it.present } &&
        File(filesDir, "gtp.cfg").exists() &&
        File(File(filesDir, "katago"), "libkatago.so").exists()

    /** Download every missing model; emits progress, ends with Done, throws loudly otherwise. */
    fun downloadMissing(): Flow<DownloadState> = flow {
        var overallTotal = models.sumOf { it.approxSize }
        var overallBase = models.sumOf { m -> destOf(m).takeIf { it.exists() }?.length() ?: 0L }
        models.forEachIndexed { index, file ->
            val dest = destOf(file)
            if (dest.exists() && dest.length() > 0) return@forEachIndexed
            var knownTotal: Long? = null
            var lastEmitBytes = -1_048_577L
            var lastEmitTime = 0L
            downloadOne(file) { done, total ->
                if (total != null && knownTotal == null) {
                    knownTotal = total
                    overallTotal += total - file.approxSize
                }
                val now = System.currentTimeMillis()
                val finished = total != null && done >= total
                if (finished || done - lastEmitBytes > 1_048_576 || now - lastEmitTime > 300) {
                    lastEmitBytes = done
                    lastEmitTime = now
                    emit(
                        DownloadState.Downloading(
                            fileIndex = index,
                            fileCount = models.size,
                            fileName = file.displayName,
                            doneBytes = done,
                            totalBytes = total ?: knownTotal,
                            overallDone = overallBase + done,
                            overallTotal = overallTotal,
                        ),
                    )
                }
            }
            overallBase += dest.length()
        }
        emit(DownloadState.Done)
    }.flowOn(Dispatchers.IO)

    /** Download one file with resume + retries; throws IOException after MAX_ATTEMPTS. */
    private suspend fun downloadOne(
        file: EngineFile,
        onProgress: suspend (doneBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        val dest = destOf(file)
        val part = File(dest.parent, dest.name + ".part")
        var attempt = 0
        var backoffMs = 1_000L
        while (true) {
            attempt++
            var conn: HttpURLConnection? = null
            try {
                coroutineContext.ensureActive()
                val have = if (part.exists()) part.length() else 0L
                // Instant feedback: the UI must react on tap and on every retry,
                // even before the server declares a length or sends a byte.
                onProgress(have, null)
                conn = (URL(file.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                // Unblock a stalled read promptly on cancellation.
                val hook = coroutineContext[Job]?.invokeOnCompletion { try { conn.disconnect() } catch (_: Exception) { } }
                try {
                    conn.connect()
                    when (conn.responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            // Server ignored Range: restart from scratch.
                            if (have > 0) {
                                logger.w("${file.fileName}: server ignored resume, restarting")
                                part.delete()
                            }
                            val total = conn.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 }
                            streamTo(conn, part, append = false, alreadyHave = 0L, declaredTotal = total, onProgress)
                        }
                        HttpURLConnection.HTTP_PARTIAL -> {
                            streamTo(conn, part, append = true, alreadyHave = have, declaredTotal = parseTotal(conn, have), onProgress)
                        }
                        429 -> throw IOException("rate limited (HTTP 429)")
                        in 500..599 -> throw IOException("server error (HTTP ${conn.responseCode})")
                        416 -> {
                            logger.w("${file.fileName}: range unsatisfiable, restarting")
                            part.delete()
                            if (attempt >= MAX_ATTEMPTS) throw IOException("range unsatisfiable (HTTP 416)")
                        }
                        else -> throw IOException("unexpected HTTP ${conn.responseCode}")
                    }
                } finally {
                    hook?.dispose()
                    try { conn.disconnect() } catch (_: Exception) { }
                }
                if (!part.exists() || part.length() == 0L) {
                    throw IOException("empty response for ${file.fileName}")
                }
                if (!part.renameTo(dest)) throw IOException("cannot publish ${file.fileName}")
                logger.i("${file.fileName} complete (${dest.length()} bytes)")
                return
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    logger.i("${file.fileName}: cancelled, keeping .part for resume")
                    throw e
                }
                if (attempt >= MAX_ATTEMPTS) {
                    throw IOException("download failed after $attempt attempts: ${e.message}", e)
                }
                logger.w("${file.fileName}: attempt $attempt failed (${e.message}), retry in ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = min(backoffMs * 2, 30_000L)
            } finally {
                try { conn?.disconnect() } catch (_: Exception) { }
            }
        }
    }

    private fun parseTotal(conn: HttpURLConnection, have: Long): Long? {
        // Prefer Content-Range: bytes 100-999/1234, fall back to Content-Length + offset.
        conn.getHeaderField("Content-Range")?.let { cr ->
            cr.substringAfterLast("/").toLongOrNull()?.let { return it }
        }
        val len = conn.getHeaderFieldLong("Content-Length", -1)
        if (len >= 0) return have + len
        return null
    }

    private suspend fun streamTo(
        conn: HttpURLConnection,
        part: File,
        append: Boolean,
        alreadyHave: Long,
        declaredTotal: Long?,
        onProgress: suspend (Long, Long?) -> Unit,
    ) {
        var done = alreadyHave
        onProgress(done, declaredTotal)
        FileOutputStream(part, append).use { out ->
            val buf = ByteArray(BUFFER)
            conn.inputStream.use { input ->
                while (true) {
                    coroutineContext.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    onProgress(done, declaredTotal)
                }
            }
        }
        if (declaredTotal != null && done != declaredTotal) {
            throw IOException("size mismatch: got $done bytes, server declared $declaredTotal")
        }
    }
}
