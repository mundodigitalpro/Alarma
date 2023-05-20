package com.josejordan.alarma
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {
    private val MY_PERMISSIONS_REQUEST_CAMERA = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkCameraPermission()
    }

    fun startMotionDetection(view: View) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, MotionDetectionService::class.java)
            startService(intent)
            Snackbar.make(view, "Detección de movimiento iniciada", Snackbar.LENGTH_LONG).show()
        } else {
            Snackbar.make(view, "No tienes permisos para usar la cámara", Snackbar.LENGTH_SHORT).show()
        }
    }

    fun stopMotionDetection(view: View) {
        val intent = Intent(this, MotionDetectionService::class.java)
        stopService(intent)
        Snackbar.make(view, "Detección de movimiento detenida", Snackbar.LENGTH_LONG).show()
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                MY_PERMISSIONS_REQUEST_CAMERA)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_CAMERA -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Snackbar.make(findViewById(android.R.id.content), "Permiso de cámara otorgado", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(findViewById(android.R.id.content), "Permiso de cámara denegado", Snackbar.LENGTH_SHORT).show()
                }
                return
            }
            else -> {
                // Otra solicitud de permiso desconocida
            }
        }
    }
}

