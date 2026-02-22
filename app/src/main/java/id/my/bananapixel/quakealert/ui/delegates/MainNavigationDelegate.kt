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
 * and bottom inset for the current fragment's RecyclerView.
 *
 * ## Why not [NavigationUI.setupWithNavController]?
 *
 * `setupWithNavController` uses default [NavOptions] and adds each tab to the back stack,
 * which leads to [TransactionTooLargeException] when the back stack grows too large.
 *
 * This delegate uses custom [NavOptions] with [setPopUpTo] + [setLaunchSingleTop] to clear
 * the back stack on tab switch, and [trimBackStackIfNeeded] caps the stack at [MAX_NAV_BACK_STACK].
 *
 * See: https://developer.android.com/guide/navigation/backstack
 * and https://developer.android.com/guide/navigation/navigation-ui#bottom_navigation
 */
class MainNavigationDelegate(
    private val activity: FragmentActivity
) {
    private var navController: NavController? = null
    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null

    private val navHostFragment: NavHostFragment?
        get() = activity.supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment

    /**
     * Connects bottom nav to NavController with back-stack-clearing behavior.
     * Uses [setPopUpTo] to avoid TransactionTooLargeException (see class KDoc).
     */
    fun setupBottomNavClearBackStack(
        navController: NavController,
        bottomNav: BottomNavigationView
    ) {
        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != navController.currentDestination?.id) {
                navController.navigate(item.itemId, null, buildTabSwitchNavOptions(navController))
            }
            true
        }
    }

    /** NavOptions that clear back stack on tab switch; prevents TransactionTooLargeException.
     * Uses inclusive=false so nav_history stays in the stack as popUpTo target. With inclusive=true,
     * the start destination gets popped on first tab switch, so subsequent switches to History find
     * nothing to pop and the stack grows indefinitely during rapid navigation. */
    private fun buildTabSwitchNavOptions(navController: NavController): NavOptions =
        NavOptions.Builder()
            .setPopUpTo(navController.graph.startDestinationId, false)
            .setLaunchSingleTop(true)
            .setRestoreState(false)
            .build()

    fun addDestinationChangedListener(
        navController: NavController,
        bottomNav: BottomNavigationView,
        onApplyBottomInset: () -> Unit
    ) {
        this.navController = navController
        val listener = NavController.OnDestinationChangedListener { controller, destination, _ ->
            if (activity.isDestroyed) return@OnDestinationChangedListener
            // Sync bottom nav selection (replacement for setupWithNavController)
            if (destination.id in TOP_LEVEL_DESTINATIONS) {
                bottomNav.selectedItemId = destination.id
            }
            if (destination.id == R.id.nav_warning) {
                bottomNav.setItemBackgroundResource(R.drawable.inset_nav_tile_warning)
            } else {
                bottomNav.setItemBackgroundResource(R.drawable.inset_nav_tile)
            }
            bottomNav.post { onApplyBottomInset() }
            // Cap back stack to avoid TransactionTooLargeException when saving state
            trimBackStackIfNeeded(controller)
        }
        this.destinationChangedListener = listener
        navController.addOnDestinationChangedListener(listener)
    }

    /**
     * Applies bottom inset to the current fragment's root (floating UI margin + RecyclerView padding).
     */
    fun applyBottomInset(bottomNav: View) {
        if (activity.isDestroyed) return
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

    /**
     * Trims the NavController back stack when it exceeds the limit to prevent
     * TransactionTooLargeException. Runs immediately so we catch growth before
     * it causes TransactionTooLargeException on activity pause.
     */
    private fun trimBackStackIfNeeded(controller: NavController) {
        if (activity.isDestroyed || activity.isFinishing) return
        val fragmentCount = navHostFragment?.childFragmentManager?.fragments?.size ?: 0
        if (fragmentCount <= MAX_NAV_BACK_STACK) return
        try {
            // Pop back to start destination - collapses to 1 fragment, minimal state
            controller.popBackStack(controller.graph.startDestinationId, false)
        } catch (_: Exception) { /* ignore - avoid crash during navigation */ }
    }

    /**
     * Aggressively trims the nav back stack before the activity goes to background.
     * Call from Activity.onPause() to ensure saved state stays under Binder limit
     * (e.g. when opening Settings from the 3-dot menu after rapid tab switching).
     */
    fun trimBackStackBeforeBackground() {
        if (activity.isDestroyed || activity.isFinishing) return
        val controller = navHostFragment?.navController ?: return
        val fragmentCount = navHostFragment?.childFragmentManager?.fragments?.size ?: 0
        if (fragmentCount <= MAX_NAV_BACK_STACK) return
        try {
            // Pop back to start destination - leaves 1 fragment, minimal saved state
            controller.popBackStack(controller.graph.startDestinationId, false)
        } catch (_: Exception) { /* ignore */ }
    }

    private companion object {
        /** Max entries in nav back stack; prevents TransactionTooLargeException.
         * With popUpTo(nav_history, false) we expect at most 2 (History + current tab). */
        const val MAX_NAV_BACK_STACK = 3
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.nav_history,
            R.id.nav_sensors,
            R.id.nav_warning,
            R.id.nav_chat,
            R.id.nav_settings
        )
    }

    /**
     * Clears listeners when Activity is destroyed to prevent memory leaks.
     * Call from Activity.onDestroy().
     */
    fun cleanup() {
        destinationChangedListener?.let { listener ->
            navController?.removeOnDestinationChangedListener(listener)
        }
        navController = null
        destinationChangedListener = null
    }
}
