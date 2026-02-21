package id.my.bananapixel.quakealert.ui.delegates

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.ui.MainAdapter
import id.my.bananapixel.quakealert.ui.SubscriptionsViewModel
import id.my.bananapixel.quakealert.util.dangerButton
import androidx.appcompat.app.AlertDialog

private const val ANIMATION_DURATION = 80L

/**
 * Handles contextual action mode: multi-select, delete confirmation, FAB hide/show animation.
 */
class MainActionModeDelegate(
    private val activity: androidx.appcompat.app.AppCompatActivity,
    private val adapter: MainAdapter,
    private val fab: FloatingActionButton,
    private val viewModel: SubscriptionsViewModel,
    private val onRedrawList: () -> Unit
) {
    private var actionMode: ActionMode? = null

    val actionModeCallback: ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            actionMode = mode
            mode?.let {
                it.menuInflater.inflate(R.menu.menu_main_action_mode, menu)
                it.title = "1"
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.main_action_mode_delete -> {
                    onMultiDeleteClick()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            endActionModeAndRedraw()
        }
    }

    fun isActive(): Boolean = actionMode != null

    fun handleItemClick(subscription: Subscription) {
        adapter.toggleSelection(subscription.id)
        if (adapter.selected.isEmpty()) {
            finishActionMode()
        } else {
            actionMode?.title = adapter.selected.size.toString()
        }
    }

    fun startActionMode(subscription: Subscription) {
        actionMode = activity.startSupportActionMode(actionModeCallback)
        adapter.toggleSelection(subscription.id)

        fab.alpha = 1f
        fab.animate()
            .alpha(0f)
            .setDuration(ANIMATION_DURATION)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!activity.isDestroyed) fab.visibility = View.GONE
                }
            })
    }

    private fun onMultiDeleteClick() {
        MaterialAlertDialogBuilder(activity)
            .setMessage(R.string.main_action_mode_delete_dialog_message)
            .setPositiveButton(R.string.main_action_mode_delete_dialog_permanently_delete) { _, _ ->
                if (!activity.isDestroyed) {
                    adapter.selected.forEach { subscriptionId ->
                        viewModel.remove(activity, subscriptionId)
                    }
                }
                finishActionMode()
            }
            .setNegativeButton(R.string.main_action_mode_delete_dialog_cancel) { _, _ ->
                finishActionMode()
            }
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).dangerButton()
                }
                dialog.show()
            }
    }

    private fun finishActionMode() {
        actionMode?.finish()
        endActionModeAndRedraw()
    }

    private fun endActionModeAndRedraw() {
        actionMode = null
        adapter.selected.clear()
        if (activity.isDestroyed) return
        onRedrawList()

        fab.alpha = 0f
        fab.visibility = View.VISIBLE
        fab.animate()
            .alpha(1f)
            .setDuration(ANIMATION_DURATION)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!activity.isDestroyed) fab.visibility = View.VISIBLE
                }
            })
    }

    /**
     * Cleans up when Activity is destroyed to prevent memory leaks.
     * Finishes action mode if active. Call from Activity.onDestroy().
     */
    fun cleanup() {
        if (actionMode != null) {
            actionMode?.finish()
            actionMode = null
            adapter.selected.clear()
        }
    }
}
