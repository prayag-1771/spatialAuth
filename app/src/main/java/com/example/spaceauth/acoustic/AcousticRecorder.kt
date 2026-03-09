package com.example.spaceauth.acoustic

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat

class AcousticRecorder {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Records audio for the given duration (in milliseconds).
     * Returns a ShortArray of samples if permission is granted, else returns null.
     */
    fun record(context: Context, durationMillis: Int): ShortArray? {

        // Check RECORD_AUDIO permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            audioFormat
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.DEFAULT,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        val samples = ShortArray(sampleRate * durationMillis / 1000)

        try {
            audioRecord.startRecording()
            audioRecord.read(samples, 0, samples.size)
        } catch (e: SecurityException) {
            e.printStackTrace()
            return null
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        return samples
    }
}