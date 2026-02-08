package io.heckel.ntfy.app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.heckel.ntfy.db.Notification

object AlertState {
    private val _isAlertActive = MutableLiveData<Boolean>(false)
    val isAlertActive: LiveData<Boolean> = _isAlertActive

    private val _latestAlert = MutableLiveData<Notification?>(null)
    val latestAlert: LiveData<Notification?> = _latestAlert

    fun setActive(isActive: Boolean, notification: Notification? = null) {
        _isAlertActive.postValue(isActive)
        if (isActive) {
            _latestAlert.postValue(notification)
        } else {
            _latestAlert.postValue(null)
        }
    }
}
