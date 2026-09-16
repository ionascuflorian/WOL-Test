package com.example.wolremote

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Verifică dacă laptopul răspunde pe rețeaua locală, după ce a pornit.
 * Folosit de WakeService pentru confirmarea pornirii.
 */
object HostCheck {

    private val DEFAULT_PORTS = intArrayOf(445, 135, 139, 3389)
    private const val CONNECT_TIMEOUT_MS = 2000

    fun isReachable(host: String): Boolean {
        val target = host.trim()
        if (target.isEmpty()) return false
        if (tcpReachable(target)) return true
        return pingReachable(target)
    }

    private fun tcpReachable(host: String): Boolean {
        for (port in DEFAULT_PORTS) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                return true
            } catch (_: Exception) {
                // port închis sau host neaccesibil — încercăm următorul
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
        return false
    }

    private fun pingReachable(host: String): Boolean {
        return try {
            val process = ProcessBuilder("/system/bin/ping", "-c", "1", "-W", "2", host)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (finished) process.exitValue() == 0 else {
                process.destroy()
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}