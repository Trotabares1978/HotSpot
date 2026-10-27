package ar.com.argentas.invitados

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket

class MainActivity : Activity() {
    private val bg = Color.rgb(18, 19, 21)
    private val red = Color.rgb(226, 38, 45)
    private val green = Color.rgb(55, 205, 105)
    private val yellow = Color.rgb(255, 197, 48)
    private val internalKey = "argentas-local-control-v1"
    @Volatile private var serverAddress: String? = null
    @Volatile private var hotspotOn = false
    private lateinit var statusLight: TextView
    private lateinit var statusText: TextView
    private lateinit var button: Button
    private lateinit var connectionHint: TextView
    private val networkHandler = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            if (serverAddress != null) send("STATUS") else discoverAndSend("STATUS")
            networkHandler.postDelayed(this, 5000L)
        }
    }

    private fun rounded(color: Int, radius: Float = 22f): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            setBackgroundColor(bg)
        }
        val brand = TextView(this).apply {
            text = "ARGENTAS"
            textSize = 29f
            letterSpacing = 0.10f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val subtitle = TextView(this).apply {
            text = "PANEL DE CONTROL"
            textSize = 12f
            letterSpacing = 0.20f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(155, 163, 175))
            setPadding(0, dp(5), 0, dp(24))
        }
        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(Color.rgb(30, 32, 37), 24f)
        }
        statusLight = TextView(this).apply {
            text = "●"
            textSize = 52f
            gravity = Gravity.CENTER
            setTextColor(yellow)
            contentDescription = "Estado del hotspot desconocido"
        }
        statusText = TextView(this).apply {
            text = "Buscando tablet…"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(8), 0, dp(4))
        }
        connectionHint = TextView(this).apply {
            text = "Conectate a la misma red Wi-Fi que la tablet"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(170, 177, 187))
        }
        statusCard.addView(statusLight, LinearLayout.LayoutParams(-1, dp(76)))
        statusCard.addView(statusText, LinearLayout.LayoutParams(-1, -2))
        statusCard.addView(connectionHint, LinearLayout.LayoutParams(-1, -2))

        button = Button(this).apply {
            text = "ACTIVAR HOTSPOT"
            textSize = 17f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            minHeight = dp(68)
            isAllCaps = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setTextColor(Color.rgb(15, 22, 18))
            background = rounded(green, 18f)
            stateListAnimator = null
            setOnClickListener {
                isEnabled = false
                text = "CONECTANDO…"
                val address = serverAddress
                if (address == null) discoverAndSend(if (hotspotOn) "OFF" else "ON")
                else send(if (hotspotOn) "OFF" else "ON")
            }
        }
        val footer = TextView(this).apply {
            text = "ARGENTAS INVITADOS · v1.1.0\nAmbas aplicaciones deben estar abiertas en la misma red."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(126, 133, 145))
            setPadding(dp(12), dp(20), dp(12), 0)
        }
        root.addView(brand, LinearLayout.LayoutParams(-1, -2))
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2))
        root.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        root.addView(button, LinearLayout.LayoutParams(-1, dp(70)))
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 4102)
        } else {
            discoverAndSend("STATUS")
            networkHandler.postDelayed(heartbeat, 5000L)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4102) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                discoverAndSend("STATUS")
                networkHandler.removeCallbacks(heartbeat)
                networkHandler.postDelayed(heartbeat, 5000L)
            } else {
                runOnUiThread {
                    statusText.text = "PERMISO DE RED NECESARIO"
                    statusLight.setTextColor(yellow)
                    connectionHint.text = "Permití Dispositivos cercanos para conectar con la tablet"
                }
            }
        }
    }

    override fun onDestroy() {
        networkHandler.removeCallbacks(heartbeat)
        super.onDestroy()
    }

    private fun renderState(state: String) {
        runOnUiThread {
            button.isEnabled = true
            when (state) {
                "enabled" -> {
                    hotspotOn = true
                    statusLight.setTextColor(green)
                    statusText.text = "HOTSPOT ACTIVADO"
                    statusText.setTextColor(green)
                    button.text = "DESACTIVAR HOTSPOT"
                    button.setBackgroundColor(red)
                    button.setTextColor(Color.WHITE)
                    connectionHint.text = "La tablet confirmó que está activo"
                    statusLight.contentDescription = "Hotspot conectado"
                }
                "disabled" -> {
                    hotspotOn = false
                    statusLight.setTextColor(red)
                    statusText.text = "HOTSPOT APAGADO"
                    statusText.setTextColor(red)
                    button.text = "ACTIVAR HOTSPOT"
                    button.setBackgroundColor(green)
                    button.setTextColor(Color.BLACK)
                    connectionHint.text = "Podés activarlo con el botón de abajo"
                    statusLight.contentDescription = "Hotspot desconectado"
                }
                "unavailable" -> {
                    statusLight.setTextColor(yellow)
                    statusText.text = "ESTADO SIN CONFIRMAR"
                    connectionHint.text = "La tablet no pudo verificar el cambio"
                    statusText.setTextColor(yellow)
                    button.text = if (hotspotOn) "DESACTIVAR HOTSPOT" else "ACTIVAR HOTSPOT"
                    button.setBackgroundColor(yellow)
                    button.setTextColor(Color.BLACK)
                    statusLight.contentDescription = "Estado del hotspot no confirmado"
                }
                "settings" -> {
                    statusLight.setTextColor(yellow)
                    statusText.text = "REVISÁ LA TABLET"
                    connectionHint.text = "Se abrió Ajustes para completar la acción"
                    statusText.setTextColor(yellow)
                    button.text = "VOLVER A COMPROBAR"
                    button.setBackgroundColor(yellow)
                    button.setTextColor(Color.BLACK)
                    statusLight.contentDescription = "Requiere confirmación en la tablet"
                }
                else -> {
                    serverAddress = null
                    statusLight.setTextColor(red)
                    statusText.text = "TABLET NO ENCONTRADA"
                    connectionHint.text = "Abrí ARGENTAS · Control en la tablet y usá la misma Wi-Fi"
                    statusText.setTextColor(red)
                    button.text = if (hotspotOn) "DESACTIVAR HOTSPOT" else "ACTIVAR HOTSPOT"
                    button.setBackgroundColor(green)
                    button.setTextColor(Color.BLACK)
                    statusLight.contentDescription = "Sin conexión con la tablet"
                }
            }
        }
    }

    private fun discoverAndSend(command: String) {
        renderState("connection_lost")
        Thread {
            try {
                val address = DatagramSocket().use { udp ->
                    udp.broadcast = true
                    udp.soTimeout = 1800
                    val data = "ARGENTAS_DISCOVER".toByteArray()
                    val targets = linkedSetOf<InetAddress>()
                    targets.add(InetAddress.getByName("255.255.255.255"))
                    try {
                        val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                        val dhcp = wifi.dhcpInfo
                        if (dhcp.ipAddress != 0 && dhcp.netmask != 0) {
                            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
                            val bytes = byteArrayOf(
                                (broadcast and 0xff).toByte(),
                                (broadcast shr 8 and 0xff).toByte(),
                                (broadcast shr 16 and 0xff).toByte(),
                                (broadcast shr 24 and 0xff).toByte()
                            )
                            targets.add(InetAddress.getByAddress(bytes))
                        }
                    } catch (_: Exception) { }

                    var found: String? = null
                    // Repeat discovery because Android/HyperOS can drop the first UDP broadcast
                    // while Wi-Fi is waking up or switching between networks.
                    repeat(3) {
                        if (found != null) return@repeat
                        for (target in targets) {
                            try {
                                udp.send(DatagramPacket(data, data.size, target, 38472))
                            } catch (_: Exception) { }
                        }
                        val deadline = System.currentTimeMillis() + 1800L
                        while (found == null && System.currentTimeMillis() < deadline) {
                            try {
                                val buf = ByteArray(128)
                                val packet = DatagramPacket(buf, buf.size)
                                udp.receive(packet)
                                if (String(packet.data, packet.offset, packet.length) == "ARGENTAS_HERE") {
                                    found = packet.address.hostAddress
                                }
                            } catch (_: java.net.SocketTimeoutException) {
                                break
                            }
                        }
                    }
                    // UDP broadcast can be filtered. Enumerate real IPv4 interfaces instead
                    // of relying on WifiManager.dhcpInfo, which can be empty on recent Android.
                    if (found == null) {
                        val subnets = linkedMapOf<String, List<InetAddress>>()
                        try {
                            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                            while (interfaces.hasMoreElements()) {
                                val networkInterface = interfaces.nextElement()
                                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                                for (ifa in networkInterface.interfaceAddresses) {
                                    val local = ifa.address
                                    if (local !is java.net.Inet4Address || local.isLoopbackAddress || local.isLinkLocalAddress) continue
                                    val prefix = ifa.networkPrefixLength.toInt()
                                    if (prefix !in 16..30) continue
                                    val ipBytes = local.address
                                    val ip = ((ipBytes[0].toInt() and 255) shl 24) or
                                        ((ipBytes[1].toInt() and 255) shl 16) or
                                        ((ipBytes[2].toInt() and 255) shl 8) or
                                        (ipBytes[3].toInt() and 255)
                                    val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
                                    val network = ip and mask
                                    val broadcast = network or mask.inv()
                                    val hostCount = broadcast - network - 1
                                    // Avoid pathological scans on broad enterprise networks.
                                    if (hostCount !in 1..1022) continue
                                    val hosts = ArrayList<InetAddress>(hostCount)
                                    for (value in (network + 1) until broadcast) {
                                        if (value == ip) continue
                                        hosts.add(InetAddress.getByAddress(byteArrayOf(
                                            (value shr 24 and 255).toByte(),
                                            (value shr 16 and 255).toByte(),
                                            (value shr 8 and 255).toByte(),
                                            (value and 255).toByte()
                                        )))
                                    }
                                    subnets[local.hostAddress] = hosts
                                }
                            }
                        } catch (_: Exception) { }

                        val hosts = subnets.values.flatten().distinct()
                        if (hosts.isNotEmpty()) {
                            val pool = java.util.concurrent.Executors.newFixedThreadPool(32)
                            try {
                                val completion = java.util.concurrent.ExecutorCompletionService<String>(pool)
                                hosts.forEach { candidate ->
                                    completion.submit(java.util.concurrent.Callable<String> {
                                        Socket().use { socket ->
                                            socket.connect(java.net.InetSocketAddress(candidate, 38471), 400)
                                            candidate.hostAddress
                                        }
                                    })
                                }
                                val deadline = System.currentTimeMillis() + 7000L
                                var remaining = hosts.size
                                while (found == null && remaining > 0) {
                                    val wait = deadline - System.currentTimeMillis()
                                    if (wait <= 0L) break
                                    val future = completion.poll(wait, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
                                    remaining--
                                    try {
                                        found = future.get()
                                    } catch (_: Exception) { }
                                }
                            } finally {
                                pool.shutdownNow()
                            }
                        }
                    }
                    found ?: throw java.net.SocketTimeoutException("No se encontró la tablet en la red local")
                }
                serverAddress = address
                send(command)
            } catch (_: Exception) {
                serverAddress = null
                renderState("connection_lost")
            }
        }.start()
    }

    private fun send(command: String) {
        val address = serverAddress ?: return discoverAndSend(command)
        Thread {
            try {
                Socket(address, 38471).use { socket ->
                    socket.soTimeout = 5000
                    PrintWriter(socket.getOutputStream(), true).println("ARGENTAS1|$internalKey|$command")
                    val response = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                        .readLine().orEmpty()
                    when {
                        response == "STATUS|enabled" -> renderState("enabled")
                        response == "STATUS|disabled" -> renderState("disabled")
                        response == "STATUS|unavailable" -> renderState("unavailable")
                        response == "RESULT|enabled" -> renderState("enabled")
                        response == "RESULT|disabled" -> renderState("disabled")
                        response.startsWith("OPEN_SETTINGS|") -> renderState("settings")
                        response.startsWith("ERROR|HOTSPOT_CONTROL_UNAVAILABLE") -> renderState("unavailable")
                        response.startsWith("ERROR|") -> renderState("unavailable")
                        else -> renderState("unavailable")
                    }
                }
            } catch (_: Exception) {
                renderState("connection_lost")
            }
        }.start()
    }
}
