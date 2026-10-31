package uz.orbitx.arport.engine.stereo

/**
 * Optical lens distortion profile for VR/AR headsets.
 * Barrel distortion formula: r' = r * (1 + k1 * r^2 + k2 * r^4)
 */
data class LensProfile(
    val id: String,
    val name: String,
    val k1: Float,
    val k2: Float
) {
    companion object {
        val CARDBOARD_V2 = LensProfile("cardboard_v2", "Google Cardboard v2", 0.34f, 0.55f)
        val CARDBOARD_V1 = LensProfile("cardboard_v1", "Google Cardboard v1", 0.441f, 0.156f)
        val NONE = LensProfile("none", "Distorsiyasiz (Tekis)", 0.0f, 0.0f)

        fun fromId(id: String): LensProfile {
            return when (id.lowercase()) {
                "cardboard_v1" -> CARDBOARD_V1
                "none" -> NONE
                else -> CARDBOARD_V2
            }
        }
    }
}

