package com.example.wolremote

import android.content.Context
import android.net.ConnectivityManager
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress

object WakeOnLan {

    const val WOL_PORT = 9

    fun parseMac(raw: String): ByteArray {
        val cleaned = raw.replace(":", "").replace("-", "").replace(".", "").trim().uppercase()
        require(cleaned.length == 12) { "MAC address invalid (need 12 hex chars)" }
        require(cleaned.all { it in "0123456789ABCDEF" }) { "MAC address conține caractere invalide" }
        return ByteArray(6) { i -> cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    fun buildMagicPacket(mac: ByteArray): ByteArray {
        require(mac.size == 6) { "MAC must be 6 bytes" }
        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(mac, 0, packet, 6 + i * 6, 6)
        return packet
    }

    @Throws(IOException::class)
    fun send(mac: String, broadcastAddress: String, context: Context) {
        val macBytes = parseMac(mac)
        val magic = buildMagicPacket(macBytes)
        val target = if (broadcastAddress.isBlank()) {
            detectBroadcastAddress(context)
        } else {
            broadcastAddress.trim()
        }
        val inetAddress = try {
            InetAddress.getByName(target)
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid broadcast address: $target", e)
        }
        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            val packet = DatagramPacket(magic, magic.size, inetAddress, WOL_PORT)
            socket.send(packet)
        } finally {
            socket.close()
        }
    }

    private fun detectBroadcastAddress(context: Context): String {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            for (network in cm.allNetworks) {
                val props = cm.getLinkProperties(network) ?: continue
                for (linkAddress in props.linkAddresses) {
                    val address = linkAddress.address
                    if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        val prefix = linkAddress.prefixLength
                        if (prefix == 0 || prefix > 30) continue
                        val ipParts = address.address
                        val ip = bytesToLong(ipParts)
                        val mask = if (prefix == 32) 0xFFFFFFFFL else ((0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL)
                        val broadcast = (ip or (mask.inv() and 0xFFFFFFFFL)) and 0xFFFFFFFFL
                        return longToIp(broadcast)
                    }
                }
            }
            "255.255.255.255"
        } catch (_: Exception) {
            "255.255.255.255"
        }
    }

    private fun bytesToLong(bytes: ByteArray): Long {
        var value = 0L
        for (i in 0 until 4) value = (value shl 8) or (bytes[i].toLong() and 0xFF)
        return value
    }

    private fun longToIp(value: Long): String {
        val v = value and 0xFFFFFFFFL
        return "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
    }
}