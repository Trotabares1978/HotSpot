package ar.com.argentas.control

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.net.ServerSocket
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.security.MessageDigest

class MainActivity : Activity() {
    private val dark = Color.rgb(18, 19, 21)
    private val yellow = Color.rgb(255, 197, 48)
    private lateinit var status: TextView
    private lateinit var pin: EditText
    private lateinit var start: Button
    @Volatile private var running = false
    private var server: ServerSocket? = null
    @Volatile private var requested: String = "unknown"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 30, 24, 24)
            setBackgroundColor(dark)
        }
        val title = TextView(this).apply {
            text = "ARGENTAS"
            textSize = 28f
            letterSpacing = .12f
            setTextColor(yellow)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "PANEL DE CONTROL · TABLET"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "● Servidor detenido"
            textSize = 16f
            setTextColor(Color.rgb(255, 95, 95))
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 20)
        }
        pin = EditText(this).apply {
            hint = "Código compartido (mínimo 6 caracteres)"
            setSingleLine(true)
            inputType = 129
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(getSharedPreferences("control", MODE_PRIVATE).getString("pin", ""))
        }
        start = Button(this).apply {
            text = "INICIAR SERVIDOR"
            setTextColor(Color.BLACK)
            setBackgroundColor(yellow)
            setOnClickListener { if (running) stopServer() else startServer() }
        }
        val note = TextView(this).apply {
            text = "Recibe órdenes de Invitados. El control real del hotspot y el apagado automático requieren integración y pruebas en esta Redmi Pad."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 24, 8, 0)
        }
        listOf(title, subtitle, status, pin, start, note).forEach { root.addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }) }
        setContentView(root)
    }

    private fun startServer() {
        val secret = pin.text.toString()
        if (secret.length < 6) {
            status.text = "Ingresá un código de al menos 6 caracteres"
            return
        }
        getSharedPreferences("control", MODE_PRIVATE).edit().putString("pin", secret).apply()
        Thread {
            try {
                val socket = ServerSocket(38471)
                server = socket
                running = true
                runOnUiThread { status.text = "● Servidor activo · puerto 38471"; status.setTextColor(Color.rgb(70, 210, 125)); start.text = "DETENER SERVIDOR" }
                while (running) {
                    val client = socket.accept()
                    Thread { handle(client, secret) }.start()
                }
            } catch (_: Exception) {
                if (running) runOnUiThread { status.text = "No se pudo iniciar el servidor" }
            }
        }.start()
    }

    private fun handle(client: java.net.Socket, secret: String) {
        client.use { s ->
            try {
                s.soTimeout = 4000
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(s.getOutputStream(), true)
                val parts = reader.readLine().orEmpty().split("|", limit = 3)
                if (parts.size != 3 || parts[0] != "ARGENTAS1") { writer.println("ERROR|BAD_REQUEST"); return }
                if (!MessageDigest.isEqual(parts[1].toByteArray(), secret.toByteArray())) { writer.println("ERROR|AUTH"); return }
                when (parts[2]) {
                    "STATUS" -> writer.println("STATUS|$requested")
                    "ON" -> { requested = "true"; writer.println("QUEUED|ON") }
                    "OFF" -> { requested = "false"; writer.println("QUEUED|OFF") }
                    else -> writer.println("ERROR|UNKNOWN_COMMAND")
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopServer() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
        status.text = "● Servidor detenido"
        status.setTextColor(Color.rgb(255, 95, 95))
        start.text = "INICIAR SERVIDOR"
    }

    override fun onDestroy() { stopServer(); super.onDestroy() }
}