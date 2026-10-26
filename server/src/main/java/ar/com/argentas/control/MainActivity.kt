package ar.com.argentas.control

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.lang.reflect.InvocationTargetException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.security.MessageDigest

class MainActivity : Activity() {
    private val internalKey = "argentas-local-control-v1"
    private lateinit var light: TextView
    private lateinit var label: TextView
    @Volatile private var running = false
    @Volatile private var lastClientContact = 0L
    private var server: ServerSocket? = null
    private var discovery: DatagramSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoOff = Runnable { setHotspotEnabled(false) }
    private val connectionMonitor = object : Runnable {
        override fun run() {
            val connected = running && lastClientContact > 0L &&
                System.currentTimeMillis() - lastClientContact < 15000L
            showConnection(connected)
            if (running) handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        light = TextView(this).apply {
            text = "●"
            textSize = 88f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 75, 75))
        }
        label = TextView(this).apply {
            text = "Servidor listo"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 197, 48))
            setPadding(0, 8, 0, 0)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.rgb(18, 19, 21))
            addView(light, LinearLayout.LayoutParams(-1, 180))
            addView(label, LinearLayout.LayoutParams(-1, -2))
        }
        setContentView(root)
        startServer()
    }

    private fun showConnection(connected: Boolean) {
        runOnUiThread {
            light.setTextColor(if (connected) Color.rgb(70, 210, 125) else Color.rgb(255, 75, 75))
            label.text = if (connected) "Celular conectado" else "Esperando al celular"
            label.setTextColor(if (connected) Color.rgb(70, 210, 125) else Color.rgb(255, 95, 95))
        }
    }

    private fun hotspotState(): String {
        return try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val method = wifi.javaClass.getDeclaredMethod("getWifiApState")
            method.isAccessible = true
            when ((method.invoke(wifi) as Int)) {
                13 -> "enabled"
                10 -> "disabled"
                11, 12 -> "unavailable"
                else -> "unavailable"
            }
        } catch (_: Exception) {
            "unavailable"
        }
    }

    private fun setHotspotEnabled(enabled: Boolean): String {
        return try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val method = wifi.javaClass.getDeclaredMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration::class.java, Boolean::class.javaPrimitiveType!!)
            method.isAccessible = true
            val accepted = method.invoke(wifi, null, enabled) as? Boolean ?: false
            if (!accepted) return "unavailable"
            if (enabled) handler.postDelayed(autoOff, 10 * 60 * 1000L) else handler.removeCallbacks(autoOff)
            Thread.sleep(1200L)
            hotspotState()
        } catch (_: Exception) {
            "unavailable"
        }
    }

    private fun startServer() {
        if (running) return
        showConnection(false)
        Thread {
            try {
                val tcp = ServerSocket(38471)
                server = tcp
                running = true
                lastClientContact = 0L
                handler.post(connectionMonitor)
                Thread { discoverLoop() }.start()
                while (running) {
                    val client = tcp.accept()
                    Thread { handle(client) }.start()
                }
            } catch (_: Exception) {
                running = false
                handler.removeCallbacks(connectionMonitor)
                showConnection(false)
            }
        }.start()
    }

    private fun discoverLoop() {
        try {
            DatagramSocket(38472).use { udp ->
                discovery = udp
                val buf = ByteArray(128)
                while (running) {
                    val packet = DatagramPacket(buf, buf.size)
                    udp.receive(packet)
                    val msg = String(packet.data, packet.offset, packet.length)
                    if (msg == "ARGENTAS_DISCOVER") {
                        lastClientContact = System.currentTimeMillis()
                        val data = "ARGENTAS_HERE".toByteArray()
                        udp.send(DatagramPacket(data, data.size, packet.address, packet.port))
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            try {
                s.soTimeout = 6000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(s.getOutputStream(), true)
                val parts = reader.readLine().orEmpty().split("|", limit = 3)
                if (parts.size != 3 || parts[0] != "ARGENTAS1") {
                    writer.println("ERROR|BAD_REQUEST")
                    return
                }
                if (!MessageDigest.isEqual(parts[1].toByteArray(), internalKey.toByteArray())) {
                    writer.println("ERROR|AUTH")
                    return
                }
                lastClientContact = System.currentTimeMillis()
                when (parts[2]) {
                    "STATUS" -> writer.println("STATUS|${hotspotState()}")
                    "ON" -> {
                        val state = setHotspotEnabled(true)
                        if (state == "enabled") writer.println("RESULT|enabled")
                        else writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE")
                    }
                    "OFF" -> {
                        val state = setHotspotEnabled(false)
                        if (state == "disabled") writer.println("RESULT|disabled")
                        else writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE")
                    }
                    else -> writer.println("ERROR|UNKNOWN_COMMAND")
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(connectionMonitor)
        handler.removeCallbacks(autoOff)
        try { server?.close() } catch (_: Exception) {}
        try { discovery?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
