package com.example.server

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Standalone background server process launched with UID 2000 (shell) via app_process:
 *
 * CLASSPATH=/data/app/.../base.apk nohup app_process /system/bin com.example.server.FakeStandbyServer <port> <token> &
 *
 * Runs persistently in the background independently of Wi-Fi or adbd wireless debugging state.
 * Listens on:
 * 1. Unix Domain Socket (LocalServerSocket: "fake_standby_shell_server")
 * 2. Localhost TCP Socket (127.0.0.1:<port>, default 53921)
 *
 * Executes `cmd statusbar collapse` with shell privileges whenever commanded by the app.
 */
@Keep
object FakeStandbyServer {

    private const val TAG = "FakeStandbyServer"
    const val DEFAULT_PORT = 53921
    const val SOCKET_NAME = "fake_standby_shell_server"

    @Volatile
    private var isRunning = true

    @JvmStatic
    fun main(args: Array<String>) {
        println("$TAG: Starting server process (PID: ${android.os.Process.myPid()}, UID: ${android.os.Process.myUid()})")

        val port = if (args.isNotEmpty()) {
            args[0].toIntOrNull() ?: DEFAULT_PORT
        } else {
            DEFAULT_PORT
        }

        val authToken = if (args.size > 1) {
            args[1].trim()
        } else {
            ""
        }

        println("$TAG: Configured on port $port, token length: ${authToken.length}")

        // 1. Start LocalSocket (Unix Domain Socket) listener thread (non-daemon for persistence)
        startUnixSocketListener(authToken)

        // 2. Start Localhost TCP Socket listener on loopback 127.0.0.1 (non-daemon for persistence)
        startTcpSocketListener(port, authToken)

        println("$TAG: Listeners initialized. Server ready.")

        // Keep main thread alive permanently
        while (isRunning) {
            try {
                Thread.sleep(60000)
            } catch (ignored: Throwable) {
                // Never exit on interrupt
            }
        }
        println("$TAG: Server stopped.")
    }

    private fun startUnixSocketListener(authToken: String) {
        thread(isDaemon = false, name = "UnixSocketListener") {
            while (isRunning) {
                var server: LocalServerSocket? = null
                try {
                    server = LocalServerSocket(SOCKET_NAME)
                    println("$TAG: Unix domain socket listening on @$SOCKET_NAME")
                    while (isRunning) {
                        try {
                            val client = server.accept()
                            handleUnixClient(client, authToken)
                        } catch (e: Exception) {
                            if (!isRunning) break
                            Log.e(TAG, "Error accepting Unix client", e)
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Unix domain socket error (SELinux or in-use): ${e.message}")
                    try { Thread.sleep(3000) } catch (ignored: Throwable) {}
                } finally {
                    try { server?.close() } catch (ignored: Throwable) {}
                }
            }
        }
    }

    private fun handleUnixClient(client: LocalSocket, authToken: String) {
        thread(isDaemon = true) {
            try {
                client.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.inputStream))
                    val writer = PrintWriter(s.outputStream, true)
                    val line = reader.readLine()?.trim().orEmpty()
                    val response = processCommand(line, authToken)
                    writer.println(response)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling Unix client request", e)
            }
        }
    }

    private fun startTcpSocketListener(port: Int, authToken: String) {
        thread(isDaemon = false, name = "TcpSocketListener") {
            val loopback = try {
                InetAddress.getByName("127.0.0.1")
            } catch (e: Throwable) {
                null
            }

            while (isRunning) {
                var serverSocket: ServerSocket? = null
                try {
                    serverSocket = if (loopback != null) {
                        ServerSocket(port, 50, loopback)
                    } else {
                        ServerSocket(port, 50)
                    }
                    println("$TAG: TCP socket listening on 127.0.0.1:$port")

                    while (isRunning) {
                        try {
                            val client = serverSocket.accept()
                            handleTcpClient(client, authToken)
                        } catch (e: Exception) {
                            if (!isRunning) break
                            Log.e(TAG, "Error accepting TCP client", e)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Could not bind TCP socket on 127.0.0.1:$port: ${e.message}")
                    try { Thread.sleep(3000) } catch (ignored: Throwable) {}
                } finally {
                    try { serverSocket?.close() } catch (ignored: Throwable) {}
                }
            }
        }
    }

    private fun handleTcpClient(client: Socket, authToken: String) {
        thread(isDaemon = true) {
            try {
                client.soTimeout = 3000
                client.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    val writer = PrintWriter(s.getOutputStream(), true)
                    val line = reader.readLine()?.trim().orEmpty()
                    val response = processCommand(line, authToken)
                    writer.println(response)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling TCP client request", e)
            }
        }
    }

    private fun processCommand(commandLine: String, authToken: String): String {
        if (commandLine.isEmpty()) return "ERR_EMPTY"

        val parts = commandLine.split(" ", limit = 2)
        val token = parts.getOrNull(0).orEmpty()
        val action = parts.getOrNull(1).orEmpty().ifEmpty { token }

        // Token validation (if configured)
        if (authToken.isNotEmpty() && token != authToken && action != "ping") {
            Log.w(TAG, "Unauthorized request attempt")
            return "ERR_UNAUTHORIZED"
        }

        return when (action) {
            "ping" -> "PONG"

            "collapse" -> {
                val ok = executeCollapse()
                if (ok) "OK" else "ERR_EXEC"
            }

            "exit" -> {
                isRunning = false
                "BYE"
            }

            else -> "ERR_UNKNOWN_CMD"
        }
    }

    private fun executeCollapse(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("cmd", "statusbar", "collapse"))
            val exitCode = process.waitFor()
            Log.d(TAG, "cmd statusbar collapse returned: $exitCode")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing cmd statusbar collapse", e)
            false
        }
    }
}
