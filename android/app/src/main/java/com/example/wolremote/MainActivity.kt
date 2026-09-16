package com.example.wolremote

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var serverInput: EditText
    private lateinit var macInput: EditText
    private lateinit var broadcastInput: EditText
    private lateinit var targetIpInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var testButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverInput = findViewById(R.id.server_input)
        macInput = findViewById(R.id.mac_input)
        broadcastInput = findViewById(R.id.broadcast_input)
        targetIpInput = findViewById(R.id.target_ip_input)
        intervalInput = findViewById(R.id.interval_input)
        toggleButton = findViewById(R.id.toggle_button)
        testButton = findViewById(R.id.test_button)
        statusText = findViewById(R.id.status_text)

        val prefs = Prefs.get(this)
        serverInput.setText(prefs.getString(Prefs.KEY_SERVER, ""))
        macInput.setText(prefs.getString(Prefs.KEY_MAC, ""))
        broadcastInput.setText(prefs.getString(Prefs.KEY_BROADCAST, ""))
        targetIpInput.setText(prefs.getString(Prefs.KEY_TARGET_IP, ""))
        intervalInput.setText(prefs.getString(Prefs.KEY_INTERVAL, "5"))

        toggleButton.setOnClickListener { onToggleClicked() }
        testButton.setOnClickListener { onTestClicked() }
        updateToggleButton()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        updateToggleButton()
        refreshStatus()
    }

    private fun onToggleClicked() {
        if (isServiceRunning()) {
            stopService(Intent(this, WakeService::class.java))
            Toast.makeText(this, "Serviciul a fost oprit", Toast.LENGTH_SHORT).show()
        } else {
            val server = serverInput.text.toString().trim()
            if (server.isEmpty()) {
                Toast.makeText(this, "Introdu adresa serverului", Toast.LENGTH_SHORT).show()
                return
            }
            if (!server.startsWith("http://") && !server.startsWith("https://")) {
                Toast.makeText(this, "Adresa serverului trebuie să înceapă cu http(s)://", Toast.LENGTH_SHORT).show()
                return
            }
            val interval = intervalInput.text.toString().trim()
            prefsEditor().apply {
                putString(Prefs.KEY_SERVER, server)
                putString(Prefs.KEY_MAC, macInput.text.toString().trim())
                putString(Prefs.KEY_BROADCAST, broadcastInput.text.toString().trim())
                putString(Prefs.KEY_TARGET_IP, targetIpInput.text.toString().trim())
                putString(Prefs.KEY_INTERVAL, interval.ifEmpty { "5" })
                apply()
            }
            startForegroundServiceCompat()
            Toast.makeText(this, "Serviciul a pornit", Toast.LENGTH_SHORT).show()
        }
        updateToggleButton()
        refreshStatus()
    }

    private fun prefsEditor() = Prefs.get(this).edit()

    private fun startForegroundServiceCompat() {
        val intent = Intent(this, WakeService::class.java)
        startForegroundService(intent)
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == WakeService::class.java.name }
    }

    private fun onTestClicked() {
        val mac = macInput.text.toString().trim()
        if (mac.isEmpty()) {
            Toast.makeText(this, "Introdu MAC-ul laptopului", Toast.LENGTH_SHORT).show()
            return
        }
        val broadcast = broadcastInput.text.toString().trim()
        statusText.text = "Se trimite test WOL..."
        Thread {
            val result = try {
                val targetText = WakeOnLan.send(mac, broadcast, applicationContext)
                "Test WOL trimis → $targetText"
            } catch (e: Exception) {
                "Test eșuat: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread {
                statusText.text = result
                Prefs.get(this).edit().putString(Prefs.KEY_LAST_EVENT, result).apply()
            }
        }.start()
    }

    private fun updateToggleButton() {
        toggleButton.text = if (isServiceRunning()) "OPREȘTE serviciul" else "PORNEȘTE serviciul"
    }

    private fun refreshStatus() {
        val lastEvent = Prefs.get(this).getString(Prefs.KEY_LAST_EVENT, null)
        statusText.text = when {
            isServiceRunning() -> "Serviciul rulează. ${
                if (lastEvent != null) "\nUltima activitate: $lastEvent" else ""
            }"
            else -> "Serviciul e oprit. Configurează setările și pornește-l."
        }
    }
}