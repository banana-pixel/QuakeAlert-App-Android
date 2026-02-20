package id.my.bananapixel.quakealert.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import id.my.bananapixel.quakealert.R

/**
 * Adapter for the main ViewPager2 that shows the five top-level tabs
 * in the same order as the bottom navigation menu.
 */
class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> HistoryFragment()
        1 -> SensorsFragment()
        2 -> WarningFragment()
        3 -> ChatFragment()
        4 -> SettingsFragment()
        else -> throw IllegalArgumentException("Unknown position: $position")
    }

    companion object {
        const val TAB_COUNT = 5

        val TAB_TITLES = listOf(
            "History",
            "Sensors",
            "Warning",
            "Chat",
            "Settings"
        )

        /** Bottom nav menu item ids in ViewPager order (index -> id). */
        val POSITION_TO_NAV_ID = intArrayOf(
            R.id.nav_history,
            R.id.nav_sensors,
            R.id.nav_warning,
            R.id.nav_chat,
            R.id.nav_settings
        )

        fun positionToNavId(position: Int): Int = POSITION_TO_NAV_ID[position]
            .takeIf { position in 0 until TAB_COUNT }
            ?: POSITION_TO_NAV_ID[0]

        fun navIdToPosition(navId: Int): Int = POSITION_TO_NAV_ID.indexOf(navId)
            .takeIf { it >= 0 } ?: 0
    }
}
