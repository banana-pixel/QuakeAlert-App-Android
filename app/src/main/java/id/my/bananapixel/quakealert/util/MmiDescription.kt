package id.my.bananapixel.quakealert.util

import android.content.Context
import id.my.bananapixel.quakealert.R

/**
 * Maps MMI (Modified Mercalli Intensity) levels to localized descriptions.
 * Replaces the previous hardcoded mapping in ESP32 firmware.
 *
 * Intensity format from API: "VII (Sangat Kuat)", "II-III (Lemah)", "X+ (Ekstrem)", etc.
 */
object MmiDescription {

    /**
     * Returns the localized description for the given intensity string.
     * Intensity can be full format "VII (Sangat Kuat)" or just "VII".
     */
    fun getDescription(context: Context, intensity: String): String {
        val key = intensity.split(" ").firstOrNull()?.uppercase() ?: return context.getString(R.string.mmi_desc_unknown)
        val resId = when (key) {
            "I" -> R.string.mmi_desc_i
            "II-III", "II", "III" -> R.string.mmi_desc_ii_iii
            "IV" -> R.string.mmi_desc_iv
            "V" -> R.string.mmi_desc_v
            "VI" -> R.string.mmi_desc_vi
            "VII" -> R.string.mmi_desc_vii
            "VIII" -> R.string.mmi_desc_viii
            "IX" -> R.string.mmi_desc_ix
            "X", "X+" -> R.string.mmi_desc_x
            else -> R.string.mmi_desc_unknown
        }
        return context.getString(resId)
    }
}
