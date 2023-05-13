package com.josejordan.alarma

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.LinkedList
import java.util.Queue
import kotlin.math.pow


class MotionDetectionService : Service(), LifecycleOwner {
    private val executor = Executors.newSingleThreadExecutor()
    private val lifecycleRegistry = LifecycleRegistry(this)
    private var mediaPlayer: MediaPlayer? = null
    private var isServiceStarted = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!isServiceStarted) {
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
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
    }

    override fun getLifecycle(): Lifecycle {
        return lifecycleRegistry
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
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
        if (!mediaPlayer?.isPlaying!!) {
            mediaPlayer?.start()
        }
    }

    //septimo
    inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
        private val THRESHOLD = 5000  // Increased threshold
        private var lastFrames: Queue<ByteBuffer> = LinkedList()
        private var lastTriggerTime = 0L
        private val MIN_TIME_BETWEEN_TRIGGERS = 10000 // Increased minimum time between triggers
        private val FRAME_BUFFER_SIZE = 10  // Increased buffer size
        override fun analyze(image: ImageProxy) {
            val currentFrame = image.planes[0].buffer
            if (lastFrames.size == FRAME_BUFFER_SIZE) {
                if (hasMotion(lastFrames.peek(), currentFrame)) {
                    val currentTriggerTime = System.currentTimeMillis()
                    if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                        onMotionDetected()
                        lastTriggerTime = currentTriggerTime
                    }
                }
                lastFrames.remove()
            }
            val newFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
            newFrame.put(currentFrame)
            lastFrames.add(newFrame)
            image.close()
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

//sexto mejorandoel segundo
/*    inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
        private val THRESHOLD = 2500
        private var lastFrames: Queue<ByteBuffer> = LinkedList()
        private var lastTriggerTime = 0L
        private val MIN_TIME_BETWEEN_TRIGGERS = 5000 // Time in milliseconds.
        private val FRAME_BUFFER_SIZE = 5

        override fun analyze(image: ImageProxy) {
            val currentFrame = image.planes[0].buffer
            if (lastFrames.size == FRAME_BUFFER_SIZE) {
                if (hasMotion(lastFrames.peek(), currentFrame)) {
                    val currentTriggerTime = System.currentTimeMillis()
                    if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                        onMotionDetected()
                        lastTriggerTime = currentTriggerTime
                    }
                }
                lastFrames.remove()
            }
            val newFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
            newFrame.put(currentFrame)
            lastFrames.add(newFrame)
            image.close()
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
    }*/

//quinto menos sensible
/*inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
    private val BASE_THRESHOLD = 60 // Base threshold.
    private val MIN_TIME_BETWEEN_TRIGGERS = 3000 // Min time between triggers (in milliseconds).
    private var lastFrame: ByteBuffer? = null
    private var lastTriggerTime = 0L

    override fun analyze(image: ImageProxy) {
        val currentFrame = image.planes[0].buffer
        lastFrame?.let { previousFrame ->
            if (hasMotion(previousFrame, currentFrame, image)) {
                val currentTriggerTime = System.currentTimeMillis()
                if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                    onMotionDetected()
                    lastTriggerTime = currentTriggerTime
                }
            }
        }
        lastFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
        lastFrame?.put(currentFrame)
        image.close()
    }

    private fun hasMotion(previousFrame: ByteBuffer, currentFrame: ByteBuffer, image: ImageProxy): Boolean {
        previousFrame.rewind()
        currentFrame.rewind()

        var diff = 0
        var totalLuminance = 0
        while (previousFrame.hasRemaining() && currentFrame.hasRemaining()) {
            val previousPixel = previousFrame.get().toInt()
            val currentPixel = currentFrame.get().toInt()
            diff += Math.abs(previousPixel - currentPixel)
            totalLuminance += currentPixel
        }

        val averageLuminance = totalLuminance / (previousFrame.limit().toDouble())
        val adjustedThreshold = BASE_THRESHOLD * Math.sqrt(1 - averageLuminance / 255) // Adjust threshold based on average luminance.

        val averageDiff = diff.toDouble() / (previousFrame.limit().toDouble())
        return averageDiff > adjustedThreshold
    }
}*/


