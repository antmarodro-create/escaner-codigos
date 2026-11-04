package com.trabajo.escaner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var txtResultado: TextView
    private val executor = Executors.newSingleThreadExecutor()
    private var ultimoCodigo = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        txtResultado = findViewById(R.id.txtResultado)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara()
        } else {
            Toast.makeText(this, "Se necesita permiso de cámara", Toast.LENGTH_LONG).show()
        }
    }

    private fun iniciarCamara() {
        val futuro = ProcessCameraProvider.getInstance(this)
        futuro.addListener({
            val provider = futuro.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val camara = CameraSelector.DEFAULT_BACK_CAMERA
            val analizador = androidx.camera.core.ImageAnalysis.Builder()
                .build()
                .also { analisis ->
                    analisis.setAnalyzer(executor) { imagenProxy ->
                        val mediaImage = imagenProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage, imagenProxy.imageInfo.rotationDegrees)
                            val opciones = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(
                                    Barcode.FORMAT_QR_CODE,
                                    Barcode.FORMAT_EAN_13,
                                    Barcode.FORMAT_EAN_8,
                                    Barcode.FORMAT_CODE_128)
                                .build()
                            val scanner = BarcodeScanning.getClient(opciones)
                            scanner.process(inputImage)
                                .addOnSuccessListener { codigos ->
                                    for (codigo in codigos) {
                                        val texto = codigo.rawValue ?: continue

                                        if (texto != ultimoCodigo) {
                                            ultimoCodigo = texto
                                            runOnUiThread {
                                                txtResultado.text = "Código: $texto"
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imagenProxy.close()
                                    scanner.close()
                                }
                        } else {
                            imagenProxy.close()
                        }
                    }
                }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, camara, preview, analizador)
            } catch (e: Exception) {
                Toast.makeText(this, "Error cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
}
