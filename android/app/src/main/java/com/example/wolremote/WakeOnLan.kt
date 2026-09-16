package com.example.wolremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
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

    /**
     * Rezolvă lista de ținte pentru magic packet: broadcast manual (dacă e dat),
     * broadcast-ul subrețelei WiFi și 255.255.255.255 (limited broadcast) ca backup.
     */
    fun resolveTargets(context: Context, manualBroadcast: String): List<String> {
        val targets = LinkedHashSet<String>()
        manualBroadcast.trim().takeIf { it.isNotEmpty() }?.let { targets.add(it) }
        detectBroadcastAddress(context).takeIf { it.isNotEmpty() }?.let { targets.add(it) }
        targets.add("255.255.255.255")
        return targets.toList()
    }

    /**
     * Trimite magic packet-ul către toate țintele, pe rețeaua WiFi, și întoarce
     * un text de diagnostic cu țintele folosite.
     */
    @Throws(IOException::class)
    fun send(mac: String, manualBroadcast: String, context: Context): String {
        val macBytes = parseMac(mac)
        val magic = buildMagicPacket(macBytes)
        val targets = resolveTargets(context, manualBroadcast)
        val wifiNetworks = findWifiNetworks(context.applicationContext)

        val socket = DatagramSocket()
        try {
            socket.broadcast = true
            if (wifiNetworks.isNotEmpty()) {
                try {
                    wifiNetworks[0].bindSocket(socket)
                } catch (_: IOException) {
                    // fallback: trimitem prin rețeaua implicită
                }
            }

            var packets = 0
            repeat(2) {
                for (target in targets) {
                    try {
                        val addr = InetAddress.getByName(target)
                        socket.send(DatagramPacket(magic, magic.size, addr, WOL_PORT))
                        packets++
                        try {
                            Thread.sleep(50)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            return@repeat
                        }
                    } catch (_: IOException) {
                        // țintă invalidă sau transport blocat — continuăm cu următoarea
                    }
                }
            }
            return "${targets.joinToString(", ")} (pachete: $packets)"
        } finally {
            socket.close()
        }
    }

    private fun findWifiNetworks(context: Context): List<Network> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.allNetworks.filter { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun detectBroadcastAddress(context: Context): String {
        return try {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val active = cm.getActiveNetwork()
            if (active != null) {
                findBroadcast(cm.getLinkProperties(active))?.let { return it }
            }

            for (network in cm.allNetworks) {
                val found = findBroadcast(cm.getLinkProperties(network))
                if (found != null) return found
            }
            ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun findBroadcast(props: LinkProperties?): String? {
        if (props == null) return null
        for (linkAddress in props.linkAddresses) {
            val address = linkAddress.address
            if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                val prefix = linkAddress.prefixLength
                if (prefix == 0 || prefix > 30) continue
                val ip = bytesToLong(address.address)
                val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
                val broadcast = (ip or (mask.inv() and 0xFFFFFFFFL)) and 0xFFFFFFFFL
                return longToIp(broadcast)
            }
        }
        return null
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