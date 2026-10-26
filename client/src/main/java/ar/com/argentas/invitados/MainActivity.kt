package ar.com.argentas.invitados

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
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
    private val bg = Color.rgb(16,17,19)
    private val panel = Color.rgb(31,32,35)
    private val yellow = Color.rgb(255,197,48)
    private val red = Color.rgb(226,38,45)
    private val internalKey = "argentas-local-control-v1"
    private var serverAddress: String? = null
    private var requestedOn = false
    private lateinit var status: TextView
    private lateinit var button: Button
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(26,30,26,24)
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "ARGENTAS"
            textSize = 29f
            letterSpacing = .12f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            setTextColor(yellow)
            gravity = Gravity.CENTER
        }, lp())
        root.addView(TextView(this).apply {
            text = "WI-FI PARA INVITADOS"
            textSize = 12f
            letterSpacing = .18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }, lp())
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(20,24,20,24)
            setBackgroundColor(panel)
        }
        status = TextView(this).apply {
            text = "● Buscando la tablet…"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(yellow)
            setPadding(0,0,0,22)
        }
        button = Button(this).apply {
            text = "CONECTAR"
            textSize = 17f
            setTextColor(Color.BLACK)
            setBackgroundColor(yellow)
            setOnClickListener { if (serverAddress == null) discoverAndSend("STATUS") else send(if (requestedOn) "OFF" else "ON") }
        }
        hint = TextView(this).apply {
            text = "Un solo botón. Sin configurar IP ni códigos."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8,18,8,0)
        }
        listOf(status,button,hint).forEach { card.addView(it, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=10 }) }
        root.addView(card, LinearLayout.LayoutParams(-1,-2).apply { topMargin=30 })
        root.addView(TextView(this).apply {
            text = "ARGENTAS  •  PUNTA LARA"
            textSize = 11f
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0,24,0,0)
        }, lp())
        setContentView(root)
        discoverAndSend("STATUS")
    }

    private fun lp() = LinearLayout.LayoutParams(-1,-2)

    private fun discoverAndSend(command: String) {
        runOnUiThread { status.text="● Buscando la tablet…"; status.setTextColor(yellow); button.text="CONECTAR" }
        Thread {
            try {
                DatagramSocket().use { udp ->
                    udp.broadcast = true
                    udp.soTimeout = 2500
                    val data = "ARGENTAS_DISCOVER".toByteArray()
                    udp.send(DatagramPacket(data,data.size,InetAddress.getByName("255.255.255.255"),38472))
                    val buf = ByteArray(128)
                    val packet = DatagramPacket(buf,buf.size)
                    udp.receive(packet)
                    serverAddress = packet.address.hostAddress
                }
                send(command)
            } catch (_: Exception) {
                runOnUiThread {
                    status.text="● No se encontró la tablet"
                    status.setTextColor(red)
                    hint.text="Conectá este teléfono a la red local de ARGENTAS e intentá otra vez."
                    button.text="BUSCAR TABLET"
                }
            }
        }.start()
    }

    private fun send(command: String) {
        val address=serverAddress ?: return discoverAndSend(command)
        Thread {
            try {
                Socket(address,38471).use { s ->
                    s.soTimeout=4000
                    PrintWriter(s.getOutputStream(),true).println("ARGENTAS1|$internalKey|$command")
                    val response=BufferedReader(InputStreamReader(s.getInputStream(),Charsets.UTF_8)).readLine().orEmpty()
                    runOnUiThread {
                        if (response.startsWith("ERROR")) {
                            serverAddress=null
                            status.text="● No se pudo conectar"
                            status.setTextColor(red)
                            button.text="BUSCAR TABLET"
                        } else {
                            status.text="● Tablet conectada"
                            status.setTextColor(Color.rgb(70,210,125))
                            when {
                                response=="STATUS|true" -> requestedOn=true
                                response=="STATUS|false" -> requestedOn=false
                                response=="QUEUED|ON" -> requestedOn=true
                                response=="QUEUED|OFF" -> requestedOn=false
                            }
                            button.text=if(requestedOn) "DESACTIVAR WI-FI" else "ACTIVAR WI-FI"
                            button.setBackgroundColor(if(requestedOn) red else yellow)
                            button.setTextColor(if(requestedOn) Color.WHITE else Color.BLACK)
                            hint.text=if(response.startsWith("QUEUED")) "Orden enviada. El servidor debe confirmar el estado real." else "Conexión establecida."
                        }
                    }
                }
            } catch (_: Exception) {
                serverAddress=null
                discoverAndSend(command)
            }
        }.start()
    }
}