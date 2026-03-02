package com.example.spaceauth.acoustic

data class AcousticFeatureVector(
    val energy: Double,
    val zeroCrossingRate: Double,
    val spectralCentroid: Double
)