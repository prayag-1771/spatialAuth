package com.example.spaceauth.acoustic

data class AcousticFeatureVector(
    val energy: Double = 0.0,    val zeroCrossingRate: Double = 0.0,
    val spectralCentroid: Double = 0.0
)