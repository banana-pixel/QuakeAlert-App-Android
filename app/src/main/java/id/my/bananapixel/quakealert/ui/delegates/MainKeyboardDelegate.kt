package id.my.bananapixel.quakealert.ui.delegates

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import id.my.bananapixel.quakealert.ui.ChatFragment
import id.my.bananapixel.quakealert.R

/**
 * Handles keyboard (IME) visibility: hide/show bottom nav and scrim, adjust floating input margin
 * and RecyclerView padding, maintain scroll position on chat page.
 */
class MainKeyboardDelegate(
    private val activity: androidx.fragment.app.FragmentActivity,
    private val navigationDelegate: MainNavigationDelegate
) {
    private var rootView: View? = null

    fun setupKeyboardListener(
        bottomNav: BottomNavigationView,
        navHostFragment: NavHostFragment,
        rootView: View
    ) {
        this.rootView = rootView
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            if (activity.isDestroyed) return@setOnApplyWindowInsetsListener insets
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

            val currentFragment = navHostFragment.childFragmentManager.fragments.firstOrNull()
            val fragmentView = currentFragment?.view
            val floatingUi = fragmentView?.findViewById<View>(R.id.bottom_floating_ui)
            val recyclerView = fragmentView?.findViewById<RecyclerView>(R.id.recycler_view)
            val isChatPage = currentFragment is ChatFragment

            if (imeVisible && imeHeight > 0) {
                // Keyboard visible: hide bottom nav and scrim
                bottomNav.animate()
                    .translationY(bottomNav.height.toFloat())
                    .setDuration(200)
                    .withEndAction { bottomNav.visibility = View.GONE }
                    .start()

                activity.findViewById<View>(R.id.nav_scrim)?.animate()
                    ?.alpha(0f)
                    ?.setDuration(200)
                    ?.withEndAction {
                        activity.findViewById<View>(R.id.nav_scrim)?.visibility = View.GONE
                    }
                    ?.start()

                floatingUi?.let { ui ->
                    val params = ui.layoutParams as ViewGroup.MarginLayoutParams
                    params.bottomMargin = 0
                    ui.layoutParams = params
                }

                if (isChatPage) {
                    floatingUi?.post {
                        val inputHeight = floatingUi.height
                        recyclerView?.let { rv ->
                            rv.clipToPadding = false
                            rv.setPadding(
                                rv.paddingLeft,
                                rv.paddingTop,
                                rv.paddingRight,
                                inputHeight + (16 * activity.resources.displayMetrics.density).toInt()
                            )
                            rv.adapter?.let { adapter ->
                                if (adapter.itemCount > 0) {
                                    rv.smoothScrollToPosition(adapter.itemCount - 1)
                                }
                            }
                        }
                    }
                }
            } else {
                // Keyboard hidden: show bottom nav and scrim, restore layout
                bottomNav.visibility = View.VISIBLE
                bottomNav.animate().translationY(0f).setDuration(200).start()

                activity.findViewById<View>(R.id.nav_scrim)?.let { scrim ->
                    scrim.visibility = View.VISIBLE
                    scrim.animate().alpha(1f).setDuration(200).start()
                }

                bottomNav.post {
                    if (isChatPage && recyclerView != null) {
                        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
                        val lastVisiblePosition = layoutManager?.findLastCompletelyVisibleItemPosition() ?: -1

                        navigationDelegate.applyBottomInset(bottomNav)

                        recyclerView.post {
                            recyclerView.adapter?.let { adapter ->
                                if (adapter.itemCount > 0) {
                                    layoutManager?.scrollToPositionWithOffset(adapter.itemCount - 1, 0)
                                }
                            }
                        }
                    } else {
                        navigationDelegate.applyBottomInset(bottomNav)
                    }
                }
            }

            insets
        }
    }

    /**
     * Clears listener when Activity is destroyed to prevent memory leaks.
     * Call from Activity.onDestroy().
     */
    fun cleanup() {
        rootView?.let { ViewCompat.setOnApplyWindowInsetsListener(it, null) }
        rootView = null
    }
}
