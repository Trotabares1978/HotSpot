package ar.com.argentas.control

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
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
    private lateinit var light: TextView
    @Volatile private var running = false
    @Volatile private var requested = "unknown"
    private var server: ServerSocket? = null
    private var discovery: DatagramSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        light = TextView(this).apply {
            text = "●"
            textSize = 88f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 75, 75))
        }
        val root = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(18, 19, 21))
            addView(light, LinearLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        startServer()
    }

    private fun startServer() {
        if (running) return
        Thread {
            try {
                val tcp = ServerSocket(38471)
                server = tcp
                running = true
                runOnUiThread { light.setTextColor(Color.rgb(70, 210, 125)) }
                Thread { discoverLoop() }.start()
                while (running) {
                    val client = tcp.accept()
                    Thread { handle(client) }.start()
                }
            } catch (_: Exception) {
                running = false
                runOnUiThread { light.setTextColor(Color.rgb(255, 75, 75)) }
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
                s.soTimeout = 4000
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
                when (parts[2]) {
                    "STATUS" -> writer.println("STATUS|$requested")
                    "ON" -> {
                        requested = "true"
                        writer.println("QUEUED|ON")
                    }
                    "OFF" -> {
                        requested = "false"
                        writer.println("QUEUED|OFF")
                    }
                    else -> writer.println("ERROR|UNKNOWN_COMMAND")
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        try { discovery?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}