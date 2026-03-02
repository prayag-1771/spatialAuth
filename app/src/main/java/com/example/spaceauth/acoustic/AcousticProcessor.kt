package com.example.spaceauth.acoustic

import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.sqrt

object AcousticProcessor {

    /**
     * Processes raw audio samples into 5 stable FFT-based features for ML.
     * @param samples Raw PCM 16-bit samples
     * @param sampleRate Sample rate of audio (e.g., 44100)
     * @return FloatArray of 5 values: fft1..fft5 (aggregated per frequency band)
     */
    fun extractForML(samples: ShortArray, sampleRate: Int): FloatArray {
        val frameDurationMs = 100
        val frameSize = (sampleRate * frameDurationMs / 1000.0).toInt()
        if (frameSize <= 0) return FloatArray(5) { 0f }

        val totalFrames = samples.size / frameSize
        if (totalFrames == 0) return FloatArray(5) { 0f }

        val fftFeaturesPerFrame = mutableListOf<FloatArray>()

        val transformer = FastFourierTransformer(DftNormalization.STANDARD)

        for (f in 0 until totalFrames) {
            val start = f * frameSize
            val frame = samples.sliceArray(start until (start + frameSize))
            val doubleFrame = frame.map { it.toDouble() }.toDoubleArray()

            // Apply Hamming window
            for (i in doubleFrame.indices) {
                val w = 0.54 - 0.46 * kotlin.math.cos(2.0 * Math.PI * i / (doubleFrame.size - 1))
                doubleFrame[i] *= w
            }

            // FFT
            val fftSize = Integer.highestOneBit(doubleFrame.size)  // next power of 2
            if (fftSize <= 0) continue
            val trimmed = doubleFrame.copyOf(fftSize)
            val fftResult = transformer.transform(trimmed, TransformType.FORWARD)
            val magnitudes = fftResult.map { it.abs() }.toDoubleArray()

            // Take first half (Nyquist)
            val halfSize = magnitudes.size / 2
            val halfMag = magnitudes.sliceArray(0 until halfSize)

            // Split into 5 frequency bands and average
            val bandSize = halfSize / 5
            val fftBands = FloatArray(5) { 0f }
            for (b in 0 until 5) {
                val bandStart = b * bandSize
                val bandEnd = if (b == 4) halfSize else (b + 1) * bandSize
                val sum = halfMag.slice(bandStart until bandEnd).sum()
                val avg = sum / (bandEnd - bandStart)
                fftBands[b] = avg.toFloat()
            }

            fftFeaturesPerFrame.add(fftBands)
        }

        // Aggregate across frames (median for stability)
        val fftFinal = FloatArray(5) { 0f }
        for (i in 0 until 5) {
            val bandValues = fftFeaturesPerFrame.map { it[i] }.sorted()
            val median = if (bandValues.isEmpty()) 0f
            else if (bandValues.size % 2 == 1) bandValues[bandValues.size / 2]
            else (bandValues[bandValues.size / 2] + bandValues[bandValues.size / 2 - 1]) / 2f
            fftFinal[i] = median
        }

        return fftFinal
    }
}