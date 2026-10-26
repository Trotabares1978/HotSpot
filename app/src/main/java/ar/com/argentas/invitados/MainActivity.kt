package ar.com.argentas.invitados

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * ARGENTAS - Invitados: first development build.
 *
 * One APK can be used in either role:
 * - Tablet: start the local command server.
 * - Phone: enter the tablet IP and shared access PIN.
 *
 * IMPORTANT: HotspotController is intentionally not marked as successful until
 * its implementation is validated on the target Redmi Pad. Standard Android
 * apps cannot reliably toggle tethering on every Android/HyperOS version.
 */
class MainActivity : Activity() {
    private val executor = Executors.newCachedThreadPool()
    private val handler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    @Volatile private var serverRunning = false
    private var serverMode = false
    private var lastRequestedState: Boolean? = null
    private var connected = false
    private var countdownAt = 0L

    private lateinit var status: TextView
    private lateinit var state: TextView
    private lateinit var hostInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var toggleButton: Button
    private lateinit var serverButton: Button

    private val countdown = object : Runnable {
        override fun run() {
            val deadline = countdownAt
            if (deadline == 0L) return
            val left = deadline - System.currentTimeMillis()
            if (left <= 0L) {
                // Safety behavior: request OFF locally. Actual radio control depends on
                // the validated platform adapter; never pretend it succeeded.
                if (serverMode) requestHotspot(false)
                countdownAt = 0L
                state.text = "Se cumplieron los 10 minutos. Orden de apagado emitida; verificá el estado real."
            } else {
                state.text = "Apagado automático solicitado en ${left / 1000}s"
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createUi()
        render()
    }

    private fun createUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 34, 26, 24)
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        val title = TextView(this).apply {
            text = "ARGENTAS\nINVITADOS"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(8, 24, 8, 18)
        }
        state = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 8, 8, 18)
        }
        hostInput = EditText(this).apply {
            hint = "IP local de la tablet"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        pinInput = EditText(this).apply {
            hint = "PIN compartido"
            setSingleLine(true)
            inputType = 129
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        toggleButton = Button(this).apply {
            textSize = 18f
            setOnClickListener {
                if (serverMode) {
                    val next = lastRequestedState != true
                    requestHotspot(next)
                } else {
                    sendCommand(if (lastRequestedState == true) "OFF" else "ON")
                }
            }
        }
        serverButton = Button(this).apply {
            text = "Iniciar servidor en esta tablet"
            setOnClickListener {
                if (serverRunning) stopServer() else startServer()
            }
        }
        val note = TextView(this).apply {
            text = "Versión de desarrollo. El control real del hotspot se habilitará después de probar la Redmi Pad. No se debe considerar confirmado el estado del Wi-Fi solo por enviar una orden."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 20, 8, 8)
        }
        listOf(title, status, state, hostInput, pinInput, toggleButton, serverButton, note).forEach {
            root.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
    }

    private fun startServer() {
        if (serverRunning) return
        executor.execute {
            try {
                val socket = ServerSocket(38471)
                serverSocket = socket
                serverRunning = true
                serverMode = true
                handler.post { render() }
                while (serverRunning) {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                }
            } catch (_: Exception) {
                if (serverRunning) handler.post {
                    status.text = "No se pudo iniciar el servidor"
                    status.setTextColor(Color.RED)
                }
            } finally {
                serverRunning = false
            }
        }
    }

    private fun stopServer() {
        serverRunning = false
        serverMode = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        render()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 4000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val writer = PrintWriter(s.getOutputStream(), true)
            val line = reader.readLine().orEmpty()
            val parts = line.split("|", limit = 3)
            if (parts.size != 3 || parts[0] != "ARGENTAS1") {
                writer.println("ERROR|BAD_REQUEST")
                return
            }
            val suppliedPin = parts[1]
            if (!constantTimeEquals(suppliedPin, pinInput.text.toString())) {
                writer.println("ERROR|AUTH")
                return
            }
            val command = parts[2]
            when (command) {
                "STATUS" -> writer.println("STATUS|${lastRequestedState?.toString() ?: "unknown"}")
                "ON" -> {
                    handler.post { requestHotspot(true) }
                    writer.println("QUEUED|ON")
                }
                "OFF" -> {
                    handler.post { requestHotspot(false) }
                    writer.println("QUEUED|OFF")
                }
                else -> writer.println("ERROR|UNKNOWN_COMMAND")
            }
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }

    private fun sendCommand(command: String) {
        val host = hostInput.text.toString().trim()
        val pin = pinInput.text.toString()
        if (host.isBlank() || pin.length < 6) {
            status.text = "Ingresá la IP de la tablet y un PIN de al menos 6 caracteres"
            status.setTextColor(Color.YELLOW)
            return
        }
        status.text = "Conectando…"
        executor.execute {
            try {
                Socket(host, 38471).use { socket ->
                    socket.soTimeout = 4000
                    PrintWriter(socket.getOutputStream(), true).println("ARGENTAS1|$pin|$command")
                    val response = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine().orEmpty()
                    handler.post {
                        connected = !response.startsWith("ERROR")
                        status.text = when {
                            response.startsWith("ERROR|AUTH") -> "PIN incorrecto"
                            response.startsWith("ERROR") -> "Orden rechazada"
                            else -> "Conexión con la tablet: activa"
                        }
                        status.setTextColor(if (connected) Color.GREEN else Color.RED)
                        if (response.startsWith("STATUS|")) {
                            lastRequestedState = when (response.substringAfter("|")) {
                                "true" -> true
                                "false" -> false
                                else -> null
                            }
                        } else if (response.startsWith("QUEUED|")) {
                            state.text = "Orden recibida por el servidor. Falta confirmar el estado real del hotspot."
                        }
                        render()
                    }
                }
            } catch (_: Exception) {
                handler.post {
                    connected = false
                    status.text = "Sin conexión con la tablet"
                    status.setTextColor(Color.RED)
                    render()
                }
            }
        }
    }

    private fun requestHotspot(enabled: Boolean) {
        // TODO: implement the platform-specific hotspot adapter after testing the Redmi Pad.
        // Keep the actual state unknown; only the requested state is recorded.
        lastRequestedState = enabled
        if (enabled) {
            countdownAt = System.currentTimeMillis() + 10 * 60 * 1000L
            handler.removeCallbacks(countdown)
            handler.post(countdown)
            state.text = "Encendido solicitado. Control real pendiente de validar."
        } else {
            countdownAt = 0L
            handler.removeCallbacks(countdown)
            state.text = "Apagado solicitado. Control real pendiente de validar."
        }
        render()
    }

    private fun render() {
        if (!::status.isInitialized) return
        if (serverRunning && serverMode) {
            status.text = "● Servidor activo · puerto 38471"
            status.setTextColor(Color.GREEN)
        } else if (!connected) {
            status.text = "● Sin conexión con la tablet"
            status.setTextColor(Color.RED)
        }
        toggleButton.text = if (lastRequestedState == true) "DESACTIVAR" else "ACTIVAR"
        serverButton.text = if (serverRunning) "Detener servidor" else "Iniciar servidor en esta tablet"
        hostInput.visibility = if (serverMode) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        stopServer()
        executor.shutdownNow()
        handler.removeCallbacks(countdown)
        super.onDestroy()
    }
}
