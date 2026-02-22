package id.my.bananapixel.quakealert.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import io.github.inflationx.viewpump.ViewPumpContextWrapper

/**
 * Base activity that wraps the context with ViewPump for Calligraphy font injection.
 * All activities must extend this (or BaseActivityCompat) so the custom font is applied
 * to every TextView at inflation time, including fragments and RecyclerView items.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase))
    }
}
