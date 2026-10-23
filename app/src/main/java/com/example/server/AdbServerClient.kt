package com.example.server

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

/**
 * IPC Client for communicating with the shell UID [FakeStandbyServer] daemon.
 *
 * Provides fast, offline communication that does NOT rely on Wi-Fi or active adbd connections.
 * Automatically tries:
 * 1. Unix Domain Socket (LocalSocket)
 * 2. Localhost TCP Socket (127.0.0.1:53921)
 */
object AdbServerClient {

    private const val TAG = "AdbServerClient"
    private const val PREFS_NAME = "fake_standby_server_prefs"
    private const val KEY_TOKEN = "server_auth_token"
    private const val TIMEOUT_MS = 600

    fun getOrCreateAuthToken(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_TOKEN, null)
        if (token.isNullOrEmpty()) {
            token = UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        return token
    }

    /**
     * Checks if the shell daemon is running and responsive.
     */
    suspend fun isDaemonRunning(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getOrCreateAuthToken(context)
        val response = sendCommand(token, "ping")
        response == "PONG"
    }

    /**
     * Requests the shell daemon to collapse the status bar / control center.
     * Returns true if successfully collapsed with UID 2000 shell privileges.
     */
    suspend fun collapseStatusBar(context: Context): Boolean = withContext(Dispatchers.IO) {
        val token = getOrCreateAuthToken(context)
        val response = sendCommand(token, "collapse")
        val success = response == "OK"
        if (success) {
            Log.i(TAG, "Successfully collapsed status bar via shell daemon!")
        } else {
            Log.w(TAG, "Shell daemon response: $response")
        }
        success
    }

    private fun sendCommand(token: String, action: String): String? {
        val command = "$token $action"

        // 1. Try Unix Domain Socket (LocalSocket)
        try {
            LocalSocket().use { socket ->
                socket.soTimeout = TIMEOUT_MS
                val address = LocalSocketAddress(FakeStandbyServer.SOCKET_NAME)
                socket.connect(address)

                val writer = PrintWriter(socket.outputStream, true)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))

                writer.println(command)
                writer.flush()
                val line = reader.readLine()
                if (!line.isNullOrEmpty()) {
                    return line.trim()
                }
            }
        } catch (e: Throwable) {
            // Unix socket may fail due to SELinux untrusted_app rules; fallback to TCP loopback
            Log.d(TAG, "Unix socket connection skipped/failed: ${e.message}")
        }

        // 2. Try Localhost TCP Socket (127.0.0.1:DEFAULT_PORT)
        try {
            Socket().use { socket ->
                socket.soTimeout = TIMEOUT_MS
                socket.connect(InetSocketAddress("127.0.0.1", FakeStandbyServer.DEFAULT_PORT), TIMEOUT_MS)

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                writer.println(command)
                writer.flush()
                val line = reader.readLine()
                if (!line.isNullOrEmpty()) {
                    return line.trim()
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "TCP socket connection failed: ${e.message}")
        }

        return null
    }
}
