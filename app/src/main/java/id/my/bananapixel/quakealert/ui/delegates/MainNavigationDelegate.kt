package id.my.bananapixel.quakealert.ui.delegates

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import id.my.bananapixel.quakealert.R

/**
 * Handles bottom navigation: tab switching, destination changes (nav bar background + insets),
 * and back press. Also applies bottom inset to the current fragment's RecyclerView.
 */
class MainNavigationDelegate(
    private val activity: FragmentActivity
) {
    private val navHostFragment: NavHostFragment?
        get() = activity.supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment

    fun setupBottomNavClearBackStack(
        navController: NavController,
        bottomNav: BottomNavigationView
    ) {
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != navController.currentDestination?.id) {
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.startDestinationId, true)
                    .setLaunchSingleTop(true)
                    .setRestoreState(false)
                    .build()
                navController.navigate(item.itemId, null, navOptions)
            }
            true
        }
    }

    fun addDestinationChangedListener(
        navController: NavController,
        bottomNav: BottomNavigationView,
        onApplyBottomInset: () -> Unit
    ) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.nav_warning) {
                bottomNav.setItemBackgroundResource(R.drawable.inset_nav_tile_warning)
            } else {
                bottomNav.setItemBackgroundResource(R.drawable.inset_nav_tile)
            }
            bottomNav.post { onApplyBottomInset() }
        }
    }

    /**
     * Applies bottom inset to the current fragment's root (floating UI margin + RecyclerView padding).
     */
    fun applyBottomInset(bottomNav: View) {
        val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull() ?: return
        val fragmentView = currentFragment.view ?: return

        val navSpace = bottomNav.height + bottomNav.marginTop + bottomNav.marginBottom + 20
        var totalBottomPadding = navSpace

        val floatingUi = fragmentView.findViewById<View>(R.id.bottom_floating_ui)
        floatingUi?.let { ui ->
            val params = ui.layoutParams as ViewGroup.MarginLayoutParams
            params.bottomMargin = navSpace
            ui.layoutParams = params
            ui.post {
                val uiHeightWithMargin = ui.height + (16 * activity.resources.displayMetrics.density).toInt()
                totalBottomPadding += uiHeightWithMargin
                applyToRecyclerView(fragmentView, totalBottomPadding)
            }
        } ?: run {
            applyToRecyclerView(fragmentView, totalBottomPadding)
        }
    }

    fun applyToRecyclerView(rootView: View, padding: Int) {
        val rv = rootView.findViewById<RecyclerView>(R.id.recycler_view)
        rv?.let {
            it.clipToPadding = false
            it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, padding)
        }
    }

    private val View.marginTop: Int
        get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
    private val View.marginBottom: Int
        get() = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
}
