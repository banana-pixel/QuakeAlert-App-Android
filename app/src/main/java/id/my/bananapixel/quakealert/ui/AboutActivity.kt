package id.my.bananapixel.quakealert.ui

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.ui.Colors
import id.my.bananapixel.quakealert.util.isDarkThemeOn

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbarLayout = findViewById<View>(R.id.app_bar_drawer)
        val repository = id.my.bananapixel.quakealert.db.Repository.getInstance(this)
        val dynamicColors = repository.getDynamicColorsEnabled()
        val darkMode = isDarkThemeOn(this)
        val statusBarColor = Colors.statusBarNormal(this, dynamicColors, darkMode)
        val toolbarTextColor = Colors.toolbarTextColor(this, dynamicColors, darkMode)
        toolbarLayout.setBackgroundColor(statusBarColor)

        val toolbar = toolbarLayout.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setTitleTextColor(toolbarTextColor)
        toolbar.setNavigationIconTint(toolbarTextColor)
        setSupportActionBar(toolbar)

        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            Colors.shouldUseLightStatusBar(dynamicColors, darkMode)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.about_quakealert_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
