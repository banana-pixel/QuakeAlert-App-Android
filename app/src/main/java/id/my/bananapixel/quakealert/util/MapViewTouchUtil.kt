package id.my.bananapixel.quakealert.util

import android.view.MotionEvent
import android.view.View
import android.view.ViewParent
import org.maplibre.android.maps.MapView

/**
 * Prevents ViewPager2 (or any horizontal pager) from intercepting touch events when the user
 * pans/zooms the map. Call [MapViewTouchUtil.allowMapToConsumeHorizontalTouches] on any MapView
 * that is inside a ViewPager2.
 *
 * Without this, horizontal map panning would trigger a tab swipe instead of moving the map.
 */
object MapViewTouchUtil {

    /**
     * Attaches a touch listener to the MapView so that on ACTION_DOWN and ACTION_MOVE,
     * the view requests its parents (up to and including ViewPager2) to disallow touch
     * interception. This allows the map to receive horizontal swipes for panning.
     *
     * @param mapView The MapLibre [MapView] instance that should receive horizontal touches
     *   without the parent ViewPager2 intercepting them.
     */
    @JvmStatic
    fun allowMapToConsumeHorizontalTouches(mapView: MapView) {
        mapView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    disallowParentInterceptTouchEvent(v)
                }
            }
            false
        }
    }

    /**
     * Traverses up the parent chain and calls [ViewParent.requestDisallowInterceptTouchEvent]
     * on each parent so that ViewPager2 (and its internal RecyclerView) do not intercept
     * horizontal touch events.
     */
    private fun disallowParentInterceptTouchEvent(view: View) {
        var parent: ViewParent? = view.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(true)
            parent = parent.parent
        }
    }
}
