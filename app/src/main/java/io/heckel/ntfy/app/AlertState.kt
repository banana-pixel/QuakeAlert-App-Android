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

    fun setActive(isActive: Boolean) {
        _isAlertActive.postValue(isActive)
        if (!isActive) {
            _latestAlert.postValue(null)
            _latestDistance.postValue("")
        }
    }

    fun setAlertData(notification: Notification, distance: String?) {
        _latestAlert.postValue(notification)
        if (distance != null) _latestDistance.postValue(distance)
        _isAlertActive.postValue(true)
    }

    fun setAlertFromRaw(message: String, distance: String, timestamp: Long) {
        val tempNotification = Notification(
            id = "temp_alert",
            subscriptionId = 0L,
            timestamp = timestamp,
            message = message,
            title = "Earthquake Alert",
            priority = 5,
            tags = "warning,quake",
            click = "",
            actions = null,
            deleted = false,
            icon = null,
            notificationId = 0,
            sequenceId = "0",
            encoding = "",
            contentType = "",
            attachment = null
        )
        setAlertData(tempNotification, distance)
    }
}