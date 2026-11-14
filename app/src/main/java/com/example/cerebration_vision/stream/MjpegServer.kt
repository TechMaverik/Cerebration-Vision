package com.example.cerebration_vision.stream

import java.io.BufferedOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Holds the most recent JPEG frame and wakes stream clients when a new one arrives. */
class FrameHub {
    private val lock = Object()
    private var frame: ByteArray? = null
    private var seq = 0L

    fun publish(jpeg: ByteArray) = synchronized(lock) {
        frame = jpeg
        seq++
        lock.notifyAll()
    }

    fun latest(): ByteArray? = synchronized(lock) { frame }

    fun clear() = synchronized(lock) { frame = null }

    /** Blocks until a frame newer than [afterSeq] exists, or returns null on timeout. */
    fun awaitNext(afterSeq: Long, timeoutMs: Long): Pair<Long, ByteArray>? = synchronized(lock) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (seq == afterSeq || frame == null) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return null
            lock.wait(left)
        }
        seq to frame!!
    }
}

/**
 * Minimal HTTP server exposing the camera on the local network.
 *
 *  - `/`              viewer page
 *  - `/stream.mjpg`   live MJPEG stream (multipart/x-mixed-replace)
 *  - `/snapshot.jpg`  the latest single frame
 *  - `/status.json`   stream status
 */
class MjpegServer(private val hub: FrameHub) {

    private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val sockets = Collections.synchronizedSet(HashSet<Socket>())
    private val streamClients = AtomicInteger(0)

    @Volatile var frameWidth = 0
    @Volatile var frameHeight = 0
    @Volatile var fps = 0

    val clientCount: Int get() = streamClients.get()
    val port: Int get() = serverSocket?.localPort ?: -1
    val isRunning: Boolean get() = serverSocket?.isClosed == false

