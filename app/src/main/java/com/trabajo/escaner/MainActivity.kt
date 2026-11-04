package com.trabajo.escaner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
    private lateinit var btnAjustes: ImageButton

    private var linternaOn = false
    private var flashAuto = true
    private var camara: androidx.camera.core.Camera? = null
    private val executor = Executors.newSingleThreadExecutor()
    private var ultimoCodigo = ""
    private var ultimoTiempo = 0L

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
        btnAjustes = findViewById(R.id.btnAjustes)

        overlay.setColorHex(prefs.getString("colorMarco", "#CC1B1B1B") ?: "#CC1B1B1B")

        btnLinterna.setOnClickListener { alternarLinterna() }
        btnCopiar.setOnClickListener {
            if (ultimoCodigo.isNotEmpty()) copiar(ultimoCodigo)
        }
        btnHistorial.setOnClickListener { mostrarHistorial() }
        btnAjustes.setOnClickListener { mostrarAjustes() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) iniciarCamara()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 1 && g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) iniciarCamara()
        else Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val provider = futuro.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val opciones = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(opciones)

            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analisis.setAnalyzer(executor) { proxy ->
                val hayLuz = medirBrillo(proxy)
                procesar(proxy, scanner, hayLuz)
            }

            try {
                provider.unbindAll()
                camara = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis
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
            val paso = buffer.remaining() / 50
            var suma = 0L
            var n = 0
            var i = 0
            while (i < buffer.remaining() && n < 50) {
                suma += buffer.get(i).toInt() and 0xFF
                i += paso
                n++
            }
            (suma / (if (n == 0) 1 else n)) < 60
        } catch (e: Exception) { false }
    }

    private fun procesar(proxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner, hayLuz: Boolean) {
        runOnUiThread {
            if (flashAuto && hayLuz && !linternaOn) {
                camara?.cameraControl?.enableTorch(true)
            } else if (!flashAuto && !linternaOn) {
                camara?.cameraControl?.enableTorch(false)
            }
        }
        try {
            val media = proxy.imageInfo.rotationDegrees
            val imagen = InputImage.fromMediaImage(proxy.image!!, media)
            scanner.process(imagen)
                .addOnSuccessListener { codigos ->
                    for (cod in codigos) {
                        val texto = cod.rawValue ?: cod.displayValue ?: continue
                        val ahora = System.currentTimeMillis()
                        if (texto == ultimoCodigo && ahora - ultimoTiempo < 2000) continue
                        ultimoCodigo = texto
                        ultimoTiempo = ahora
                        runOnUiThread { manejarLectura(texto) }
                        break
                    }
                }
                .addOnCompleteListener { proxy.close() }
        } catch (e: Exception) {
            proxy.close()
        }
    }

    private fun manejarLectura(texto: String) {
        tvResultado.text = texto
        copiar(texto)
        guardarEnHistorial(texto)

        if (texto.startsWith("http://") || texto.startsWith("https://")) {
            try {
                val dominio = Uri.parse(texto).host ?: texto
                AlertDialog.Builder(this)
                    .setTitle("Abrir enlace")
                    .setMessage("¿Quieres abrir:\n$dominio ?")
                    .setPositiveButton("Abrir") { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(texto)))
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            } catch (e: Exception) { }
        }
    }

    private fun copiar(texto: String) {
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("código", texto))
        Toast.makeText(this, "Copiado ✅", Toast.LENGTH_SHORT).show()
    }

    private fun guardarEnHistorial(texto: String) {
        val lista = prefs.getStringSet("historial", mutableSetOf())?.toMutableSet()
            ?: mutableSetOf()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        lista.add("sdf.format(Date())∣{sdf.format(Date())} |sdf.format(Date())∣texto")
        prefs.edit().putStringSet("historial", lista).apply()
    }

    private fun mostrarHistorial() {
        val datos = prefs.getStringSet("historial", mutableSetOf())
            ?.sortedDescending() ?: emptyList()

        // Agrupar por mes y día
        val agrupado = StringBuilder()
        var mesActual = ""
        var diaActual = ""
        val meses = arrayOf("", "Enero","Febrero","Marzo","Abril","Mayo","Junio",
            "Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre")
        for (linea in datos) {
            val partes = linea.split(" | ", limit = 2)
            if (partes.size < 2) continue
            val fecha = partes[0]
            val texto = partes[1]
            val mes = fecha.substring(0, 7)
            val dia = fecha.substring(0, 10)
            if (mes != mesActual) {
                mesActual = mes
                diaActual = ""
                agrupado.append("\n■ meses[mes.substring(5).toInt()]{meses[mes.substring(5).toInt()]}meses[mes.substring(5).toInt()]{mes.substring(0, 4)}\n")
            }
            if (dia != diaActual) {
                diaActual = dia
                agrupado.append("\n  ▸ $dia\n")
            }
            agrupado.append("    $texto\n")
        }
        if (agrupado.isEmpty()) agrupado.append("Sin lecturas todavía")

        val vista = TextView(this)
        vista.text = agrupado.toString()
        vista.setPadding(40, 40, 40, 40)
        vista.setTextIsSelectable(true)

        AlertDialog.Builder(this)
            .setTitle("Historial")
            .setView(vista)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Borrar todo") { _, _ ->
                prefs.edit().remove("historial").apply()
                Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun mostrarAjustes() {
        val entrada = EditText(this)
        entrada.hint = "Ej: #CC1B1B1B, #80000000, #66000000"
        entrada.setText(prefs.getString("colorMarco", "#CC1B1B1B"))

        AlertDialog.Builder(this)
            .setTitle("Ajustes")
            .setMultiChoiceItems(
                arrayOf("Flash automático con poca luz"),
                booleanArrayOf(flashAuto)
            ) { _, which, isChecked ->
                flashAuto = isChecked
                prefs.edit().putBoolean("flashAuto", isChecked).apply()
            }
            .setView(entrada)
            .setPositiveButton("Guardar color") { _, _ ->
                val color = entrada.text.toString().trim()
                overlay.setColorHex(color)
                prefs.edit().putString("colorMarco", color).apply()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun alternarLinterna() {
        linternaOn = !linternaOn
        camara?.cameraControl?.enableTorch(linternaOn)
        Toast.makeText(this, if (linternaOn) "Linterna ON" else "Linterna OFF", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
