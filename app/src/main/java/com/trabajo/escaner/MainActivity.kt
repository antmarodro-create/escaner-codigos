package com.trabajo.escaner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.core.content.ContextCompat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: OverlayView
    private lateinit var tvResultado: TextView
    private lateinit var btnLinterna: ImageButton
    private lateinit var btnCopiar: Button
    private lateinit var btnHistorial: Button
    private lateinit var btnReanudar: Button
    private lateinit var btnAjustes: ImageButton

    private var linternaOn = false
    private var flashAuto = true
    private var escaneoActivo = true
    private var camara: Camera? = null
    private var analisis: ImageAnalysis? = null
    private var ultimoCodigo = ""
    private val executor = Executors.newSingleThreadExecutor()

    private val prefs by lazy { getSharedPreferences("escaner", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.overlay)
        tvResultado = findViewById(R.id.tvResultado)
        btnLinterna = findViewById(R.id.btnLinterna)
        btnCopiar = findViewById(R.id.btnCopiar)
        btnHistorial = findViewById(R.id.btnHistorial)
        btnReanudar = findViewById(R.id.btnReanudar)
        btnAjustes = findViewById(R.id.btnAjustes)

        overlay.setColorHex(prefs.getString("colorMarco", "#CC1B1B1B") ?: "#CC1B1B1B")
        flashAuto = prefs.getBoolean("flashAuto", true)

        btnLinterna.setOnClickListener { alternarLinterna() }
        btnCopiar.setOnClickListener { copiar(ultimoCodigo) }
        btnHistorial.setOnClickListener { mostrarHistorial() }
        btnReanudar.setOnClickListener { reanudarEscaneo() }
        btnAjustes.setOnClickListener { mostrarAjustes() }

        btnReanudar.visibility = android.view.View.GONE

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) iniciarCamara()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onRequestPermissionsResult(
        rc: Int, p: Array<out String>, g: IntArray
    ) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 1 && g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
        }
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            try {
                val provider = futuro.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val opciones = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                val scanner = BarcodeScanning.getClient(opciones)

                analisis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analisis!!.setAnalyzer(executor) { proxy ->
                    if (escaneoActivo) {
                        val hayLuz = medirBrillo(proxy)
                        procesar(proxy, scanner, hayLuz)
                    } else {
                        proxy.close()
                    }
                }

                provider.unbindAll()
                camara = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis!!
                )
                camara?.cameraControl?.enableTorch(false)
            } catch (e: Exception) {
                runOnUiThread { tvResultado.text = "Error cámara: ${e.message}" }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun medirBrillo(proxy: ImageProxy): Boolean {
        return try {
            val buffer = proxy.planes[0].buffer
            val total = buffer.remaining()
            val paso = if (total > 50) total / 50 else 1
            var suma = 0L
            var n = 0
            var i = 0
            while (i < total && n < 50) {
                suma += (buffer.get(i).toInt() and 0xFF)
                i += paso
                n++
            }
            (suma / (if (n == 0) 1 else n)) < 60L
        } catch (e: Exception) {
            false
        }
    }

    private fun procesar(proxy: ImageProxy, scanner: BarcodeScanner, hayLuz: Boolean) {
        runOnUiThread {
            if (flashAuto && !linternaOn) {
                camara?.cameraControl?.enableTorch(hayLuz)
            }
        }
        try {
            val rot = proxy.imageInfo.rotationDegrees
            val imagen = InputImage.fromMediaImage(proxy.image!!, rot)
            scanner.process(imagen)
                .addOnSuccessListener { codigos ->
                    if (codigos.isNotEmpty() && escaneoActivo) {
                        val b = codigos[0]
                        val texto = (b.rawValue ?: b.displayValue ?: "").trim()
                        if (texto.isNotEmpty()) {
                            escaneoActivo = false
                            runOnUiThread { manejarLectura(texto) }
                        }
                    }
                }
                .addOnCompleteListener { proxy.close() }
        } catch (e: Exception) {
            proxy.close()
        }
    }
        private fun manejarLectura(texto: String) {
        val limpio = texto.trim()
        ultimoCodigo = limpio

        val esUrl = limpio.startsWith("http://", ignoreCase = true) ||
                limpio.startsWith("https://", ignoreCase = true) ||
                limpio.startsWith("www.", ignoreCase = true)

        tvResultado.text = if (esUrl) "🔗 limpio"else"✅limpio" else "✅limpio"else"✅limpio"


        copiar(limpio)
        guardarEnHistorial(limpio)
        btnReanudar.visibility = android.view.View.VISIBLE

        if (esUrl) {
            val url = if (limpio.startsWith("http://", true) ||
                limpio.startsWith("https://", true)
            ) limpio else "https://$limpio"

            val dominio = try {
                Uri.parse(url).host ?: url
            } catch (e: Exception) { url }

            AlertDialog.Builder(this)
                .setTitle("Abrir enlace")
                .setMessage("¿Quieres abrir:\n$dominio ?")
                .setPositiveButton("Abrir") { _, _ ->
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun reanudarEscaneo() {
        escaneoActivo = true
        ultimoCodigo = ""
        tvResultado.text = "Apunta al código dentro del marco"
        btnReanudar.visibility = android.view.View.GONE
    }

    private fun copiar(texto: String) {
        if (texto.isEmpty()) return
        try {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cb.setPrimaryClip(ClipData.newPlainText("código", texto))
            Toast.makeText(this, "Copiado ✅", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { }
    }

    // Historial como una sola lista de líneas con fecha
    private fun guardarEnHistorial(texto: String) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val linea = "sdf.format(Date())∣{sdf.format(Date())} |sdf.format(Date())∣texto"
            val anterior = prefs.getString("historialTexto", "") ?: ""
            val nuevo = if (anterior.isEmpty()) linea else "linea\nlinea\nlinea\nanterior"
            val ok = prefs.edit().putString("historialTexto", nuevo).commit()
            if (!ok) {
                Toast.makeText(this, "Aviso: no se guardó en historial", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error guardando historial", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarHistorial() {
        val contenido = prefs.getString("historialTexto", "") ?: ""
        if (contenido.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Historial")
                .setMessage("Sin lecturas todavía")
                .setPositiveButton("Cerrar", null)
                .show()
            return
        }

        val meses = arrayOf(
            "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        val sb = StringBuilder()
        var mesActual = ""
        var diaActual = ""
        for (linea in contenido.split("\n")) {
            val partes = linea.split(" | ", limit = 2)
            if (partes.size < 2) continue
            val fecha = partes[0]
            val texto = partes[1]
            if (fecha.length < 16) continue
            val mes = fecha.substring(0, 7)
            val dia = fecha.substring(0, 10)
            if (mes != mesActual) {
                mesActual = mes
                diaActual = ""
                val numMes = mes.substring(5).toIntOrNull() ?: 1
                sb.append("\n■ meses[numMes]{meses[numMes]}meses[numMes]{mes.substring(0, 4)}\n")
            }
            if (dia != diaActual) {
                diaActual = dia
                sb.append("\n  ▸ $dia\n")
            }
            sb.append("    fecha.substring(11){fecha.substring(11)}fecha.substring(11)texto\n")
        }

        val vista = TextView(this)
        vista.text = sb.toString()
        vista.setPadding(40, 40, 40, 40)
        vista.setTextIsSelectable(true)
        vista.movementMethod = ScrollingMovementMethod()

        AlertDialog.Builder(this)
            .setTitle("Historial")
            .setView(vista)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Borrar todo") { _, _ ->
                prefs.edit().remove("historialTexto").commit()
                Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun mostrarAjustes() {
        val entrada = EditText(this)
        entrada.hint = "Ej: #CC1B1B1B, #80000000, #66000000"
        entrada.setText(prefs.getString("colorMarco", "#CC1B1B1B"))

        val chk = booleanArrayOf(flashAuto)
        AlertDialog.Builder(this)
            .setTitle("Ajustes")
            .setMultiChoiceItems(
                arrayOf("Flash automático con poca luz"),
                chk
            ) { _, _, isChecked -> chk[0] = isChecked }
            .setView(entrada)
            .setPositiveButton("Guardar") { _, _ ->
                flashAuto = chk[0]
                prefs.edit().putBoolean("flashAuto", flashAuto).apply()
                val color = entrada.text.toString().trim()
                if (color.isNotEmpty()) {
                    overlay.setColorHex(color)
                    prefs.edit().putString("colorMarco", color).apply()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun alternarLinterna() {
        linternaOn = !linternaOn
        camara?.cameraControl?.enableTorch(linternaOn)
        Toast.makeText(
            this,
            if (linternaOn) "Linterna ON" else "Linterna OFF",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
