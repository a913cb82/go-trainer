package com.gotrainer.nine.engine

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * Downloader robustness against a local HTTP server:
 * fresh download, resume from .part, and a flaky server (500s then success).
 */
class DownloaderTest {
    private lateinit var server: HttpServer
    private lateinit var filesDir: File
    private var port: Int = 0
    private val payload: ByteArray = ByteArray(3_000_000) { (it % 251).toByte() }
    private val flakyHits = AtomicInteger(0)

    @Before fun start() {
        filesDir = Files.createTempDirectory("models-test").toFile()
        server = HttpServer.create(InetSocketAddress(0), 0)
        port = server.address.port
        server.createContext("/full.bin") { ex -> serve(ex, flaky = false) }
        server.createContext("/flaky.bin") { ex -> serve(ex, flaky = true) }
        server.start()
    }

    @After fun stop() {
        server.stop(0)
        filesDir.deleteRecursively()
    }

    private fun serve(ex: com.sun.net.httpserver.HttpExchange, flaky: Boolean) {
        try {
            if (flaky && flakyHits.getAndIncrement() < 2) {
                ex.sendResponseHeaders(500, -1)
                return
            }
            val range = ex.requestHeaders.getFirst("Range")
            if (range != null && range.startsWith("bytes=")) {
                val from = range.removePrefix("bytes=").substringBefore("-").toLong()
                val slice = payload.copyOfRange(from.toInt(), payload.size)
                ex.responseHeaders.add("Content-Range", "bytes $from-${payload.size - 1}/${payload.size}")
                ex.sendResponseHeaders(206, slice.size.toLong())
                ex.responseBody.use { it.write(slice) }
            } else {
                ex.responseHeaders.add("Accept-Ranges", "bytes")
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.use { it.write(payload) }
            }
        } finally {
            ex.close()
        }
    }

    private fun fileFor(path: String, name: String) = ModelManager.EngineFile(
        key = "t", displayName = "Test", detail = "test",
        url = "http://127.0.0.1:$port$path", fileName = name, approxSize = payload.size.toLong(),
    )

    private fun modelsDir(): File = File(filesDir, "models").apply { mkdirs() }

    @Test fun `fresh download completes and publishes`() = runBlocking {
        ModelManager(filesDir, listOf(fileFor("/full.bin", "full.bin")), ModelManager.SilentLogger).downloadMissing().collect {}
        val dest = File(modelsDir(), "full.bin")
        assertTrue(dest.exists())
        assertArrayEquals(payload, dest.readBytes())
        assertTrue(!File(modelsDir(), "full.bin.part").exists())
    }

    @Test fun `resume continues from partial file`() = runBlocking {
        File(modelsDir(), "resume.bin.part").writeBytes(payload.copyOfRange(0, 1_000_000))
        ModelManager(filesDir, listOf(fileFor("/full.bin", "resume.bin")), ModelManager.SilentLogger).downloadMissing().collect {}
        assertArrayEquals(payload, File(modelsDir(), "resume.bin").readBytes())
    }

    @Test fun `flaky server succeeds via retries`() = runBlocking {
        ModelManager(filesDir, listOf(fileFor("/flaky.bin", "flaky.bin")), ModelManager.SilentLogger).downloadMissing().collect {}
        assertArrayEquals(payload, File(modelsDir(), "flaky.bin").readBytes())
    }

    @Test fun `progress ends at full size then done`() = runBlocking {
        val states = mutableListOf<ModelManager.DownloadState>()
        ModelManager(filesDir, listOf(fileFor("/full.bin", "prog.bin")), ModelManager.SilentLogger).downloadMissing().collect { states.add(it) }
        assertTrue(states.last() is ModelManager.DownloadState.Done)
        val prog = states.filterIsInstance<ModelManager.DownloadState.Downloading>()
        assertTrue(prog.isNotEmpty())
        assertEquals(payload.size.toLong(), prog.last().doneBytes)
    }
}
