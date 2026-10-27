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
import android.util.Log
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
    private val autoOff = Runnable {
        // Tethering callbacks and Delta verification can block; never run on the UI thread.
        Thread({
            val result = setHotspotEnabled(false)
            Log.i(TAG, "Automatic hotspot timeout result=$result")
        }, "argentas-hotspot-auto-off").start()
    }

    companion object {
        private const val TAG = "ArgentasControl"
    }
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

    override fun onResume() {
        super.onResume()
        if (!::detail.isInitialized) return
        if (Build.VERSION.SDK_INT >= 36) {
            detail.text = "Control remoto listo. El hotspot se gestiona mediante Android y Delta/Shizuku; el permiso de modificar ajustes no garantiza que HyperOS permita activarlo."
        }
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
        // Android 16 exposes a supported tethering-state callback. Prefer it over
        // WifiManager.getWifiApState(), a hidden API that OEM builds can block or
        // return inconsistently even when the hotspot operation itself succeeded.
        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val tethering = getSystemService(android.net.TetheringManager::class.java)
                    ?: throw IllegalStateException("TetheringManager unavailable")
                val latch = java.util.concurrent.CountDownLatch(1)
                var observed = "unavailable"
                val callback = object : android.net.TetheringManager.TetheringEventCallback {
                    override fun onTetheredInterfacesChanged(
                        interfaces: Set<android.net.TetheringInterface>
                    ) {
                        observed = if (interfaces.any {
                            it.type == android.net.TetheringManager.TETHERING_WIFI
                        }) "enabled" else "disabled"
                        latch.countDown()
                    }
                }
                try {
                    tethering.registerTetheringEventCallback(
                        java.util.concurrent.Executor { task -> task.run() }, callback
                    )
                    if (latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        Log.d(TAG, "Hotspot state from TetheringManager=$observed")
                        return observed
                    }
                    Log.w(TAG, "TetheringManager state callback timed out; trying legacy state")
                } finally {
                    try { tethering.unregisterTetheringEventCallback(callback) }
                    catch (e: Exception) { Log.d(TAG, "Tethering callback cleanup: ${e.javaClass.simpleName}") }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read state from TetheringManager; trying legacy state", e)
            }
        }
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
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read hotspot state", e)
            "unavailable"
        }
    }

    /**
     * Android 16 rejects direct third-party tethering control with error 14.
     * Delta can perform the operation through Shizuku when it has been configured
     * on this tablet and its Tasker-compatible broadcast receiver is enabled.
     */
    private fun setHotspotThroughDelta(enabled: Boolean): String {
        val action = if (enabled) {
            "dev.shadoe.delta.action.START_SOFT_AP"
        } else {
            "dev.shadoe.delta.action.STOP_SOFT_AP"
        }
        return try {
            packageManager.getPackageInfo("dev.shadoe.delta", 0)
            Log.i(TAG, "Sending Delta action=$action")
            val intent = Intent(action).setClassName(
                "dev.shadoe.delta",
                "dev.shadoe.delta.SoftApBroadcastReceiver"
            )
            sendBroadcast(intent)
            Log.i(TAG, "Delta broadcast sent; waiting for hotspot state")

            // Delta's broadcast API does not return a result, so verify by polling
            // the observed hotspot state instead of claiming success immediately.
            val expected = if (enabled) "enabled" else "disabled"
            val deadline = System.currentTimeMillis() + 7000L
            while (System.currentTimeMillis() < deadline) {
                if (hotspotState() == expected) {
                    if (enabled) {
                        handler.removeCallbacks(autoOff)
                        handler.postDelayed(autoOff, 10 * 60 * 1000L)
                    } else {
                        handler.removeCallbacks(autoOff)
                    }
                    return expected
                }
                Thread.sleep(350L)
            }
            Log.e(TAG, "Delta action sent but hotspot state was not confirmed")
            "delta_not_confirmed"
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            Log.e(TAG, "Delta is not installed")
            "delta_required"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invoke Delta action", e)
            "delta_not_confirmed"
        }
    }

    private fun setHotspotEnabled(enabled: Boolean): String {
        if (Build.VERSION.SDK_INT >= 36) {
            return try {
                val tethering = getSystemService(android.net.TetheringManager::class.java)
                    ?: return setHotspotThroughDelta(enabled)
                // Android 16 may reject the public tethering API even when the
                // app has WRITE_SETTINGS. Do not stop here: Delta/Shizuku is the
                // supported privileged fallback and does not depend on this grant.
                val request = android.net.TetheringManager.TetheringRequest.Builder(
                    android.net.TetheringManager.TETHERING_WIFI
                ).build()
                val latch = java.util.concurrent.CountDownLatch(1)
                var result = "unavailable"
                val executor = java.util.concurrent.Executor { task -> task.run() }
                if (enabled) {
                    tethering.startTethering(request, executor,
                        object : android.net.TetheringManager.StartTetheringCallback {
                            override fun onTetheringStarted() {
                                Log.i(TAG, "Android reported tethering started")
                                result = "enabled"
                                latch.countDown()
                            }
                            override fun onTetheringFailed(error: Int) {
                                Log.w(TAG, "Android rejected tethering start error=$error")
                                result = "unavailable:$error"
                                latch.countDown()
                            }
                        })
                } else {
                    tethering.stopTethering(request, executor,
                        object : android.net.TetheringManager.StopTetheringCallback {
                            override fun onStopTetheringSucceeded() {
                                Log.i(TAG, "Android reported tethering stopped")
                                result = "disabled"
                                latch.countDown()
                            }
                            override fun onStopTetheringFailed(error: Int) {
                                Log.w(TAG, "Android rejected tethering stop error=$error")
                                result = "unavailable:$error"
                                latch.countDown()
                            }
                        })
                }
                if (!latch.await(8, java.util.concurrent.TimeUnit.SECONDS)) {
                    // A missing callback is not evidence that the operation succeeded.
                    // Try the privileged route instead of returning "pending" and
                    // silently skipping the only fallback that can control HyperOS.
                    Log.w(TAG, "Android tethering callback timed out; trying Delta/Shizuku")
                    return setHotspotThroughDelta(enabled)
                }
                // Try Delta/Shizuku for any API-level tethering rejection, not
                // only error 14; OEM builds report equivalent restrictions differently.
                if (result.startsWith("unavailable:")) return setHotspotThroughDelta(enabled)
                if (result == "enabled") {
                    handler.removeCallbacks(autoOff)
                    handler.postDelayed(autoOff, 10 * 60 * 1000L)
                } else {
                    handler.removeCallbacks(autoOff)
                }
                result
            } catch (e: SecurityException) {
                // A synchronous permission denial must not bypass the privileged
                // Delta/Shizuku route. Android OEMs may reject before invoking
                // the asynchronous callback, so treat both paths consistently.
                Log.w(TAG, "Public tethering API threw SecurityException; trying Delta/Shizuku", e)
                setHotspotThroughDelta(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected tethering control error; trying Delta/Shizuku", e)
                setHotspotThroughDelta(enabled)
            }
        }
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
            } catch (e: Exception) {
                Log.e(TAG, "TCP server stopped unexpectedly", e)
                running = false
                handler.removeCallbacks(connectionMonitor)
                showConnection(false)
                runOnUiThread { detail.text = "Error del servidor de control: " + e.javaClass.simpleName }
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
        } catch (e: Exception) {
            if (running) Log.w(TAG, "UDP discovery stopped", e)
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
                        else if (state == "manual_required") writer.println("OPEN_SETTINGS|hotspot")
                        else if (state == "settings_required" || state == "permission_required" || state == "permission_denied") writer.println("OPEN_SETTINGS|permission")
                        else if (state == "pending") writer.println("RESULT|pending")
                        else if (state == "delta_required") writer.println("ERROR|DELTA_REQUIRED")
                        else if (state == "delta_not_confirmed") writer.println("ERROR|DELTA_NOT_CONFIRMED")
                        else if (state.startsWith("unavailable:")) writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE|$state")
                        else writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE")
                    }
                    "OFF" -> {
                        val state = setHotspotEnabled(false)
                        if (state == "disabled") writer.println("RESULT|disabled")
                        else if (state == "manual_required") writer.println("OPEN_SETTINGS|hotspot")
                        else if (state == "settings_required") writer.println("OPEN_SETTINGS|settings")
                        else if (state == "permission_required" || state == "permission_denied") writer.println("OPEN_SETTINGS|permission")
                        else if (state == "pending") writer.println("RESULT|pending")
                        else if (state == "delta_required") writer.println("ERROR|DELTA_REQUIRED")
                        else if (state == "delta_not_confirmed") writer.println("ERROR|DELTA_NOT_CONFIRMED")
                        else if (state.startsWith("unavailable:")) writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE|$state")
                        else writer.println("ERROR|HOTSPOT_CONTROL_UNAVAILABLE")
                    }
                    else -> writer.println("ERROR|UNKNOWN_COMMAND")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to handle a client command", e)
                try {
                    PrintWriter(s.getOutputStream(), true).println("ERROR|SERVER_EXCEPTION|" + e.javaClass.simpleName)
                } catch (_: Exception) { }
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