    /** Binds to [preferredPort], or the next free port after it. Returns the bound port. */
    @Synchronized
    @Throws(IOException::class)
    fun start(preferredPort: Int = DEFAULT_PORT): Int {
        if (isRunning) return port
        var last: IOException? = null
        for (candidate in preferredPort until preferredPort + 10) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress(candidate))
                serverSocket = socket
                val executor = Executors.newCachedThreadPool()
                pool = executor
                executor.execute { acceptLoop(socket, executor) }
                return candidate
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("No free port")
    }

    @Synchronized
    fun stop() {
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        synchronized(sockets) {
            sockets.forEach { try { it.close() } catch (_: IOException) {} }
            sockets.clear()
        }
        pool?.shutdownNow()
        pool = null
        fps = 0
    }

    private fun acceptLoop(server: ServerSocket, executor: ExecutorService) {
        while (!server.isClosed) {
            try {
                val client = server.accept()
                sockets.add(client)
                executor.execute { handle(client) }
            } catch (_: IOException) {
                break // socket closed by stop()
            } catch (_: RuntimeException) {
                break // pool shut down
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 5000
            val reader = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = reader.readLine() ?: return
            while (true) { // discard headers
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1].substringBefore('?')
            val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

            if (method != "GET" && method != "HEAD") {
                respond(out, "405 Method Not Allowed", "text/plain", "Method not allowed".toByteArray())
                return
            }
            when (path) {
                "/", "/index.html" ->
                    respond(out, "200 OK", "text/html; charset=utf-8", VIEWER_HTML.toByteArray(), method == "HEAD")
                "/stream.mjpg", "/stream", "/video", "/video.mjpg" ->
                    if (method == "HEAD") respond(out, "200 OK", "multipart/x-mixed-replace; boundary=$BOUNDARY", ByteArray(0), true)
                    else stream(socket, out)
                "/snapshot.jpg", "/snapshot", "/shot.jpg" -> {
                    val frame = hub.latest()
                    if (frame == null) respond(out, "503 Service Unavailable", "text/plain", "No frame available yet".toByteArray())
                    else respond(out, "200 OK", "image/jpeg", frame, method == "HEAD")
                }
                "/status.json", "/status" -> {
                    val json = """{"app":"Cerebration Vision","streaming":true,"clients":${streamClients.get()},""" +
                        """"width":$frameWidth,"height":$frameHeight,"fps":$fps}"""
                    respond(out, "200 OK", "application/json", json.toByteArray(), method == "HEAD")
                }
                else -> respond(out, "404 Not Found", "text/plain", "Not found".toByteArray())
            }
        } catch (_: IOException) {
            // client went away
        } finally {
            sockets.remove(socket)
            try { socket.close() } catch (_: IOException) {}
        }
    }

    private fun stream(socket: Socket, out: OutputStream) {
        streamClients.incrementAndGet()
        try {
            socket.soTimeout = 0
            out.write(
                ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                    NO_CACHE_HEADERS +
                    "Connection: close\r\n\r\n").toByteArray()
            )
            out.flush()
            var seq = -1L
            while (!socket.isClosed && !Thread.currentThread().isInterrupted) {
                // On timeout (camera paused) loop again; the write below detects dead clients.
                val (nextSeq, frame) = hub.awaitNext(seq, 2000) ?: continue
                seq = nextSeq
                out.write(
                    ("--$BOUNDARY\r\n" +
                        "Content-Type: image/jpeg\r\n" +
                        "Content-Length: ${frame.size}\r\n\r\n").toByteArray()
                )
                out.write(frame)
                out.write("\r\n".toByteArray())
                out.flush()
            }
        } catch (_: InterruptedException) {
            // server stopping
        } finally {
            streamClients.decrementAndGet()
        }
    }

    private fun respond(out: OutputStream, status: String, type: String, body: ByteArray, headOnly: Boolean = false) {
        out.write(
            ("HTTP/1.1 $status\r\n" +
                "Content-Type: $type\r\n" +
                "Content-Length: ${body.size}\r\n" +
                NO_CACHE_HEADERS +
                "Connection: close\r\n\r\n").toByteArray()
        )
        if (!headOnly) out.write(body)
        out.flush()
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val BOUNDARY = "cerebrationframe"
        private const val NO_CACHE_HEADERS =
            "Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n" +
                "Pragma: no-cache\r\n" +
                "Access-Control-Allow-Origin: *\r\n"

        private val VIEWER_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Live Feed — Cerebration Vision</title>
<style>
  *,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
  :root{--bg:#f5f5f3;--surface:#fbfbfa;--surface2:#ededea;--border:#e3e3de;--text:#0c0c0b;--text-sec:#46443e;--muted:#76736b;
    --font-display:Ubuntu,'Segoe UI',system-ui,sans-serif;--font-mono:'Ubuntu Mono',ui-monospace,Menlo,monospace;color-scheme:light}
  @media (prefers-color-scheme:dark){:root{--bg:#0e0e0c;--surface:#141412;--surface2:#1c1c19;--border:#2a2a26;--text:#fff;--text-sec:#d9d7d0;--muted:#a39f95;color-scheme:dark}}
  body{font-family:var(--font-display);background:var(--bg);color:var(--text);min-height:100vh;padding:24px 16px}
  main{max-width:980px;margin:0 auto}
  header{display:flex;align-items:baseline;gap:12px;flex-wrap:wrap;padding-bottom:16px;border-bottom:1px solid var(--border);margin-bottom:20px}
  .brand{font-weight:700;font-size:24px;letter-spacing:-.02em}
  .kicker{font-family:var(--font-mono);font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:var(--muted)}
  .feed{background:#0c0c0b;border:1px solid var(--border);border-radius:3px;overflow:hidden;display:flex;justify-content:center}
  .feed img{display:block;max-width:100%;max-height:78vh;height:auto}
  .meta{display:flex;gap:10px;flex-wrap:wrap;margin-top:16px}
  .meta a,.meta span{font-family:var(--font-mono);font-size:12.5px;padding:8px 12px;border:1px solid var(--border);border-radius:2px;background:var(--surface);color:var(--text-sec);text-decoration:none}
  .meta a:hover{background:var(--surface2);color:var(--text)}
</style>
</head>
<body>
<main>
  <header><span class="brand">Cerebration</span><span class="kicker">Vision · Live site feed</span></header>
  <div class="feed"><img src="/stream.mjpg" alt="Live camera feed from the phone"></div>
  <div class="meta">
    <a href="/stream.mjpg">/stream.mjpg</a>
    <a href="/snapshot.jpg">/snapshot.jpg</a>
    <a href="/status.json">/status.json</a>
    <span id="st">—</span>
  </div>
</main>
<script>
  async function tick(){try{const s=await (await fetch('/status.json')).json();
    document.getElementById('st').textContent=s.width+'×'+s.height+' · '+s.fps+' fps · '+s.clients+' viewer(s)';}
    catch(e){document.getElementById('st').textContent='offline';}}
  tick();setInterval(tick,2000);
</script>
</body>
</html>
""".trimIndent()
    }
}
