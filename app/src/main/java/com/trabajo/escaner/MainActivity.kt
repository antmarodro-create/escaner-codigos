package com.trabajo.escaner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var tvResultado: TextView
    private lateinit var btnCopiar: Button
    private lateinit var btnHistorial: Button
    private lateinit var btnLinterna: ImageButton
    private var camera: androidx.camera.core.Camera? = null
    private var linternaActiva = false
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("historial", Context.MODE_PRIVATE)

        previewView = findViewById(R.id.previewView)
        tvResultado = findViewById(R.id.tvResultado)
        btnCopiar = findViewById(R.id.btnCopiar)
        btnHistorial = findViewById(R.id.btnHistorial)
        btnLinterna = findViewById(R.id.btnLinterna)

        btnCopiar.setOnClickListener { copiar() }
        btnHistorial.setOnClickListener { mostrarHistorial() }
        btnLinterna.setOnClickListener { alternarLinterna() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            tvResultado.text = "Sin permiso de cámara no puedo escanear 😢"
        }
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val provider = futuro.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_ALL_FORMATS
                )
                .build()
            val escaner = BarcodeScanning.getClient(options)

            val analisis = androidx.camera.core.ImageAnalysis.Builder()
                .setBackpressureStrategy(
                    androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                )
                .build()
            analisis.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media != null) {
                    val imagen = InputImage.fromMediaImage(
                        media,
                        proxy.imageInfo.rotationDegrees
                    )
                    escaner.process(imagen)
                        .addOnSuccessListener { codigos ->
                            for (codigo in codigos) {
                                val texto = codigo.rawValue ?: continue
                                procesar(texto)
                                break
                            }
                        }
                }
                proxy.close()
            }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                this, cameraSelector, preview, analisis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private var ultimoTexto = ""
    private var ultimoMomento = 0L

    private fun procesar(texto: String) {
        val ahora = System.currentTimeMillis()
        if (texto == ultimoTexto && ahora - ultimoMomento < 4000) return
        ultimoTexto = texto
        ultimoMomento = ahora

        runOnUiThread {
            tvResultado.text = texto
            guardar(texto)

            if (texto.startsWith("http://") || texto.startsWith("https://")) {
                preguntarAbrirUrl(texto)
            } else {
                Toast.makeText(this, "Código detectado ✅", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun preguntarAbrirUrl(url: String) {
        val dominio = try {
            Uri.parse(url).host ?: url
        } catch (e: Exception) { url }

        AlertDialog.Builder(this)
            .setTitle("Abrir enlace")
            .setMessage("El código contiene una URL.\n\nDominio: $dominio\n\n¿Abrir en el navegador?")
            .setPositiveButton("Abrir") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "No hay navegador disponible", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun guardar(texto: String) {
        val existente = prefs.getString("lista", "") ?: ""
        val lineas = existente.split("\n").filter { it.isNotBlank() }.toMutableList()
        lineas.remove(texto)
        lineas.add(0, texto)
        prefs.edit().putString("lista", lineas.take(50).joinToString("\n")).apply()
    }

    private fun mostrarHistorial() {
        val lineas = (prefs.getString("lista", "") ?: "")
            .split("\n").filter { it.isNotBlank() }
        if (lineas.isEmpty()) {
            Toast.makeText(this, "Historial vacío", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Historial (${lineas.size})")
            .setItems(lineas.toTypedArray()) { _, which ->
                val elegido = lineas[which]
                tvResultado.text = elegido
                if (elegido.startsWith("http")) preguntarAbrirUrl(elegido)
            }
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun copiar() {
        val texto = tvResultado.text.toString()
        if (texto.isBlank() || texto.contains("Apunta la cámara")) {
            Toast.makeText(this, "Nada que copiar", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("codigo", texto))
        Toast.makeText(this, "Copiado 📋", Toast.LENGTH_SHORT).show()
    }

    private fun alternarLinterna() {
        val cam = camera ?: return
        linternaActiva = !linternaActiva
        cam.cameraControl.enableTorch(linternaActiva)
        Toast.makeText(
            this,
            if (linternaActiva) "Linterna ON 🔦" else "Linterna OFF",
            Toast.LENGTH_SHORT
        ).show()
    }
}