//cuarto con variacion luminica y tiempo
/*    inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
        private val THRESHOLD = 40 // Base threshold.
        private val MIN_TIME_BETWEEN_TRIGGERS = 3000 // Min time between triggers (in milliseconds).
        private var lastFrame: ByteBuffer? = null
        private var lastTriggerTime = 0L

        override fun analyze(image: ImageProxy) {
            val currentFrame = image.planes[0].buffer
            lastFrame?.let { previousFrame ->
                if (hasMotion(previousFrame, currentFrame, image)) {
                    val currentTriggerTime = System.currentTimeMillis()
                    if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                        onMotionDetected()
                        lastTriggerTime = currentTriggerTime
                    }
                }
            }
            lastFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
            lastFrame?.put(currentFrame)
            image.close()
        }

        private fun hasMotion(previousFrame: ByteBuffer, currentFrame: ByteBuffer, image: ImageProxy): Boolean {
            previousFrame.rewind()
            currentFrame.rewind()

            var diff = 0
            var totalLuminance = 0
            while (previousFrame.hasRemaining() && currentFrame.hasRemaining()) {
                val previousPixel = previousFrame.get().toInt()
                val currentPixel = currentFrame.get().toInt()
                diff += Math.abs(previousPixel - currentPixel)
                totalLuminance += currentPixel
            }

            val averageLuminance = totalLuminance / (previousFrame.limit().toDouble())
            val adjustedThreshold = THRESHOLD * (1 - averageLuminance / 255) // Adjust threshold based on average luminance.

            val averageDiff = diff.toDouble() / (previousFrame.limit().toDouble())
            return averageDiff > adjustedThreshold
        }
    }*/

// tercero con variacion luminica
 /*   inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
        private val THRESHOLD = 30 // Este es un valor que quizás necesites ajustar.
        private var lastFrame: ByteBuffer? = null

        override fun analyze(image: ImageProxy) {
            val currentFrame = image.planes[0].buffer
            lastFrame?.let { previousFrame ->
                if (hasMotion(previousFrame, currentFrame, image)) {
                    onMotionDetected()
                }
            }
            lastFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
            lastFrame?.put(currentFrame)
            image.close()
        }

        private fun hasMotion(previousFrame: ByteBuffer, currentFrame: ByteBuffer, image: ImageProxy): Boolean {
            previousFrame.rewind()
            currentFrame.rewind()

            var diff = 0
            var totalLuminance = 0
            while(previousFrame.hasRemaining() && currentFrame.hasRemaining()) {
                val previousPixel = previousFrame.get().toInt()
                val currentPixel = currentFrame.get().toInt()
                diff += Math.abs(previousPixel - currentPixel)
                totalLuminance += currentPixel
            }

            val averageLuminance = totalLuminance / (previousFrame.limit().toDouble())
            val adjustedThreshold = THRESHOLD * (averageLuminance / 255) // Ajusta el umbral en función de la luminosidad media.

            val averageDiff = diff.toDouble() / (previousFrame.limit().toDouble())
            return averageDiff > adjustedThreshold
        }
    }*/

//segundo con poca luminosidad
/*inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
         private val THRESHOLD = 50 // Aumentamos el umbral de detección.
         private var lastFrame: ByteBuffer? = null
         private var lastTriggerTime = 0L
         private val MIN_TIME_BETWEEN_TRIGGERS = 5000 // Tiempo mínimo entre alarmas (en milisegundos).

         override fun analyze(image: ImageProxy) {
             val currentFrame = image.planes[0].buffer
             lastFrame?.let { previousFrame ->
                 if (hasMotion(previousFrame, currentFrame)) {
                     val currentTriggerTime = System.currentTimeMillis()
                     if (currentTriggerTime - lastTriggerTime >= MIN_TIME_BETWEEN_TRIGGERS) {
                         onMotionDetected()
                         lastTriggerTime = currentTriggerTime
                     }
                 }
             }
             lastFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
             lastFrame?.put(currentFrame)
             image.close()
         }

         private fun hasMotion(previousFrame: ByteBuffer, currentFrame: ByteBuffer): Boolean {
             previousFrame.rewind()
             currentFrame.rewind()

             var diff = 0
             while (previousFrame.hasRemaining() && currentFrame.hasRemaining()) {
                 diff += abs(previousFrame.get().toInt() - currentFrame.get().toInt())
             }

             val averageDiff = diff.toDouble() / (previousFrame.limit().toDouble())
             return averageDiff > THRESHOLD
         }
     }*/

//primero
/*    inner class MotionDetectionAnalyzer(private val onMotionDetected: () -> Unit) : ImageAnalysis.Analyzer {
            private val THRESHOLD = 30
            private var lastFrame: ByteBuffer? = null

            override fun analyze(image: ImageProxy) {
                val currentFrame = image.planes[0].buffer
                lastFrame?.let { previousFrame ->
                    if (hasMotion(previousFrame, currentFrame)) {
                        onMotionDetected()
                    }
                }
                lastFrame = ByteBuffer.allocateDirect(currentFrame.capacity())
                lastFrame?.put(currentFrame)
                image.close()
            }

            private fun hasMotion(previousFrame: ByteBuffer, currentFrame: ByteBuffer): Boolean {
                previousFrame.rewind()
                currentFrame.rewind()

                var diff = 0
                while(previousFrame.hasRemaining() && currentFrame.hasRemaining()) {
                    diff += Math.abs(previousFrame.get().toInt() - currentFrame.get().toInt())
                }

                val averageDiff = diff.toDouble() / (previousFrame.limit().toDouble())
                return averageDiff > THRESHOLD
            }
        }*/
}
