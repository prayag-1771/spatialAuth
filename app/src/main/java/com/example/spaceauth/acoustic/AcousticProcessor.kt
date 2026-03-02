package com.example.spaceauth.acoustic

import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType

object AcousticProcessor {

    fun extract(samples: ShortArray, sampleRate: Int): AcousticFeatureVector {

        val frameSize = sampleRate / 10   // 100 ms
        if (frameSize <= 0) return AcousticFeatureVector(0.0, 0.0, 0.0)

        val totalFrames = samples.size / frameSize
        if (totalFrames == 0) return AcousticFeatureVector(0.0, 0.0, 0.0)

        var rmsSum = 0.0
        var zcrSum = 0.0
        var centroidSum = 0.0
        var processedFrames = 0

        for (frameIndex in 0 until totalFrames) {

            val start = frameIndex * frameSize
            val end = start + frameSize
            val frame = samples.sliceArray(start until end)

            val doubleFrame = frame.map { it.toDouble() }.toDoubleArray()

            // ---- RMS ----
            val rms = Math.sqrt(doubleFrame.map { it * it }.average())
            rmsSum += rms

            // ---- ZCR ----
            var zeroCrossings = 0
            for (i in 1 until frame.size) {
                if ((frame[i - 1] >= 0 && frame[i] < 0) ||
                    (frame[i - 1] < 0 && frame[i] >= 0)) {
                    zeroCrossings++
                }
            }
            val zcr = zeroCrossings.toDouble() / frame.size
            zcrSum += zcr

            // ---- Apply Hamming Window ----
            for (i in doubleFrame.indices) {
                val window = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / (doubleFrame.size - 1))
                doubleFrame[i] *= window
            }

            // ---- FFT ----
            val size = Integer.highestOneBit(doubleFrame.size)
            if (size == 0) continue
            val trimmed = doubleFrame.copyOf(size)

            val transformer = FastFourierTransformer(DftNormalization.STANDARD)
            val fft = transformer.transform(trimmed, TransformType.FORWARD)

            val magnitudes = fft.map { it.abs() }

            val halfSize = magnitudes.size / 2

            var weightedSum = 0.0
            var magnitudeSum = 0.0

            for (i in 0 until halfSize) {
                val frequency = i * sampleRate.toDouble() / size
                weightedSum += frequency * magnitudes[i]
                magnitudeSum += magnitudes[i]
            }

            val centroid =
                if (magnitudeSum == 0.0) 0.0 else weightedSum / magnitudeSum

            centroidSum += centroid
            processedFrames++
        }

        if (processedFrames == 0) {
            return AcousticFeatureVector(0.0, 0.0, 0.0)
        }

        val avgRms = rmsSum / processedFrames
        val avgZcr = zcrSum / processedFrames
        val avgCentroid = centroidSum / processedFrames

        return AcousticFeatureVector(
            energy = avgRms,
            zeroCrossingRate = avgZcr,
            spectralCentroid = avgCentroid
        )
    }
}
