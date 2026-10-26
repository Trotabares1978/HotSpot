package ar.com.argentas.control

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.ServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.security.MessageDigest\nimport android.os.Handler\nimport android.os.Looper

class MainActivity : Activity() {
    private val dark=Color.rgb(18,19,21)
    private val yellow=Color.rgb(255,197,48)
    private val internalKey="argentas-local-control-v1"
    private lateinit var status: TextView
    private lateinit var start: Button
    @Volatile private var running=false
    @Volatile private var requested="unknown"
    private var server: ServerSocket?=null
    private var discovery: DatagramSocket?=null
    @Volatile private var offAt=0L\n    private val handler=Handler(Looper.getMainLooper())\n    private val timer=object: Runnable { override fun run() { if (offAt > 0L && System.currentTimeMillis() >= offAt) { requested="false"; offAt=0L }; if (running) handler.postDelayed(this,1000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER
            setPadding(24,30,24,24)
            setBackgroundColor(dark)
        }
        root.addView(TextView(this).apply {
            text="ARGENTAS"
            textSize=28f
            letterSpacing=.12f
            typeface=Typeface.DEFAULT_BOLD
            setTextColor(yellow)
            gravity=Gravity.CENTER
        },lp())
        root.addView(TextView(this).apply {
            text="PANEL DE CONTROL · TABLET"
            textSize=12f
            setTextColor(Color.LTGRAY)
            gravity=Gravity.CENTER
        },lp())
        status=TextView(this).apply {
            text="● Servidor detenido"
            textSize=16f
            setTextColor(Color.rgb(255,95,95))
            gravity=Gravity.CENTER
            setPadding(0,28,0,20)
        }
        start=Button(this).apply {
            text="INICIAR SERVIDOR"
            setTextColor(Color.BLACK)
            setBackgroundColor(yellow)
            setOnClickListener { if(running) stopServer() else startServer() }
        }
        val note=TextView(this).apply {
            text="Iniciá el servicio una vez. La app Cliente encontrará esta tablet automáticamente. El control real del hotspot aún requiere pruebas en este dispositivo."
            textSize=13f
            setTextColor(Color.LTGRAY)
            gravity=Gravity.CENTER
            setPadding(8,24,8,0)
        }
        listOf(status,start,note).forEach { root.addView(it,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=10 }) }
        setContentView(root)
    }

    private fun lp()=LinearLayout.LayoutParams(-1,-2)

    private fun startServer() {
        Thread {
            try {
                val tcp=ServerSocket(38471)
                server=tcp
                running=true
                Thread { discoverLoop() }.start()\n                handler.post(timer)
                runOnUiThread { status.text="● Servidor activo"; status.setTextColor(Color.rgb(70,210,125)); start.text="DETENER SERVIDOR" }
                while(running) {
                    val client=tcp.accept()
                    Thread { handle(client) }.start()
                }
            } catch (_:Exception) {
                if(running) runOnUiThread { status.text="No se pudo iniciar el servidor" }
            }
        }.start()
    }

    private fun discoverLoop() {
        try {
            DatagramSocket(38472).use { udp ->
                discovery=udp
                val buf=ByteArray(128)
                while(running) {
                    val packet=DatagramPacket(buf,buf.size)
                    udp.receive(packet)
                    val msg=String(packet.data,packet.offset,packet.length)
                    if(msg=="ARGENTAS_DISCOVER") {
                        val data="ARGENTAS_HERE".toByteArray()
                        udp.send(DatagramPacket(data,data.size,packet.address,packet.port))
                    }
                }
            }
        } catch (_:Exception) { }
    }

    private fun handle(client: java.net.Socket) {
        client.use { s ->
            try {
                s.soTimeout=4000
                val reader=BufferedReader(InputStreamReader(s.getInputStream(),Charsets.UTF_8))
                val writer=PrintWriter(s.getOutputStream(),true)
                val parts=reader.readLine().orEmpty().split("|",limit=3)
                if(parts.size!=3 || parts[0]!="ARGENTAS1") { writer.println("ERROR|BAD_REQUEST"); return }
                if(!MessageDigest.isEqual(parts[1].toByteArray(),internalKey.toByteArray())) { writer.println("ERROR|AUTH"); return }
                when(parts[2]) {
                    "STATUS" -> writer.println("STATUS|$requested")
                    "ON" -> { requested="true"; offAt=System.currentTimeMillis()+600000L; writer.println("QUEUED|ON") }
                    "OFF" -> { requested="false"; offAt=0L; writer.println("QUEUED|OFF") }
                    else -> writer.println("ERROR|UNKNOWN_COMMAND")
                }
            } catch (_:Exception) { }
        }
    }

    private fun stopServer() {
        running=false
        try { server?.close() } catch (_:Exception) {}
        try { discovery?.close() } catch (_:Exception) {}
        server=null
        discovery=null
        status.text="● Servidor detenido"
        status.setTextColor(Color.rgb(255,95,95))
        start.text="INICIAR SERVIDOR"
    }

    override fun onDestroy() { stopServer(); super.onDestroy() }
}