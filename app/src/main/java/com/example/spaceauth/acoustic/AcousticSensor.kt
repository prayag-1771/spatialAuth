package com.example.spaceauth.acoustic

import android.annotation.SuppressLint
import android.media.*
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

class AcousticSensor {

    val sampleRate = 44100
    private var audioRecord: AudioRecord? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun startCapture(durationMillis: Long = 1000, onComplete: (ShortArray?, Throwable?) -> Unit) {
        executor.execute {
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("AudioRecord not initialized")
                }

                val totalSamples = (sampleRate * durationMillis / 1000).toInt()
                val recordingBuffer = ShortArray(totalSamples)
                val tempBuffer = ShortArray(minBufferSize)

                // The reliability of ToneGenerator can vary across devices. It may play from
                // the wrong speaker, be suppressed, or not reflect properly for an acoustic echo.
                // A more robust solution might involve playing a known chirp and using cross-correlation.
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(ToneGenerator.TONE_CDMA_PIP, 150)

                audioRecord?.startRecording()

                var samplesRead = 0
                while (samplesRead < totalSamples) {
                    val read = audioRecord?.read(tempBuffer, 0, tempBuffer.size) ?: 0
                    if (read > 0) {
                        val remaining = totalSamples - samplesRead
                        val toCopy = if (read > remaining) remaining else read
                        System.arraycopy(tempBuffer, 0, recordingBuffer, samplesRead, toCopy)
                        samplesRead += toCopy
                    } else if (read < 0) {
                        // Error case
                        throw IllegalStateException("AudioRecord read failed with error code: $read")
                    } else {
                        // read == 0, end of stream?
                        break
                    }
                }

                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null

                mainThreadHandler.post { onComplete(recordingBuffer, null) }

            } catch (e: Exception) {
                audioRecord?.release()
                audioRecord = null
                mainThreadHandler.post { onComplete(null, e) }
            }
        }
    }
}