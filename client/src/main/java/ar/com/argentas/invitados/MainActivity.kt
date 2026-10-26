package ar.com.argentas.invitados

import android.app.Activity
import android.graphics.Color
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "ARGENTAS · INVITADOS"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 28)
        }
        statusLight = TextView(this).apply {
            text = "●"
            textSize = 64f
            gravity = Gravity.CENTER
            setTextColor(red)
            contentDescription = "Estado del hotspot desconocido"
        }
        statusText = TextView(this).apply {
            text = "Desconectado"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(red)
            setPadding(0, 4, 0, 28)
        }
        button = Button(this).apply {
            text = "ACTIVAR HOTSPOT"
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(green)
            setOnClickListener {
                isEnabled = false
                val address = serverAddress
                if (address == null) discoverAndSend(if (hotspotOn) "OFF" else "ON")
                else send(if (hotspotOn) "OFF" else "ON")
            }
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2))
        root.addView(statusLight, LinearLayout.LayoutParams(-1, 100))
        root.addView(statusText, LinearLayout.LayoutParams(-1, -2))
        root.addView(button, LinearLayout.LayoutParams(-1, -2))
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
                    statusText.text = "Conectado"
                    statusText.setTextColor(green)
                    button.text = "DESACTIVAR HOTSPOT"
                    button.setBackgroundColor(red)
                    button.setTextColor(Color.WHITE)
                    statusLight.contentDescription = "Hotspot conectado"
                }
                "disabled" -> {
                    hotspotOn = false
                    statusLight.setTextColor(red)
                    statusText.text = "Desconectado"
                    statusText.setTextColor(red)
                    button.text = "ACTIVAR HOTSPOT"
                    button.setBackgroundColor(green)
                    button.setTextColor(Color.BLACK)
                    statusLight.contentDescription = "Hotspot desconectado"
                }
                "unavailable" -> {
                    statusLight.setTextColor(yellow)
                    statusText.text = "No se pudo confirmar el estado"
                    statusText.setTextColor(yellow)
                    button.text = "REINTENTAR"
                    button.setBackgroundColor(yellow)
                    button.setTextColor(Color.BLACK)
                    statusLight.contentDescription = "Estado del hotspot no confirmado"
                }
                else -> {
                    serverAddress = null
                    statusLight.setTextColor(red)
                    statusText.text = "Desconectado de la tablet"
                    statusText.setTextColor(red)
                    button.text = "CONECTAR CON LA TABLET"
                    button.setBackgroundColor(yellow)
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
                    udp.soTimeout = 2500
                    val data = "ARGENTAS_DISCOVER".toByteArray()
                    udp.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), 38472))
                    val buf = ByteArray(128)
                    val packet = DatagramPacket(buf, buf.size)
                    udp.receive(packet)
                    if (String(packet.data, packet.offset, packet.length) != "ARGENTAS_HERE")
                        throw IllegalStateException("Respuesta de descubrimiento inválida")
                    packet.address.hostAddress
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
