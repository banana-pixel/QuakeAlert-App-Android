package id.my.bananapixel.quakealert.db

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.msg.ApiService
import id.my.bananapixel.quakealert.ui.QuakeReport
import id.my.bananapixel.quakealert.api.QuakeAlertApi
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.validUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class Repository(private val sharedPrefs: SharedPreferences, database: Database) {
    private val subscriptionDao = database.subscriptionDao()
    private val notificationDao = database.notificationDao()
    private val userDao = database.userDao()
    private val trustedCertificateDao = database.trustedCertificateDao()
    private val clientCertificateDao = database.clientCertificateDao()
    private val customHeaderDao = database.customHeaderDao()

    // Quake and Chat DAOs
    private val quakeDao = database.quakeHistoryDao()
    private val chatDao = database.chatMessageDao()

    private val connectionDetails = ConcurrentHashMap<String, ConnectionDetails>()
    private val connectionDetailsLiveData = MutableLiveData<Map<String, ConnectionDetails>>(connectionDetails)

    // --- QUAKE LOGIC: Single Source of Truth ---

    // The UI observes this Flow. It stays populated even when offline.
    val quakes: Flow<List<QuakeData>> = quakeDao.getAll()

    // Inside Repository.kt

    /**
     * Fetches quake reports from API and updates local DB.
     * @return [Result.success] on success, [Result.failure] with message on network/parse error.
     */
    suspend fun fetchQuakes(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val reports = executeFetchReports(context)
            val quakeEntities = reports.mapNotNull { report ->
                try {
                    QuakeData(
                        id = report.id.toString(),
                        magnitude = 0.0,
                        place = report.lokasi.ifEmpty { "Unknown" },
                        time = QuakeReportParser.parseQuakeTime(report.waktu_kejadian),
                        description = report.deskripsi,
                        latitude = report.latitude.orZeroIfNaN(),
                        longitude = report.longitude.orZeroIfNaN(),
                        pga = report.pga_maks.ifEmpty { "0" },
                        durasi = runCatching { report.durasi.toInt().coerceIn(0, Int.MAX_VALUE) }.getOrDefault(0),
                        station_id = report.station_id.ifEmpty { "N/A" },
                        intensity = report.intensitas_maks.ifEmpty { "I" }
                    )
                } catch (e: Exception) {
                    Log.w("NtfyRepository", "Skipping malformed quake report: ${e.message}")
                    null
                }
            }
            quakeDao.upsertAll(quakeEntities)
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeFetchReports(context: Context): List<QuakeReport> {
        val baseUrl = BuildConfig.APP_BASE_URL.trimEnd('/')
        val api = QuakeAlertApi.create(context, baseUrl)
        val body = api.getLaporan()
        return QuakeReportParser.parseReports(body)
    }

    private fun Double.orZeroIfNaN(): Double = if (this.isNaN()) 0.0 else this

    // --- CHAT LOGIC ---
    // Inside Repository class
    val chatMessages: Flow<List<ChatMessage>> = chatDao.getAll()

    suspend fun saveChatMessages(messages: List<ChatMessage>) {
        withContext(Dispatchers.IO) {
            chatDao.insertAll(messages) // Persists to disk
        }
    }

    // --- EXISTING ntfy LOGIC (Keep as is) ---
    private val connectionForceReconnectVersions = ConcurrentHashMap<String, Long>()
    val detailViewSubscriptionId = AtomicLong(0L)
    val mediaPlayer = MediaPlayer()

    init {
        Log.d(TAG, "Created $this")
    }

    fun getSubscriptionsLiveData(): LiveData<List<Subscription>> {
        val result = MediatorLiveData<List<Subscription>>()

        val subscriptionsSource = subscriptionDao.listFlow().asLiveData()
        val connectionsSource = connectionDetailsLiveData

        // Combine both sources manually
        result.addSource(subscriptionsSource) { subs ->
            result.value = toSubscriptionList(subs.orEmpty())
        }
        result.addSource(connectionsSource) {
            result.value = toSubscriptionList(subscriptionsSource.value.orEmpty())
        }

        return result
    }

    fun getSubscriptionIdsWithInstantStatusLiveData(): LiveData<Set<Pair<Long, Boolean>>> {
        return subscriptionDao
            .listFlow()
            .asLiveData()
            .map { list -> list.map { Pair(it.id, it.instant) }.toSet() }
    }

    suspend fun getSubscriptions(): List<Subscription> {
        return toSubscriptionList(subscriptionDao.list())
    }

    suspend fun getSubscriptionIdsWithInstantStatus(): Set<Pair<Long, Boolean>> {
        return subscriptionDao
            .list()
            .map { Pair(it.id, it.instant) }.toSet()
    }

    fun getSubscription(subscriptionId: Long): Subscription? {
        return toSubscription(subscriptionDao.get(subscriptionId))
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun getSubscription(baseUrl: String, topic: String): Subscription? {
        return toSubscription(subscriptionDao.get(baseUrl, topic))
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun getSubscriptionByConnectorToken(connectorToken: String): Subscription? {
        return toSubscription(subscriptionDao.getByConnectorToken(connectorToken))
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun addSubscription(subscription: Subscription) {
        subscriptionDao.add(subscription)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.update(subscription)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun removeSubscription(subscription: Subscription) {
        notificationDao.removeAll(subscription.id)
        subscriptionDao.remove(subscription.id)
        updateConnectionDetails(subscription.baseUrl, ConnectionState.NOT_APPLICABLE)
    }

    suspend fun getNotifications(): List<Notification> {
        return notificationDao.list()
    }

    fun getDeletedNotificationsWithAttachments(): List<Notification> {
        return notificationDao.listDeletedWithAttachments()
    }

    fun getActiveIconUris(): Set<String> {
        return notificationDao.listActiveIconUris().toSet()
    }

    fun clearIconUri(uri: String) {
        notificationDao.clearIconUri(uri)
    }

    fun getNotificationsLiveData(subscriptionId: Long): LiveData<List<Notification>> {
        return notificationDao.listFlow(subscriptionId).asLiveData()
    }

    fun getNotificationsFilteredLiveData(subscriptionId: Long, query: String): LiveData<List<Notification>> {
        return notificationDao.listFlowFiltered(subscriptionId, query).asLiveData()
    }

    fun getNotification(notificationId: String): Notification? {
        return notificationDao.get(notificationId)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun addNotification(notification: Notification): Boolean {
        val maybeExistingNotification = notificationDao.get(notification.id)
        if (maybeExistingNotification != null || notification.event != ApiService.EVENT_MESSAGE) {
            return false
        }
        if (notification.sequenceId.isNotEmpty()) {
            notificationDao.markAsDeletedBySequenceId(notification.subscriptionId, notification.sequenceId)
        }
        subscriptionDao.updateLastNotificationId(notification.subscriptionId, notification.id)
        notificationDao.add(notification)
        return true
    }

    fun updateNotification(notification: Notification) {
        notificationDao.update(notification)
    }

    fun undeleteNotification(notificationId: String) {
        notificationDao.undelete(notificationId)
    }

    fun markAsDeleted(notificationId: String) {
        notificationDao.markAsDeleted(notificationId)
    }

    fun markAsDeletedBySequenceId(subscriptionId: Long, sequenceId: String) {
        notificationDao.markAsDeletedBySequenceId(subscriptionId, sequenceId)
    }

    fun updateLastNotificationId(subscriptionId: Long, notificationId: String) {
        subscriptionDao.updateLastNotificationId(subscriptionId, notificationId)
    }

    fun getLastNotificationId(subscriptionIds: Collection<Long>): String? {
        return subscriptionDao.getLastNotificationId(subscriptionIds)
    }

    fun markAllAsDeleted(subscriptionId: Long) {
        notificationDao.markAllAsDeleted(subscriptionId)
    }

    fun markAllAsRead(subscriptionId: Long) {
        notificationDao.markAllAsRead(subscriptionId)
    }

    fun markAsReadBySequenceId(subscriptionId: Long, sequenceId: String) {
        notificationDao.markAsReadBySequenceId(subscriptionId, sequenceId)
    }

    fun markAsDeletedIfOlderThan(subscriptionId: Long, olderThanTimestamp: Long) {
        notificationDao.markAsDeletedIfOlderThan(subscriptionId, olderThanTimestamp)
    }

    fun removeNotificationsIfOlderThan(subscriptionId: Long, olderThanTimestamp: Long) {
        notificationDao.removeIfOlderThan(subscriptionId, olderThanTimestamp)
    }

    suspend fun getUsers(): List<User> {
        return userDao.list()
    }

    fun getUsersLiveData(): LiveData<List<User>> {
        return userDao.listFlow().asLiveData()
    }

    suspend fun addUser(user: User) {
        userDao.insert(user)
    }

    suspend fun updateUser(user: User) {
        userDao.update(user)
    }

    suspend fun getUser(baseUrl: String): User? {
        return userDao.get(baseUrl)
    }

    suspend fun deleteUser(baseUrl: String) {
        userDao.delete(baseUrl)
    }

    suspend fun getTrustedCertificates(): List<TrustedCertificate> {
        return trustedCertificateDao.list()
    }

    suspend fun getTrustedCertificate(baseUrl: String): TrustedCertificate? {
        return trustedCertificateDao.get(baseUrl)
    }

    suspend fun addTrustedCertificate(baseUrl: String, pem: String) {
        trustedCertificateDao.insert(TrustedCertificate(baseUrl, pem))
    }

    suspend fun removeTrustedCertificate(baseUrl: String) {
        trustedCertificateDao.delete(baseUrl)
    }

    suspend fun getClientCertificates(): List<ClientCertificate> {
        return clientCertificateDao.list()
    }

    suspend fun getClientCertificate(baseUrl: String): ClientCertificate? {
        return clientCertificateDao.get(baseUrl)
    }

    suspend fun addClientCertificate(baseUrl: String, p12Base64: String, password: String) {
        clientCertificateDao.insert(ClientCertificate(baseUrl, p12Base64, password))
    }

    suspend fun removeClientCertificate(baseUrl: String) {
        clientCertificateDao.delete(baseUrl)
    }

    fun getPollWorkerVersion(): Int {
        return sharedPrefs.getInt(SHARED_PREFS_POLL_WORKER_VERSION, 0)
    }

    fun setPollWorkerVersion(version: Int) {
        sharedPrefs.edit {
            putInt(SHARED_PREFS_POLL_WORKER_VERSION, version)
        }
    }

    fun getDeleteWorkerVersion(): Int {
        return sharedPrefs.getInt(SHARED_PREFS_DELETE_WORKER_VERSION, 0)
    }

    fun setDeleteWorkerVersion(version: Int) {
        sharedPrefs.edit {
            putInt(SHARED_PREFS_DELETE_WORKER_VERSION, version)
        }
    }

    fun getAutoRestartWorkerVersion(): Int {
        return sharedPrefs.getInt(SHARED_PREFS_AUTO_RESTART_WORKER_VERSION, 0)
    }

    fun setAutoRestartWorkerVersion(version: Int) {
        sharedPrefs.edit {
            putInt(SHARED_PREFS_AUTO_RESTART_WORKER_VERSION, version)
        }
    }

    fun setMinPriority(minPriority: Int) {
        if (minPriority <= MIN_PRIORITY_ANY) {
            sharedPrefs.edit { remove(SHARED_PREFS_MIN_PRIORITY) }
        } else {
            sharedPrefs.edit { putInt(SHARED_PREFS_MIN_PRIORITY, minPriority) }
        }
    }

    fun getMinPriority(): Int {
        return sharedPrefs.getInt(SHARED_PREFS_MIN_PRIORITY, MIN_PRIORITY_ANY)
    }

    fun getAutoDownloadMaxSize(): Long {
        val defaultValue = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            AUTO_DOWNLOAD_NEVER
        } else {
            AUTO_DOWNLOAD_DEFAULT
        }
        return sharedPrefs.getLong(SHARED_PREFS_AUTO_DOWNLOAD_MAX_SIZE, defaultValue)
    }

    fun setAutoDownloadMaxSize(maxSize: Long) {
        sharedPrefs.edit { putLong(SHARED_PREFS_AUTO_DOWNLOAD_MAX_SIZE, maxSize) }
    }

    fun getAutoDeleteSeconds(): Long {
        return sharedPrefs.getLong(SHARED_PREFS_AUTO_DELETE_SECONDS, AUTO_DELETE_DEFAULT_SECONDS)
    }

    fun setAutoDeleteSeconds(seconds: Long) {
        sharedPrefs.edit { putLong(SHARED_PREFS_AUTO_DELETE_SECONDS, seconds) }
    }

    /** True when the user has set or the app has obtained a location (no fake default). */
    fun isUserLocationSet(): Boolean =
        sharedPrefs.contains(SHARED_PREFS_USER_LATITUDE) && sharedPrefs.contains(SHARED_PREFS_USER_LONGITUDE)

    /** Returns user latitude, or [Double.NaN] when location has never been set. */
    fun getUserLatitude(): Double {
        if (!sharedPrefs.contains(SHARED_PREFS_USER_LATITUDE)) return Double.NaN
        return Double.fromBits(sharedPrefs.getLong(SHARED_PREFS_USER_LATITUDE, 0L))
    }

    fun setUserLatitude(latitude: Double) {
        sharedPrefs.edit { putLong(SHARED_PREFS_USER_LATITUDE, latitude.toRawBits()) }
    }

    /** Returns user longitude, or [Double.NaN] when location has never been set. */
    fun getUserLongitude(): Double {
        if (!sharedPrefs.contains(SHARED_PREFS_USER_LONGITUDE)) return Double.NaN
        return Double.fromBits(sharedPrefs.getLong(SHARED_PREFS_USER_LONGITUDE, 0L))
    }

    fun setUserLongitude(longitude: Double) {
        sharedPrefs.edit { putLong(SHARED_PREFS_USER_LONGITUDE, longitude.toRawBits()) }
    }

    fun getUserCityName(): String {
        return sharedPrefs.getString(SHARED_PREFS_USER_CITY_NAME, "").orEmpty()
    }

    fun setUserCityName(cityName: String?) {
        sharedPrefs.edit {
            if (cityName.isNullOrBlank()) remove(SHARED_PREFS_USER_CITY_NAME)
            else putString(SHARED_PREFS_USER_CITY_NAME, cityName)
        }
    }

    fun setDarkMode(mode: Int) {
        if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            sharedPrefs.edit { remove(SHARED_PREFS_DARK_MODE) }
        } else {
            sharedPrefs.edit { putInt(SHARED_PREFS_DARK_MODE, mode) }
        }
    }

    fun getDarkMode(): Int {
        return sharedPrefs.getInt(SHARED_PREFS_DARK_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    fun setDynamicColorsEnabled(enabled: Boolean) {
        sharedPrefs.edit(commit = true) { putBoolean(SHARED_PREFS_DYNAMIC_COLORS, enabled) }
    }

    fun getDynamicColorsEnabled(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_DYNAMIC_COLORS, false)
    }

    fun setConnectionProtocol(connectionProtocol: String) {
        sharedPrefs.edit { putString(SHARED_PREFS_CONNECTION_PROTOCOL, connectionProtocol) }
    }

    fun getConnectionProtocol(): String {
        return sharedPrefs.getString(SHARED_PREFS_CONNECTION_PROTOCOL, null) ?: CONNECTION_PROTOCOL_WS
    }

    fun getBroadcastEnabled(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_BROADCAST_ENABLED, true)
    }

    fun setBroadcastEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean(SHARED_PREFS_BROADCAST_ENABLED, enabled) }
    }

    fun getUnifiedPushEnabled(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_UNIFIEDPUSH_ENABLED, true)
    }

    fun setUnifiedPushEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean(SHARED_PREFS_UNIFIEDPUSH_ENABLED, enabled) }
    }

    fun getInsistentMaxPriorityEnabled(): Boolean {
        // Default to true so "Keep alerting" starts enabled for new installs
        return sharedPrefs.getBoolean(SHARED_PREFS_INSISTENT_MAX_PRIORITY_ENABLED, true)
    }

    fun setInsistentMaxPriorityEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean(SHARED_PREFS_INSISTENT_MAX_PRIORITY_ENABLED, enabled) }
    }

    fun getRecordLogs(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_RECORD_LOGS_ENABLED, false)
    }

    fun setRecordLogsEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean(SHARED_PREFS_RECORD_LOGS_ENABLED, enabled) }
    }

    fun getMessageBarEnabled(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_MESSAGE_BAR_ENABLED, true)
    }

    fun setMessageBarEnabled(enabled: Boolean) {
        sharedPrefs.edit { putBoolean(SHARED_PREFS_MESSAGE_BAR_ENABLED, enabled) }
    }

    fun isOnboardingCompleted(): Boolean {
        return sharedPrefs.getBoolean(SHARED_PREFS_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        sharedPrefs.edit(commit = true) { putBoolean(SHARED_PREFS_ONBOARDING_COMPLETED, completed) }
    }

    fun getBatteryOptimizationsRemindTime(): Long {
        return sharedPrefs.getLong(SHARED_PREFS_BATTERY_OPTIMIZATIONS_REMIND_TIME, BATTERY_OPTIMIZATIONS_REMIND_TIME_ALWAYS)
    }

    fun setBatteryOptimizationsRemindTime(timeMillis: Long) {
        sharedPrefs.edit { putLong(SHARED_PREFS_BATTERY_OPTIMIZATIONS_REMIND_TIME, timeMillis) }
    }

    fun getWebSocketRemindTime(): Long {
        return sharedPrefs.getLong(SHARED_PREFS_WEBSOCKET_REMIND_TIME, WEBSOCKET_REMIND_TIME_ALWAYS)
    }

    fun setWebSocketRemindTime(timeMillis: Long) {
        sharedPrefs.edit { putLong(SHARED_PREFS_WEBSOCKET_REMIND_TIME, timeMillis) }
    }

    fun getWebSocketReconnectRemindTime(): Long {
        return sharedPrefs.getLong(SHARED_PREFS_WEBSOCKET_RECONNECT_REMIND_TIME, WEBSOCKET_RECONNECT_REMIND_TIME_ALWAYS)
    }

    fun setWebSocketReconnectRemindTime(timeMillis: Long) {
        sharedPrefs.edit { putLong(SHARED_PREFS_WEBSOCKET_RECONNECT_REMIND_TIME, timeMillis) }
    }

    fun getDefaultBaseUrl(): String? {
        return sharedPrefs.getString(SHARED_PREFS_DEFAULT_BASE_URL, null) ?:
        sharedPrefs.getString(SHARED_PREFS_UNIFIED_PUSH_BASE_URL, null)
    }

    fun setDefaultBaseUrl(baseUrl: String) {
        if (baseUrl == "") {
            sharedPrefs.edit { remove(SHARED_PREFS_UNIFIED_PUSH_BASE_URL).remove(SHARED_PREFS_DEFAULT_BASE_URL) }
        } else {
            sharedPrefs.edit { remove(SHARED_PREFS_UNIFIED_PUSH_BASE_URL).putString(SHARED_PREFS_DEFAULT_BASE_URL, baseUrl) }
        }
    }

    suspend fun getCustomHeaders(): List<CustomHeader> {
        return customHeaderDao.list()
    }

    suspend fun getCustomHeaders(baseUrl: String): List<CustomHeader> {
        return customHeaderDao.get(baseUrl)
    }

    suspend fun addCustomHeader(header: CustomHeader) {
        customHeaderDao.insert(header)
    }

    suspend fun updateCustomHeader(oldHeader: CustomHeader, newHeader: CustomHeader) {
        customHeaderDao.delete(oldHeader.baseUrl, oldHeader.name)
        customHeaderDao.insert(newHeader)
    }

    suspend fun deleteCustomHeader(header: CustomHeader) {
        customHeaderDao.delete(header.baseUrl, header.name)
    }

    fun isGlobalMuted(): Boolean {
        val mutedUntil = getGlobalMutedUntil()
        return mutedUntil == 1L || (mutedUntil > 1L && mutedUntil > System.currentTimeMillis()/1000)
    }

    fun getGlobalMutedUntil(): Long {
        return sharedPrefs.getLong(SHARED_PREFS_MUTED_UNTIL_TIMESTAMP, 0L)
    }

    fun setGlobalMutedUntil(mutedUntilTimestamp: Long) {
        sharedPrefs.edit { putLong(SHARED_PREFS_MUTED_UNTIL_TIMESTAMP, mutedUntilTimestamp) }
    }

    fun checkGlobalMutedUntil(): Boolean {
        val mutedUntil = sharedPrefs.getLong(SHARED_PREFS_MUTED_UNTIL_TIMESTAMP, 0L)
        val expired = mutedUntil > 1L && System.currentTimeMillis()/1000 > mutedUntil
        if (expired) {
            sharedPrefs.edit { putLong(SHARED_PREFS_MUTED_UNTIL_TIMESTAMP, 0L) }
            return true
        }
        return false
    }

    fun getLastShareTopics(): List<String> {
        val topics = sharedPrefs.getString(SHARED_PREFS_LAST_TOPICS, "") ?: ""
        return topics.split("\n").filter { validUrl(it) }
    }

    fun addLastShareTopic(topic: String) {
        val topics = (getLastShareTopics().filterNot { it == topic } + topic).takeLast(LAST_TOPICS_COUNT)
        sharedPrefs.edit { putString(SHARED_PREFS_LAST_TOPICS, topics.joinToString(separator = "\n")) }
    }

    private fun toSubscriptionList(list: List<SubscriptionWithMetadata>): List<Subscription> {
        return list.map { s ->
            Subscription(
                id = s.id,
                baseUrl = s.baseUrl,
                topic = s.topic,
                instant = s.instant,
                dedicatedChannels = s.dedicatedChannels,
                mutedUntil = s.mutedUntil,
                minPriority = s.minPriority,
                autoDelete = s.autoDelete,
                insistent = s.insistent,
                lastNotificationId = s.lastNotificationId,
                icon = s.icon,
                upAppId = s.upAppId,
                upConnectorToken = s.upConnectorToken,
                displayName = s.displayName,
                totalCount = s.totalCount,
                newCount = s.newCount,
                lastActive = s.lastActive,
                connectionDetails = connectionDetails[s.baseUrl] ?: ConnectionDetails()
            )
        }
    }

    private fun toSubscription(s: SubscriptionWithMetadata?): Subscription? {
        if (s == null) return null
        return Subscription(
            id = s.id,
            baseUrl = s.baseUrl,
            topic = s.topic,
            instant = s.instant,
            dedicatedChannels = s.dedicatedChannels,
            mutedUntil = s.mutedUntil,
            minPriority = s.minPriority,
            autoDelete = s.autoDelete,
            insistent = s.insistent,
            lastNotificationId = s.lastNotificationId,
            icon = s.icon,
            upAppId = s.upAppId,
            upConnectorToken = s.upConnectorToken,
            displayName = s.displayName,
            totalCount = s.totalCount,
            newCount = s.newCount,
            lastActive = s.lastActive,
            connectionDetails = connectionDetails[s.baseUrl] ?: ConnectionDetails()
        )
    }

    fun updateConnectionDetails(baseUrl: String, state: ConnectionState, error: Throwable? = null, nextRetryTime: Long = 0L, latencyMs: Int? = null) {
        val current = connectionDetails[baseUrl]
        val resolvedLatency = when (state) {
            ConnectionState.CONNECTED -> latencyMs
            else -> current?.latencyMs
        }
        val details = ConnectionDetails(state, error, nextRetryTime, resolvedLatency)
        if (current != details) {
            if (state == ConnectionState.NOT_APPLICABLE && error == null) {
                connectionDetails.remove(baseUrl)
            } else {
                connectionDetails[baseUrl] = details
            }
            connectionDetailsLiveData.postValue(connectionDetails.toMap())
            Log.d(TAG, "Connection details updated for $baseUrl: state=$state, error=${error?.message}, nextRetry=$nextRetryTime, latencyMs=$resolvedLatency")
        }
    }

    fun getConnectionDetailsLiveData(): LiveData<Map<String, ConnectionDetails>> {
        return connectionDetailsLiveData
    }

    fun getConnectionDetails(): Map<String, ConnectionDetails> {
        return connectionDetails.toMap()
    }

    fun getConnectionForceReconnectVersion(baseUrl: String): Long {
        return connectionForceReconnectVersions[baseUrl] ?: 0L
    }

    fun incrementConnectionForceReconnectVersion(baseUrl: String) {
        connectionForceReconnectVersions.compute(baseUrl) { _, current -> (current ?: 0L) + 1 }
        Log.d(TAG, "Connection force reconnect version incremented for $baseUrl: ${connectionForceReconnectVersions[baseUrl]}")
    }

    companion object {
        const val SHARED_PREFS_ID = "MainPreferences"
        const val SHARED_PREFS_POLL_WORKER_VERSION = "PollWorkerVersion"
        const val SHARED_PREFS_DELETE_WORKER_VERSION = "DeleteWorkerVersion"
        const val SHARED_PREFS_AUTO_RESTART_WORKER_VERSION = "AutoRestartWorkerVersion"
        const val SHARED_PREFS_MUTED_UNTIL_TIMESTAMP = "MutedUntil"
        const val SHARED_PREFS_MIN_PRIORITY = "MinPriority"
        const val SHARED_PREFS_AUTO_DOWNLOAD_MAX_SIZE = "AutoDownload"
        const val SHARED_PREFS_AUTO_DELETE_SECONDS = "AutoDelete"
        const val SHARED_PREFS_CONNECTION_PROTOCOL = "ConnectionProtocol"
        const val SHARED_PREFS_DARK_MODE = "DarkMode"
        const val SHARED_PREFS_DYNAMIC_COLORS = "DynamicColors"
        const val SHARED_PREFS_BROADCAST_ENABLED = "BroadcastEnabled"
        const val SHARED_PREFS_UNIFIEDPUSH_ENABLED = "UnifiedPushEnabled"
        const val SHARED_PREFS_INSISTENT_MAX_PRIORITY_ENABLED = "InsistentMaxPriority"
        const val SHARED_PREFS_RECORD_LOGS_ENABLED = "RecordLogs"
        const val SHARED_PREFS_MESSAGE_BAR_ENABLED = "MessageBarEnabled"
        const val SHARED_PREFS_BATTERY_OPTIMIZATIONS_REMIND_TIME = "BatteryOptimizationsRemindTime"
        const val SHARED_PREFS_WEBSOCKET_REMIND_TIME = "JsonStreamRemindTime"
        const val SHARED_PREFS_WEBSOCKET_RECONNECT_REMIND_TIME = "WebSocketReconnectRemindTime"
        const val SHARED_PREFS_UNIFIED_PUSH_BASE_URL = "UnifiedPushBaseURL"
        const val SHARED_PREFS_DEFAULT_BASE_URL = "DefaultBaseURL"
        const val SHARED_PREFS_LAST_TOPICS = "LastTopics"
        const val SHARED_PREFS_USER_LATITUDE = "UserLatitude"
        const val SHARED_PREFS_USER_LONGITUDE = "UserLongitude"
        const val SHARED_PREFS_USER_CITY_NAME = "UserCityName"
        const val SHARED_PREFS_ALERT_RADIUS = "alert_radius"
        const val SHARED_PREFS_ONBOARDING_COMPLETED = "OnboardingCompleted"

        private const val LAST_TOPICS_COUNT = 3
        const val MIN_PRIORITY_USE_GLOBAL = 0
        const val MIN_PRIORITY_ANY = 1
        const val MUTED_UNTIL_SHOW_ALL = 0L
        const val MUTED_UNTIL_FOREVER = 1L
        const val MUTED_UNTIL_TOMORROW = 2L

        private const val ONE_MB = 1024 * 1024L
        const val AUTO_DOWNLOAD_NEVER = 0L
        const val AUTO_DOWNLOAD_ALWAYS = 1L
        const val AUTO_DOWNLOAD_DEFAULT = ONE_MB

        private const val ONE_DAY_SECONDS = 24 * 60 * 60L
        const val AUTO_DELETE_USE_GLOBAL = -1L
        const val AUTO_DELETE_NEVER = 0L
        const val AUTO_DELETE_ONE_DAY_SECONDS = ONE_DAY_SECONDS
        const val AUTO_DELETE_THREE_DAYS_SECONDS = 3 * ONE_DAY_SECONDS
        const val AUTO_DELETE_ONE_WEEK_SECONDS = 7 * ONE_DAY_SECONDS
        const val AUTO_DELETE_ONE_MONTH_SECONDS = 30 * ONE_DAY_SECONDS
        const val AUTO_DELETE_THREE_MONTHS_SECONDS = 90 * ONE_DAY_SECONDS
        const val AUTO_DELETE_DEFAULT_SECONDS = AUTO_DELETE_ONE_MONTH_SECONDS

        const val INSISTENT_MAX_PRIORITY_USE_GLOBAL = -1
        const val INSISTENT_MAX_PRIORITY_ENABLED = 1
        const val CONNECTION_PROTOCOL_JSONHTTP = "jsonhttp"
        const val CONNECTION_PROTOCOL_WS = "ws"
        const val BATTERY_OPTIMIZATIONS_REMIND_TIME_ALWAYS = 1L
        const val BATTERY_OPTIMIZATIONS_REMIND_TIME_NEVER = Long.MAX_VALUE
        const val WEBSOCKET_REMIND_TIME_ALWAYS = 1L
        const val WEBSOCKET_REMIND_TIME_NEVER = Long.MAX_VALUE
        const val WEBSOCKET_RECONNECT_REMIND_TIME_ALWAYS = 1L
        const val WEBSOCKET_RECONNECT_REMIND_TIME_NEVER = Long.MAX_VALUE

        /** Used only for map display when user location is not set (e.g. Settings map center). */
        const val DEFAULT_MAP_CENTER_LAT = -6.9175
        const val DEFAULT_MAP_CENTER_LON = 107.6191

        private const val TAG = "NtfyRepository"
        private var instance: Repository? = null

        /**
         * Set by Hilt module so [getInstance] returns the injected singleton.
         * Enables backward compatibility during DI migration.
         */
        @Volatile
        var hiltInstance: Repository? = null

        fun getInstance(context: Context): Repository {
            hiltInstance?.let { return it }
            val database = Database.getInstance(context.applicationContext)
            val sharedPrefs = context.getSharedPreferences(SHARED_PREFS_ID, Context.MODE_PRIVATE)
            return getInstance(sharedPrefs, database)
        }

        private fun getInstance(sharedPrefs: SharedPreferences, database: Database): Repository {
            return synchronized(Repository::class) {
                val newInstance = instance ?: Repository(sharedPrefs, database)
                instance = newInstance
                newInstance
            }
        }
    }
}