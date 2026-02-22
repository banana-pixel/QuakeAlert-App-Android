package id.my.bananapixel.quakealert.util

import android.graphics.Typeface
import android.widget.TextView
import io.github.inflationx.viewpump.InflateResult
import io.github.inflationx.viewpump.Interceptor

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

/**
 * Applies Inter Semibold to TextViews with bold style. Regular is applied by Calligraphy.
 * Calligraphy replaces the typeface and strips bold, so we read textStyle from the original
 * XML attrs instead of view.typeface.style.
 */
class InterFontInterceptor(private val semiboldPath: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): InflateResult {
        val request = chain.request()
        val result = chain.proceed(request)
        val view = result.view
        if (view is TextView) {
            val style = request.attrs?.getAttributeIntValue(ANDROID_NS, "textStyle", Typeface.NORMAL)
                ?: Typeface.NORMAL
            val isBold = (style and Typeface.BOLD) != 0
            if (isBold) {
                try {
                    val semibold = Typeface.createFromAsset(
                        view.context.assets,
                        semiboldPath
                    )
                    view.typeface = Typeface.create(semibold, style)
                } catch (_: Exception) { /* fallback to Regular */ }
            }
        }
        return result
    }
}
