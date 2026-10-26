package ar.com.argentas.invitados

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MainActivity : Activity() {
    private val bg = Color.rgb(16, 17, 19)
    private val panel = Color.rgb(31, 32, 35)
    private val red = Color.rgb(226, 38, 45)
    private val yellow = Color.rgb(255, 197, 48)
    private var connected = false
    private var requestedOn = false
    private lateinit var host: EditText
    private lateinit var pin: EditText
    private lateinit var connection: TextView
    private lateinit var action: Button
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("guest", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(26, 30, 26, 24)
            setBackgroundColor(bg)
        }
        val brand = TextView(this).apply {
            text = "ARGENTAS"
            textSize = 30f
            letterSpacing = .12f
            setTextColor(yellow)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "WI-FI PARA INVITADOS"
            textSize = 12f
            letterSpacing = .18f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 24, 20, 24)
            setBackgroundColor(panel)
        }
        connection = TextView(this).apply {
            text = "●  Tablet sin conectar"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(255, 95, 95))
            setPadding(0, 0, 0, 20)
        }
        host = EditText(this).apply {
            hint = "IP de la tablet"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(prefs.getString("host", ""))
            textSize = 16f
        }
        pin = EditText(this).apply {
            hint = "Código de acceso"
            setSingleLine(true)
            inputType = 129
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(prefs.getString("pin", ""))
            textSize = 16f
        }
        val connect = Button(this).apply {
            text = "CONECTAR"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(75, 76, 80))
            setOnClickListener { send("STATUS") }
        }
        action = Button(this).apply {
            text = "CONECTAR PARA EMPEZAR"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(yellow)
            setPadding(8, 16, 8, 16)
            setOnClickListener {
                if (!connected) send("STATUS") else send(if (requestedOn) "OFF" else "ON")
            }
        }
        hint = TextView(this).apply {
            text = "El acceso se apaga automáticamente después de 10 minutos."
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setPadding(8, 18, 8, 0)
        }
        root.addView(brand, lp())
        root.addView(subtitle, lp())
        root.addView(TextView(this).apply { text = ""; height = 24 })
        listOf(connection, host, pin, connect, action, hint).forEach {
            card.addView(it, LinearLayout.LayoutParams(-1, if (it == action) 64 else -2).apply { bottomMargin = 8 })
        }
        root.addView(card, lp())
        root.addView(TextView(this).apply {
            text = "ARGENTAS  •  PUNTA LARA"
            textSize = 11f
            letterSpacing = .12f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, 24, 0, 0)
        }, lp())
        setContentView(root)
    }

    private fun lp() = LinearLayout.LayoutParams(-1, -2)

    private fun send(command: String) {
        val address = host.text.toString().trim()
        val secret = pin.text.toString()
        if (address.isBlank() || secret.length < 6) {
            connection.text = "●  Ingresá IP y código (mínimo 6 caracteres)"
            connection.setTextColor(Color.rgb(255, 197, 48))
            return
        }
        getSharedPreferences("guest", MODE_PRIVATE).edit().putString("host", address).putString("pin", secret).apply()
        connection.text = "●  Conectando con la tablet…"
        connection.setTextColor(Color.rgb(255, 197, 48))
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(host.windowToken, 0)
        Thread {
            try {
                Socket(address, 38471).use { socket ->
                    socket.soTimeout = 4000
                    PrintWriter(socket.getOutputStream(), true).println("ARGENTAS1|$secret|$command")
                    val response = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8)).readLine().orEmpty()
                    runOnUiThread {
                        connected = !response.startsWith("ERROR")
                        connection.text = if (connected) "●  Tablet conectada" else if (response.contains("AUTH")) "●  Código incorrecto" else "●  No se pudo conectar"
                        connection.setTextColor(if (connected) Color.rgb(70, 210, 125) else Color.rgb(255, 95, 95))
                        when {
                            response.startsWith("STATUS|true") -> requestedOn = true
                            response.startsWith("STATUS|false") -> requestedOn = false
                            response == "QUEUED|ON" -> requestedOn = true
                            response == "QUEUED|OFF" -> requestedOn = false
                        }
                        action.text = if (!connected) "REINTENTAR CONEXIÓN" else if (requestedOn) "DESACTIVAR WI-FI" else "ACTIVAR WI-FI"
                        action.setBackgroundColor(if (requestedOn) red else yellow)
                        action.setTextColor(if (requestedOn) Color.WHITE else Color.BLACK)
                        hint.text = if (response.startsWith("QUEUED|")) "Orden enviada. La confirmación real del hotspot aún está pendiente." else "Conexión establecida. El estado real del hotspot todavía debe confirmarse."
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    connected = false
                    connection.text = "●  Tablet sin conectar"
                    connection.setTextColor(Color.rgb(255, 95, 95))
                    action.text = "REINTENTAR CONEXIÓN"
                    action.setBackgroundColor(yellow)
                    action.setTextColor(Color.BLACK)
                    hint.text = "Verificá que estés en la misma red que la tablet."
                }
            }
        }.start()
    }
}