package com.josejordan.alarma

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.LinkedList
import kotlin.math.pow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*


class MotionDetectionService : Service(), LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var mediaPlayer: MediaPlayer? = null
    private var isServiceStarted = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val coroutineExecutor =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    private val motionDetectionAnalyzer = MotionDetectionAnalyzer { startAlarm() }

    companion object {
        const val CHANNEL_ID = "MotionDetectionChannel"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_DESCRIPTION = "Canal para la notificación de detección de movimiento"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = CHANNEL_ID
            val descriptionText = CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!isServiceStarted) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED

            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock =
                    powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
                wakeLock?.acquire()
            } catch (e: SecurityException) {
                Log.e("MotionDetectionService", "Error adquiriendo WakeLock", e)
            }

            val notificationIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
            )

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Detección de Movimiento Activada")
                .setContentText("La detección de movimiento está funcionando en segundo plano.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Cambia esto por el ícono de notificación que desees
                .setContentIntent(pendingIntent)
                .build()
            startForeground(NOTIFICATION_ID, notification)
            startCamera()
            isServiceStarted = true
            motionDetectionAnalyzer.lastTriggerTime = System.currentTimeMillis()
        }
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isServiceStarted = false
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindImageAnalysis(cameraProvider!!)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindImageAnalysis(cameraProvider: ProcessCameraProvider) {
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis?.setAnalyzer(coroutineExecutor, motionDetectionAnalyzer)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
    }

    private fun startAlarm() {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.alarm_sound)
        }
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
            }
        }
    }

    inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) :
        ImageAnalysis.Analyzer {
        private val THRESHOLD = 5000  // Increased threshold
        private val lastFrames = LinkedList<ByteBuffer>()
        var lastTriggerTime = 0L
        private val MIN_TIME_BETWEEN_TRIGGERS = 10000 // Increased minimum time between triggers
        private val FRAME_BUFFER_SIZE = 10  // Increased buffer size

        override fun analyze(image: ImageProxy) {
            coroutineScope.launch {
                try {
                    val currentFrame = image.planes[0].buffer
                    val newFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
                    currentFrame.rewind()
                    newFrame.put(currentFrame)
                    currentFrame.rewind()
                    newFrame.flip()
                    lastFrames.add(newFrame)

                    if (lastFrames.size > FRAME_BUFFER_SIZE) {
                        lastFrames.remove()
                    }

                    if (lastFrames.size == FRAME_BUFFER_SIZE) {
                        val firstFrame = lastFrames.peek()
                        if (hasMotion(firstFrame as ByteBuffer, currentFrame)) {
                            Log.d("MotionDetectionAnalyzer", "Movimiento detectado")
                            val currentTriggerTime = System.currentTimeMillis()
                            if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                                onMotionDetected()
                                Log.d("MotionDetectionAnalyzer", "Tomando foto")
                                takePicture(image)
                                lastTriggerTime = currentTriggerTime
                            }
                        }
                    }
                } catch (e: Exception) {  // Manejo de errores
                    Log.e("MotionDetectionAnalyzer", "Error en analyze", e)
                } finally {
                    image.close()  // Asegurarse de que la imagen siempre se cierra
                }
            }
        }


        private suspend fun takePicture(image: ImageProxy) = withContext(Dispatchers.IO) {
            try {
                val bitmap = yuv420888ToBitmap(image)
                saveBitmapToFile(bitmap)
            } catch (e: Exception) {  // Manejo de errores
                Log.e("MotionDetectionAnalyzer", "Error al tomar foto", e)
            }
        }


        private fun yuv420888ToBitmap(image: ImageProxy): Bitmap {
            val nv21 = yuv420888ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
            val pixelCount = image.cropRect.width() * image.cropRect.height()
            val pixelSizeBits = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888)
            val outputBuffer = ByteArray(pixelCount * pixelSizeBits / 8)
            imageToByteBuffer(image, outputBuffer)
            return outputBuffer
        }
       private fun imageToByteBuffer(image: ImageProxy, outputBuffer: ByteArray) {
            val imageCrop = image.cropRect
            val imagePlanes = image.planes

            val yPixelStride = imagePlanes[0].pixelStride
            val yRowStride = imagePlanes[0].rowStride
            var outputPixel = 0
            val yPlaneBuffer = imagePlanes[0].buffer

            for (row in 0 until imageCrop.height()) {
                var rowOffset = row * yRowStride
                for (pixel in 0 until imageCrop.width()) {
                    outputBuffer[outputPixel++] = yPlaneBuffer.get(rowOffset)
                    rowOffset += yPixelStride
                }
            }

            for (planeIndex in 1..2) {
                val plane = imagePlanes[planeIndex]
                val uvPlaneBuffer = plane.buffer
                val uvPixelStride = plane.pixelStride
                val uvRowStride = plane.rowStride

                for (row in 0 until imageCrop.height() / 2) {
                    var rowOffset = row * uvRowStride
                    for (pixel in 0 until imageCrop.width() / 2) {
                        outputBuffer[outputPixel++] = uvPlaneBuffer.get(rowOffset)
                        rowOffset += uvPixelStride
                    }
                }
            }
        }

        private suspend fun saveBitmapToFile(bitmap: Bitmap) = withContext(Dispatchers.IO) {
            try {
                val filename = "${UUID.randomUUID()}.jpg"
                val file = File(applicationContext.getExternalFilesDir(null), filename)
                val fos = FileOutputStream(file)

                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.close()
            } catch (e: Exception) {
                Log.e("MotionDetectionAnalyzer", "Error al guardar foto", e)
            }
        }

        private fun hasMotion(previousFrame: ByteBuffer, currentFrame: ByteBuffer): Boolean {
            previousFrame.rewind()
            currentFrame.rewind()

            var diff = 0.0
            while (previousFrame.hasRemaining() && currentFrame.hasRemaining()) {
                diff += (previousFrame.get().toInt() - currentFrame.get().toInt()).toDouble().pow(2)
            }

            val averageDiff = diff / (previousFrame.limit().toDouble())
            Log.d("MotionDetectionAnalyzer", "averageDiff: $averageDiff")
            return averageDiff > THRESHOLD
        }
    }
}
