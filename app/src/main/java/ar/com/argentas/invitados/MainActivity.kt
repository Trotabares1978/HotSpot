package ar.com.argentas.invitados

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
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
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

/**
 * Initial prototype for ARGENTAS - Invitados.
 * The LAN protocol and UI are real; hotspot switching remains behind HotspotController
 * until it is validated against the exact Redmi Pad Android/HyperOS version.
 */
class MainActivity : Activity() {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    private var serverRunning = false
    private var serverMode = false
    private var hotspotEnabled = false
    private var clientConnected = false
    private var timeoutAt = 0L

    private lateinit var status: TextView
    private lateinit var stateLabel: TextView
    private lateinit var addressInput: EditText
    private lateinit var actionButton: Button
    private lateinit var roleButton: Button
    private val timeoutTick = object : Runnable {
        override fun run() {
            if (hotspotEnabled && timeoutAt > 0L) {
                val remaining = timeoutAt - System.currentTimeMillis()
                if (remaining <= 0L) {
                    setHotspot(false, null)
                } else {
                    stateLabel.text = "Hotspot solicitado: apagado automático en ${remaining / 1000}s"
                    mainHandler.postDelayed(this, 1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        updateUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 36, 28, 28)
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
            setPadding(8, 28, 8, 20)
        }
        stateLabel = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 8, 8, 24)
        }
        addressInput = EditText(this).apply {
            hint = "IP de la tablet (ej. 192.168.1.20)"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        actionButton = Button(this).apply {
            textSize = 18f
            setOnClickListener {
                if (serverMode) {
                    setHotspot(!hotspotEnabled, null)
                } else {
                    sendCommand(if (hotspotEnabled) "OFF" else "ON")
                }
            }
        }
        roleButton = Button(this).apply {
            text = "Configurar como servidor (tablet)"
            setOnClickListener {
                if (!serverMode) startServer() else stopServer()
            }
        }
        val help = TextView(this).apply {
            text = "Prototipo inicial. El control real del hotspot se habilitará tras probar qué permite Android/HyperOS en la tablet."
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 20, 8, 8)
        }
        listOf(title, status, stateLabel, addressInput, actionButton, roleButton, help).forEach {
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
                mainHandler.post { updateUi() }
                while (serverRunning) {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    status.text = "No se pudo iniciar el servidor"
                    status.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun stopServer() {
        serverRunning = false
        serverMode = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        updateUi()
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 4000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val writer = PrintWriter(s.getOutputStream(), true)
            val command = reader.readLine()?.trim().orEmpty()
            val response = when (command) {
                "STATUS" -> "STATUS|$hotspotEnabled"
                "ON" -> {
                    mainHandler.post { setHotspot(true, null) }
                    "QUEUED|ON"
                }
                "OFF" -> {
                    mainHandler.post { setHotspot(false, null) }
                    "QUEUED|OFF"
                }
                else -> "ERROR|UNKNOWN_COMMAND"
            }
            writer.println(response)
        }
    }

    private fun sendCommand(command: String) {
        val host = addressInput.text.toString().trim()
        if (host.isBlank()) {
            status.text = "Ingresá la IP local de la tablet"
            status.setTextColor(Color.YELLOW)
            return
        }
        status.text = "Conectando con la tablet…"
        executor.execute {
            try {
                Socket(host, 38471).use { socket ->
                    socket.soTimeout = 4000
                    PrintWriter(socket.getOutputStream(), true).println(command)
                    val response = BufferedReader(InputStreamReader(socket.getInputStream())).readLine().orEmpty()
                    mainHandler.post {
                        clientConnected = true
                        status.text = "Tablet conectada"
                        status.setTextColor(Color.GREEN)
                        if (response.startsWith("STATUS|")) {
                            hotspotEnabled = response.endsWith("true")
                            updateUi()
                        } else {
                            stateLabel.text = "Orden enviada. El control del hotspot debe validarse en la tablet."
                        }
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    clientConnected = false
                    status.text = "Sin conexión con la tablet"
                    status.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun setHotspot(enabled: Boolean, unused: Any?) {
        // Placeholder adapter: Android restricts third-party tethering control on many versions.
        // Do not report success until a supported implementation is verified on the target tablet.
        hotspotEnabled = enabled
        if (enabled) {
            timeoutAt = System.currentTimeMillis() + 10 * 60 * 1000L
            mainHandler.removeCallbacks(timeoutTick)
            mainHandler.post(timeoutTick)
        } else {
            timeoutAt = 0L
            mainHandler.removeCallbacks(timeoutTick)
        }
        stateLabel.text = if (enabled) {
            "Orden de encendido registrada; control real pendiente de validar"
        } else {
            "Orden de apagado registrada; control real pendiente de validar"
        }
        updateUi()
    }

    private fun updateUi() {
        if (!::status.isInitialized) return
        status.text = when {
            serverMode && serverRunning -> "Servidor activo · puerto 38471"
            clientConnected -> "Conectado a la tablet"
            else -> "Cliente listo · falta conectar"
        }
        status.setTextColor(if (serverMode && serverRunning || clientConnected) Color.GREEN else Color.RED)
        actionButton.text = if (hotspotEnabled) "DESACTIVAR" else "ACTIVAR"
        roleButton.text = if (serverMode) "Detener servidor" else "Configurar como servidor (tablet)"
        addressInput.visibility = if (serverMode) android.view.View.GONE else android.view.View.VISIBLE
        stateLabel.text = if (hotspotEnabled) "Hotspot solicitado: ENCENDIDO" else "Hotspot solicitado: APAGADO"
    }

    override fun onDestroy() {
        stopServer()
        executor.shutdownNow()
        mainHandler.removeCallbacks(timeoutTick)
        super.onDestroy()
    }
}
