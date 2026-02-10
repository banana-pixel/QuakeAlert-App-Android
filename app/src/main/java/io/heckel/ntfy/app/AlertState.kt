package io.heckel.ntfy.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.heckel.ntfy.db.Notification

object AlertState {
    private val _isAlertActive = MutableLiveData<Boolean>(false)
    val isAlertActive: LiveData<Boolean> = _isAlertActive

    private val _latestAlert = MutableLiveData<Notification?>(null)
    val latestAlert: LiveData<Notification?> = _latestAlert

    private val _latestDistance = MutableLiveData<String?>("")
    val latestDistance: LiveData<String?> = _latestDistance

    fun setActive(isActive: Boolean, notification: Notification? = null, distance: String? = null) {
        _isAlertActive.postValue(isActive)
        if (isActive) {
            _latestAlert.postValue(notification)
            if (distance != null) {
                _latestDistance.postValue(distance)
            }
        } else {
            _latestAlert.postValue(null)
            _latestDistance.postValue("")
        }
    }
}
