package com.example.data.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Sound & Notification Service for XAUUSD Price Alert Thresholds.
 * Uses AudioTrack PCM tone generation for clean institutional chime/alert tones
 * with zero external asset dependencies, and system NotificationManager for alerts.
 */
object AlertNotificationService {

    private const val CHANNEL_ID = "xauusd_price_alerts"
    private const val CHANNEL_NAME = "XAUUSD Price Alerts"

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Real-time notifications when gold prices cross defined thresholds"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun showPriceAlertNotification(
        context: Context,
        alertId: Long,
        title: String,
        message: String
    ) {
        initNotificationChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        manager.notify((1000 + alertId).toInt(), builder.build())
    }

    /**
     * Plays a high-fidelity subtle institutional chime tone (1200Hz + harmonic decay)
     * using AudioTrack synthesis.
     */
    fun playSubtleAlertChime() {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 44100
                val durationMs = 320
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val samples = ShortArray(numSamples)

                val freq1 = 1244.5 // D#6 chime
                val freq2 = 1661.2 // G#6 harmonic

                for (i in 0 until numSamples) {
                    val time = i.toDouble() / sampleRate
                    // Smooth exponential decay envelope
                    val envelope = kotlin.math.exp(-7.0 * (i.toDouble() / numSamples))
                    val wave1 = sin(2.0 * Math.PI * freq1 * time)
                    val wave2 = 0.45 * sin(2.0 * Math.PI * freq2 * time)
                    val sample = ((wave1 + wave2) * envelope * 0.45 * Short.MAX_VALUE).toInt()
                    samples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }

                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferSize = maxOf(minBuf, numSamples * 2)

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(samples, 0, samples.size)
                audioTrack.play()
                kotlinx.coroutines.delay(400)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                // Audio synthesis gracefully ignored if audio device unavailable
            }
        }
    }
}
