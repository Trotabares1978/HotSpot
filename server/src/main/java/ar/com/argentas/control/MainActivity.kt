package ar.com.argentas.control

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.provider.Settings
import android.widget.Button
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.security.MessageDigest

class MainActivity : Activity() {
    private val internalKey = "argentas-local-control-v1"
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun panel(color: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() }
    private lateinit var light: TextView
    private lateinit var label: TextView
    private lateinit var detail: TextView
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
            text = "CELULAR DESCONECTADO"
            textSize = 18f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 197, 48))
            setPadding(0, 8, 0, 0)
        }
        val title = TextView(this).apply {
            text = "ARGENTAS"
            textSize = 29f
            letterSpacing = 0.10f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        detail = TextView(this).apply {
            text = "La tablet está lista para recibir comandos del celular."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 177, 187))
            setPadding(12, 18, 12, 24)
        }
        val settingsButton = Button(this).apply {
            text = "ABRIR AJUSTES DEL HOTSPOT"
            textSize = 14f
            isAllCaps = false
            minHeight = dp(58)
            setTextColor(Color.WHITE)
            background = panel(Color.rgb(48, 52, 61))
            stateListAnimator = null
            setOnClickListener { openHotspotSettings() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(Color.rgb(18, 19, 21))
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(TextView(this@MainActivity).apply { text = "CONTROL DE INVITADOS"; textSize = 12f; letterSpacing = 0.20f; gravity = Gravity.CENTER; setTextColor(Color.rgb(155, 163, 175)); setPadding(0, dp(5), 0, dp(24)) }, LinearLayout.LayoutParams(-1, -2))
            val statusPanel = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(18), dp(14), dp(18), dp(18)); background = panel(Color.rgb(30, 32, 37)); addView(light, LinearLayout.LayoutParams(-1, dp(84))); addView(label, LinearLayout.LayoutParams(-1, -2)); addView(detail, LinearLayout.LayoutParams(-1, -2)) }
            addView(statusPanel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
            addView(settingsButton, LinearLayout.LayoutParams(-1, dp(60)))
        }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 4103)
        } else startServer()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4103) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startServer()
            else runOnUiThread { detail.text = "Permití Dispositivos cercanos para aceptar la conexión del celular." }
        }
    }

    private fun showConnection(connected: Boolean) {
        runOnUiThread {
            light.setTextColor(if (connected) Color.rgb(70, 210, 125) else Color.rgb(255, 75, 75))
            label.text = if (connected) "Celular conectado · control remoto" else "Celular desconectado"
            label.setTextColor(if (connected) Color.rgb(70, 210, 125) else Color.rgb(255, 95, 95))
        }
    }

    private fun openHotspotSettings() {
        try {
            val intent = Intent("android.settings.TETHER_SETTINGS")
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            } catch (_: Exception) {
                runOnUiThread {
                    detail.text = "Abrí Ajustes de Android > Punto de acceso portátil manualmente."
                }
            }
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
        // Android 16 exposes the supported tethering API. WRITE_SETTINGS is declared
        // in the manifest; the system still decides whether the user granted it.
        if (Build.VERSION.SDK_INT >= 36) {
            return try {
                val tethering = getSystemService(android.net.TetheringManager::class.java)
                    ?: return "unavailable"
                if (!Settings.System.canWrite(this)) {
                    runOnUiThread {
                        detail.text = "Falta permiso de modificación de ajustes para controlar el hotspot."
                        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        })
                    }
                    return "permission_required"
                }
                if (!enabled) {
                    tethering.stopTethering(android.net.TetheringManager.TETHERING_WIFI)
                    handler.removeCallbacks(autoOff)
                    Thread.sleep(900L)
                    return hotspotState()
                }
                val latch = java.util.concurrent.CountDownLatch(1)
                var result = "unavailable"
                val request = android.net.TetheringManager.TetheringRequest.Builder(
                    android.net.TetheringManager.TETHERING_WIFI
                ).build()
                tethering.startTethering(
                    request,
                    java.util.concurrent.Executor { command -> command.run() },
                    object : android.net.TetheringManager.StartTetheringCallback() {
                        override fun onTetheringStarted() {
                            result = "enabled"
                            latch.countDown()
                        }
                        override fun onTetheringFailed(error: Int) {
                            result = "unavailable:$error"
                            latch.countDown()
                        }
                    }
                )
                if (!latch.await(8, java.util.concurrent.TimeUnit.SECONDS)) return "pending"
                if (result == "enabled") {
                    handler.removeCallbacks(autoOff)
                    handler.postDelayed(autoOff, 10 * 60 * 1000L)
                }
                result
            } catch (e: SecurityException) {
                "permission_denied"
            } catch (_: Exception) {
                "unavailable"
            }
        }
        // Legacy fallback for older Android versions. OEMs may block this hidden API.
        return try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val method = wifi.javaClass.getDeclaredMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration::class.java, Boolean::class.javaPrimitiveType!!)
            method.isAccessible = true
            val accepted = method.invoke(wifi, null, enabled) as? Boolean ?: false
            if (!accepted) return "unavailable"
            if (enabled) handler.postDelayed(autoOff, 10 * 60 * 1000L) else handler.removeCallbacks(autoOff)
            Thread.sleep(1200L)
            hotspotState()
        } catch (_: Exception) { "unavailable" }
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
                        // Discovery only means the app is looking for the tablet.
                        // Mark the phone connected only after an authenticated TCP command.
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
                        else if (state == "settings_required" || state == "permission_required") writer.println("OPEN_SETTINGS|permission")
                        else if (state == "pending") writer.println("RESULT|pending")
                        else writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE")
                    }
                    "OFF" -> {
                        val state = setHotspotEnabled(false)
                        if (state == "disabled") writer.println("RESULT|disabled")
                        else if (state == "settings_required") writer.println("OPEN_SETTINGS|settings")
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
