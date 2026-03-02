package com.example.spaceauth.acoustic

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AcousticRecorder {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun record(durationMillis: Int): ShortArray {

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

        audioRecord.startRecording()
        audioRecord.read(samples, 0, samples.size)
        audioRecord.stop()
        audioRecord.release()

        return samples
    }
}