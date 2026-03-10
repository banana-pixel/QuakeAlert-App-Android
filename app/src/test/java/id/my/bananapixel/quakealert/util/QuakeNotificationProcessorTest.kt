package id.my.bananapixel.quakealert.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.PendingIntent
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Notification
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.util.QuakeNotificationProcessor.Companion.ACTION_QUAKE_ALERT
import id.my.bananapixel.quakealert.util.QuakeNotificationProcessor.Companion.DEFAULT_ALERT_RADIUS_KM
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.slot
import io.mockk.verify
import id.my.bananapixel.quakealert.app.AlertState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuakeNotificationProcessorTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockRepository: Repository
    private lateinit var processor: QuakeNotificationProcessor

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        mockRepository = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(Repository.SHARED_PREFS_ID, Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.getInt(Repository.SHARED_PREFS_ALERT_RADIUS, DEFAULT_ALERT_RADIUS_KM) } returns 500
        every { mockContext.getString(R.string.notification_silent_quake, any()) } answers { "Silent Quake ${it.invocation.args[1]}" }
        every { mockContext.getString(R.string.notification_danger_quake, any()) } answers { "Danger Quake ${it.invocation.args[1]}" }
        every { mockContext.packageName } returns "id.my.bananapixel.quakealert"

        mockkObject(AlertState)
        every { AlertState.setAlertData(any(), any()) } returns Unit

        startKoin {
            modules(module {
                single { mockRepository }
            })
        }

        processor = QuakeNotificationProcessor(mockContext)
    }

    @After
    fun teardown() {
        stopKoin()
        unmockkObject(AlertState)
    }

    private fun createSubscription(): Subscription {
        return Subscription(
            id = 1L,
            baseUrl = "https://ntfy.sh",
            topic = "test",
            instant = false,
            mutedUntil = 0L,
            minPriority = 1,
            autoDelete = 0L,
            insistent = 0,
            lastNotificationId = null,
            icon = null,
            upAppId = null,
            upConnectorToken = null,
            displayName = null,
            dedicatedChannels = false
        )
    }

    private fun createNotification(tags: String, priority: Int = 5): Notification {
        return Notification(
            id = "abc",
            subscriptionId = 1L,
            timestamp = 1000L,
            sequenceId = "123",
            title = "Quake",
            message = "Shaking",
            contentType = "",
            encoding = "",
            notificationId = 123,
            priority = priority,
            tags = tags,
            click = "",
            icon = null,
            actions = null,
            attachment = null,
            deleted = false
        )
    }

    @Test
    fun `process - nearby earthquake escalates priority and broadcasts action`() {
        // Mock user location and earthquake very close
        every { mockRepository.isUserLocationSet() } returns true
        every { mockRepository.getUserLatitude() } returns 0.0
        every { mockRepository.getUserLongitude() } returns 0.0

        val notification = createNotification("earthquake,geo:0.01;0.01")
        val subscription = createSubscription()

        val result = processor.process(subscription, notification, "https://ntfy.sh")

        // Priority should be PRIORITY_MAX (5)
        assertEquals(PRIORITY_MAX, result.displayPriority)
        // Title should be Danger Quake
        assertTrue(result.title.contains("Danger Quake"))
        
        // Verify broadcast
        verify(exactly = 1) { mockContext.sendBroadcast(any()) }
    }

    @Test
    fun `process - faraway earthquake uses standard priority and does not broadcast`() {
        // Mock user location and earthquake far away (>500km)
        every { mockRepository.isUserLocationSet() } returns true
        every { mockRepository.getUserLatitude() } returns 0.0
        every { mockRepository.getUserLongitude() } returns 0.0

        val notification = createNotification("earthquake,geo:10.0;10.0") // far
        val subscription = createSubscription()

        val result = processor.process(subscription, notification, "https://ntfy.sh")

        // Priority should be PRIORITY_MIN (1) because it's far away and silent
        assertEquals(PRIORITY_MIN, result.displayPriority)
        assertTrue(result.title.contains("Silent Quake"))
        
        // Verify no broadcast (Priority 1 is less than PRIORITY_MAX)
        verify(exactly = 0) { mockContext.sendBroadcast(any()) }
    }

    @Test
    fun `process - malformed or missing geo tags uses standard ntfy behavior`() {
        every { mockRepository.isUserLocationSet() } returns true
        val notification = createNotification("earthquake,geo:invalid", priority = 3) // Standard priority
        val subscription = createSubscription()

        val result = processor.process(subscription, notification, "https://ntfy.sh")

        // Priority matches original notification
        assertEquals(3, result.displayPriority)
        assertNull(result.distanceLabel)

        // Broadcast should NOT trigger because priority is not MAX and distance logic fell back
        verify(exactly = 0) { mockContext.sendBroadcast(any()) }
    }
    
    @Test
    fun `process - distance calculation format is correct`() {
        every { mockRepository.isUserLocationSet() } returns true
        every { mockRepository.getUserLatitude() } returns 0.0
        every { mockRepository.getUserLongitude() } returns 0.0

        val notification = createNotification("earthquake,geo:0.01;0.0")
        val subscription = createSubscription()

        val result = processor.process(subscription, notification, "https://ntfy.sh")

        // Using GeoUtil formatter, roughly 1.1 km
        assertNotNull(result.distanceLabel)
        assertEquals("1.1", result.distanceLabel)
    }
}
