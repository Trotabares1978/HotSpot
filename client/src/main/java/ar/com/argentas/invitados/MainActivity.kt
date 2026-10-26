package ar.com.argentas.invitados

import android.app.Activity
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
        discoverAndSend("STATUS")
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
                    udp.soTimeout = 900
                    val data = "ARGENTAS_DISCOVER".toByteArray()
                    val targets = linkedSetOf<InetAddress>()
                    targets.add(InetAddress.getByName("255.255.255.255"))
                    // Some Android Wi-Fi networks drop the limited broadcast; also try
                    // the subnet broadcast calculated from the active Wi-Fi DHCP lease.
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
                    for (target in targets) {
                        try {
                            udp.send(DatagramPacket(data, data.size, target, 38472))
                            val buf = ByteArray(128)
                            val packet = DatagramPacket(buf, buf.size)
                            udp.receive(packet)
                            if (String(packet.data, packet.offset, packet.length) == "ARGENTAS_HERE") {
                                found = packet.address.hostAddress
                                break
                            }
                        } catch (_: java.net.SocketTimeoutException) { }
                    }
                    found ?: throw java.net.SocketTimeoutException("No se encontró la tablet en la red local")
                }
                serverAddress = address
                send(command)
            } catch (_: Exception) {
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
