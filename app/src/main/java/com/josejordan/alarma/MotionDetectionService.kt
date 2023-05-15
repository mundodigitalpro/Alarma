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
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.LinkedList
import kotlin.math.pow


class MotionDetectionService : Service(), LifecycleOwner {
    private val executor = Executors.newSingleThreadExecutor()
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var mediaPlayer: MediaPlayer? = null
    private var isServiceStarted = false
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var wakeLock: PowerManager.WakeLock? = null  // Variable miembro para el WakeLock

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

            // Intenta adquirir el WakeLock
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag")
                wakeLock?.acquire()
            } catch (e: SecurityException) {
                Log.e("MotionDetectionService", "Error adquiriendo WakeLock", e)
                // Aquí puedes manejar el error, por ejemplo, puedes detener el servicio si el WakeLock es esencial para tu aplicación
                //stopSelf()
                //return START_NOT_STICKY
            }

            // Crea una notificación y establece el servicio en primer plano
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
        }
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isServiceStarted = false

        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()

        // Verifica si el WakeLock está encendido antes de liberarlo
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

        val motionDetectionAnalyzer = MotionDetectionAnalyzer { startAlarm() }
        imageAnalysis?.setAnalyzer(executor, motionDetectionAnalyzer)

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

    //octavo MotionDetectionAnalyzer mejorado
    inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
        private val THRESHOLD = 5000  // Increased threshold
        private val lastFrames = LinkedList<ByteBuffer>()
        private var lastTriggerTime = 0L
        private val MIN_TIME_BETWEEN_TRIGGERS = 10000 // Increased minimum time between triggers
        private val FRAME_BUFFER_SIZE = 10  // Increased buffer size

        override fun analyze(image: ImageProxy) {
            val currentFrame = image.planes[0].buffer
            val newFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
            currentFrame.rewind()
            newFrame.put(currentFrame)
            currentFrame.rewind() // rewind the currentFrame again for the next possible usage
            newFrame.flip() // flip the newFrame buffer to make it ready for get operations
            lastFrames.add(newFrame)

            if (lastFrames.size > FRAME_BUFFER_SIZE) {
                lastFrames.remove()
            }

            image.use {
                if (lastFrames.size == FRAME_BUFFER_SIZE) {
                    val firstFrame = lastFrames.peek()
                    if (hasMotion(firstFrame as ByteBuffer, currentFrame)) {
                        val currentTriggerTime = System.currentTimeMillis()
                        if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                            onMotionDetected()
                            lastTriggerTime = currentTriggerTime
                        }
                    }
                }
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
            return averageDiff > THRESHOLD
        }
    }
}
