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
    private val yellow = Color.rgb(255, 197, 48)
    private val red = Color.rgb(226, 38, 45)
    private val green = Color.rgb(55, 205, 105)
    private val internalKey = "argentas-local-control-v1"
    @Volatile private var serverAddress: String? = null
    @Volatile private var requestedOn = false
    private lateinit var statusLight: TextView
    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 28, 28, 28)
            setBackgroundColor(bg)
        }
        statusLight = TextView(this).apply {
            text = "●"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(red)
            setPadding(0, 0, 0, 28)
            contentDescription = "Sin conexión con la tablet"
        }
        button = Button(this).apply {
            text = "ACTIVAR HOTSPOT"
            textSize = 17f
            isAllCaps = false
            setTextColor(Color.BLACK)
            setBackgroundColor(yellow)
            setOnClickListener {
                val address = serverAddress
                if (address == null) discoverAndSend("STATUS")
                else send(if (requestedOn) "OFF" else "ON")
            }
        }
        root.addView(statusLight, LinearLayout.LayoutParams(-1, -2))
        root.addView(button, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        discoverAndSend("STATUS")
    }

    private fun setDisconnected() {
        statusLight.setTextColor(red)
        statusLight.contentDescription = "Sin conexión con la tablet"
        serverAddress = null
        button.text = if (requestedOn) "DESACTIVAR HOTSPOT" else "ACTIVAR HOTSPOT"
    }

    private fun setConnected() {
        statusLight.setTextColor(green)
        statusLight.contentDescription = "Conectado a la tablet"
        button.text = if (requestedOn) "DESACTIVAR HOTSPOT" else "ACTIVAR HOTSPOT"
        button.setBackgroundColor(if (requestedOn) red else yellow)
        button.setTextColor(if (requestedOn) Color.WHITE else Color.BLACK)
    }

    private fun discoverAndSend(command: String) {
        runOnUiThread { setDisconnected() }
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
                    val response = String(packet.data, packet.offset, packet.length)
                    if (response != "ARGENTAS_HERE") throw IllegalStateException("Invalid discovery response")
                    packet.address.hostAddress
                }
                serverAddress = address
                send(command)
            } catch (_: Exception) {
                runOnUiThread { setDisconnected() }
            }
        }.start()
    }

    private fun send(command: String) {
        val address = serverAddress ?: return discoverAndSend(command)
        Thread {
            try {
                Socket(address, 38471).use { socket ->
                    socket.soTimeout = 4000
                    PrintWriter(socket.getOutputStream(), true).println("ARGENTAS1|$internalKey|$command")
                    val response = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                        .readLine().orEmpty()
                    runOnUiThread {
                        when {
                            response == "STATUS|true" -> requestedOn = true
                            response == "STATUS|false" -> requestedOn = false
                            response == "QUEUED|ON" -> requestedOn = true
                            response == "QUEUED|OFF" -> requestedOn = false
                            else -> {
                                setDisconnected()
                                return@runOnUiThread
                            }
                        }
                        setConnected()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { setDisconnected() }
            }
        }.start()
    }
}