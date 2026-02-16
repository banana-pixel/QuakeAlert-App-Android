package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.materialswitch.MaterialSwitch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.db.Notification
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.msg.NotificationService
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.PRIORITY_MAX
import id.my.bananapixel.quakealert.util.isIgnoringBatteryOptimizations
import java.util.Random

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var dotsContainer: LinearLayout
    private lateinit var btnNext: TextView
    private lateinit var btnBack: TextView
    private lateinit var btnSkip: TextView
    private lateinit var repository: Repository
    private val notifier by lazy { NotificationService(this) }

    private val pages = listOf(
        OnboardingPage.Intro(
            iconRes = R.drawable.ic_onboarding_seismograph,
            title = "Welcome to\nQuakeAlert App",
            subtitle = "Real-time earthquake early warning system.\nKeep safe with instant seismic alerts powered by IoT technology."
        ),
        OnboardingPage.Intro(
            iconRes = R.drawable.ic_onboarding_esp32,
            title = "ESP32 Powered\nDetection",
            subtitle = "QuakeAlert connects to a network of ESP32 microcontrollers equipped with seismic sensors.\n\nWhen an earthquake is detected, you receive an notification for you to prepare."
        ),
        // Required permissions first
        OnboardingPage.Permission(
            iconRes = R.drawable.ic_onboarding_notification,
            title = "Notification\nPermission",
            description = "To receive earthquake alerts, QuakeAlert needs permission to send you notifications. This is critical for your app functionality.",
            buttonText = "Enable Notifications",
            type = PermissionType.NOTIFICATION,
            required = true
        ),
        OnboardingPage.Permission(
            iconRes = R.drawable.ic_onboarding_battery,
            title = "Battery\nOptimization",
            description = "To ensure alerts are never delayed, QuakeAlert needs to run without battery restrictions. It keeps connection alive 24/7.",
            buttonText = "Disable Restriction",
            type = PermissionType.BATTERY,
            required = true
        ),
        // Optional permissions after
        OnboardingPage.Permission(
            iconRes = R.drawable.ic_onboarding_location,
            title = "Location\nAccess",
            description = "QuakeAlert uses your approximate location to calculate distance from earthquakes and provide more relevant alerts.",
            buttonText = "Grant Location",
            type = PermissionType.LOCATION,
            required = false
        ),
        OnboardingPage.TestNotification
    )

    // Indices of all required permission pages
    private val requiredPageIndices: List<Int>
        get() = pages.mapIndexedNotNull { index, page ->
            if (page is OnboardingPage.Permission && page.required) index else null
        }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d(TAG, "Notification permission result: $isGranted")
        updatePermissionStatus()
        updateNextButtonState()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = results[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        Log.d(TAG, "Location permission result: fine=$fineGranted, coarse=$coarseGranted")
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = Repository.getInstance(this)

        // If onboarding is already completed, skip directly to MainActivity
        if (repository.isOnboardingCompleted()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.onboarding_viewpager)
        dotsContainer = findViewById(R.id.onboarding_dots_container)
        btnNext = findViewById(R.id.onboarding_btn_next)
        btnBack = findViewById(R.id.onboarding_btn_back)
        btnSkip = findViewById(R.id.onboarding_skip)

        val adapter = OnboardingAdapter(pages)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 2

        // Disable swiping over-scroll effect for cleaner look
        viewPager.getChildAt(0)?.overScrollMode = View.OVER_SCROLL_NEVER

        setupDots()
        updateUI(0)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateUI(position)
                updatePermissionStatus()
                updateNextButtonState()
            }
        })

        btnNext.setOnClickListener {
            val current = viewPager.currentItem
            // Block if on a required permission page and not granted
            if (isOnRequiredPage(current)) {
                val page = pages[current] as OnboardingPage.Permission
                Toast.makeText(this, "${page.title.replace("\n", " ")} is required to continue", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (current < pages.size - 1) {
                viewPager.setCurrentItem(current + 1, true)
            } else {
                completeOnboarding()
            }
        }

        btnBack.setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) {
                viewPager.setCurrentItem(current - 1, true)
            }
        }

        btnSkip.setOnClickListener {
            completeOnboarding()
        }

        // Disable user-initiated swipe past any required permission page
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val current = viewPager.currentItem
                    // Find the first required page that hasn't been granted yet
                    val firstBlockedIndex = requiredPageIndices.firstOrNull { idx ->
                        val p = pages[idx] as OnboardingPage.Permission
                        return@firstOrNull !isPermissionGranted(p.type)
                    }
                    // If user swiped past a blocked page, snap back
                    if (firstBlockedIndex != null && current > firstBlockedIndex) {
                        viewPager.setCurrentItem(firstBlockedIndex, true)
                        val page = pages[firstBlockedIndex] as OnboardingPage.Permission
                        Toast.makeText(
                            this@OnboardingActivity,
                            "Please grant ${page.title.replace("\n", " ").lowercase()} first",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateNextButtonState()
    }

    private fun isOnRequiredPage(position: Int): Boolean {
        val page = pages[position]
        return page is OnboardingPage.Permission && page.required && !isPermissionGranted(page.type)
    }

    private fun updateNextButtonState() {
        val current = viewPager.currentItem
        val blocked = isOnRequiredPage(current)
        if (blocked) {
            btnNext.alpha = 0.35f
            btnSkip.visibility = View.GONE
        } else {
            btnNext.alpha = 1f
            val isLastPage = current == pages.size - 1
            btnSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE
        }
    }

    private fun setupDots() {
        dotsContainer.removeAllViews()
        for (i in pages.indices) {
            val dot = View(this)
            if (i == 0) {
                val params = LinearLayout.LayoutParams(
                    (24 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt()
                )
                params.setMargins(
                    (4 * resources.displayMetrics.density).toInt(), 0,
                    (4 * resources.displayMetrics.density).toInt(), 0
                )
                dot.layoutParams = params
                dot.setBackgroundResource(R.drawable.bg_onboarding_dot_active)
            } else {
                val params = LinearLayout.LayoutParams(
                    (8 * resources.displayMetrics.density).toInt(),
                    (8 * resources.displayMetrics.density).toInt()
                )
                params.setMargins(
                    (4 * resources.displayMetrics.density).toInt(), 0,
                    (4 * resources.displayMetrics.density).toInt(), 0
                )
                dot.layoutParams = params
                dot.setBackgroundResource(R.drawable.bg_onboarding_dot_inactive)
            }
            dotsContainer.addView(dot)
        }
    }

    private fun updateDots(position: Int) {
        for (i in 0 until dotsContainer.childCount) {
            val dot = dotsContainer.getChildAt(i)
            if (i == position) {
                val params = dot.layoutParams as LinearLayout.LayoutParams
                params.width = (24 * resources.displayMetrics.density).toInt()
                params.height = (10 * resources.displayMetrics.density).toInt()
                dot.layoutParams = params
                dot.setBackgroundResource(R.drawable.bg_onboarding_dot_active)
            } else {
                val params = dot.layoutParams as LinearLayout.LayoutParams
                params.width = (8 * resources.displayMetrics.density).toInt()
                params.height = (8 * resources.displayMetrics.density).toInt()
                dot.layoutParams = params
                dot.setBackgroundResource(R.drawable.bg_onboarding_dot_inactive)
            }
        }
    }

    private fun updateUI(position: Int) {
        val isLastPage = position == pages.size - 1
        val isFirstPage = position == 0

        btnNext.text = if (isLastPage) "Get Started" else "Next"
        btnBack.visibility = if (isFirstPage) View.GONE else View.VISIBLE

        // Adjust weights
        val nextParams = btnNext.layoutParams as LinearLayout.LayoutParams
        val backParams = btnBack.layoutParams as LinearLayout.LayoutParams
        if (isFirstPage) {
            nextParams.weight = 1f
            backParams.weight = 0f
        } else {
            nextParams.weight = 2f
            backParams.weight = 1f
        }
        btnNext.layoutParams = nextParams
        btnBack.layoutParams = backParams

        // Hide skip on last page
        btnSkip.visibility = if (isLastPage) View.GONE else View.VISIBLE
    }

    private fun updatePermissionStatus() {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        for (i in pages.indices) {
            val page = pages[i]
            if (page is OnboardingPage.Permission) {
                val holder = recyclerView.findViewHolderForAdapterPosition(i)
                if (holder is OnboardingAdapter.PermissionViewHolder) {
                    val granted = isPermissionGranted(page.type)
                    holder.updateStatus(granted)
                }
            }
        }
    }

    private fun isPermissionGranted(type: PermissionType): Boolean {
        return when (type) {
            PermissionType.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
            PermissionType.LOCATION -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            PermissionType.BATTERY -> {
                isIgnoringBatteryOptimizations(this)
            }
        }
    }

    private fun requestPermission(type: PermissionType) {
        when (type) {
            PermissionType.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionType.LOCATION -> {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
            PermissionType.BATTERY -> {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (_: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
    }

    private fun sendTestNotification(insistent: Boolean, statusText: TextView) {
        statusText.visibility = View.VISIBLE
        statusText.text = "Firing test alert..."
        statusText.setTextColor(android.graphics.Color.parseColor("#AA84D6C2"))

        // Enable or disable insistent max priority globally for the test
        repository.setInsistentMaxPriorityEnabled(insistent)

        try {
            val baseUrl = getString(R.string.app_base_url)

            // Create a fake subscription for the notification display
            val subscription = Subscription(
                id = 0L,
                baseUrl = baseUrl,
                topic = EMERGENCY_TOPIC,
                instant = true,
                dedicatedChannels = false,
                mutedUntil = 0,
                minPriority = 0,
                autoDelete = Repository.AUTO_DELETE_USE_GLOBAL,
                insistent = if (insistent) Repository.INSISTENT_MAX_PRIORITY_ENABLED else 0,
                lastNotificationId = null,
                icon = null,
                upAppId = null,
                upConnectorToken = null,
                displayName = "Emergency Alerts",
                totalCount = 0,
                newCount = 0,
                lastActive = System.currentTimeMillis() / 1000
            )

            // Create a local notification with max priority
            val message = if (insistent) {
                "This is a test earthquake alert. Keep Alerting is ON — dismiss this notification to stop the alarm."
            } else {
                "This is a test earthquake alert. Keep Alerting is OFF."
            }

            val notification = Notification(
                id = "onboarding-test-${System.currentTimeMillis()}",
                subscriptionId = 0L,
                timestamp = System.currentTimeMillis() / 1000,
                sequenceId = "onboarding-test",
                title = "QuakeAlert Test",
                message = message,
                contentType = "",
                encoding = "",
                notificationId = Random().nextInt(100000),
                priority = PRIORITY_MAX,
                tags = "rotating_light,warning",
                click = "",
                icon = null,
                actions = null,
                attachment = null,
                deleted = false
            )

            // Ensure notification channels exist, then display locally (no server involved)
            notifier.createDefaultNotificationChannels()
            notifier.display(subscription, notification)

            statusText.text = "Test alert fired! Check your notifications."
            statusText.setTextColor(android.graphics.Color.parseColor("#66BB6A"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire test notification", e)
            statusText.text = "Failed: ${e.message}"
            statusText.setTextColor(android.graphics.Color.parseColor("#EF5350"))
        }
    }

    private fun completeOnboarding() {
        repository.setOnboardingCompleted(true)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    // ========= ViewPager Adapter =========

    inner class OnboardingAdapter(
        private val pages: List<OnboardingPage>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return when (pages[position]) {
                is OnboardingPage.Intro -> VIEW_TYPE_INTRO
                is OnboardingPage.Permission -> VIEW_TYPE_PERMISSION
                is OnboardingPage.TestNotification -> VIEW_TYPE_TEST
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_INTRO -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_intro, parent, false)
                    IntroViewHolder(view)
                }
                VIEW_TYPE_PERMISSION -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_permission, parent, false)
                    PermissionViewHolder(view)
                }
                VIEW_TYPE_TEST -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_onboarding_test, parent, false)
                    TestViewHolder(view)
                }
                else -> throw IllegalArgumentException("Unknown view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val page = pages[position]) {
                is OnboardingPage.Intro -> (holder as IntroViewHolder).bind(page)
                is OnboardingPage.Permission -> (holder as PermissionViewHolder).bind(page)
                is OnboardingPage.TestNotification -> (holder as TestViewHolder).bind()
            }
        }

        override fun getItemCount() = pages.size

        inner class IntroViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.onboarding_icon)
            private val title: TextView = view.findViewById(R.id.onboarding_title)
            private val subtitle: TextView = view.findViewById(R.id.onboarding_subtitle)

            fun bind(page: OnboardingPage.Intro) {
                icon.setImageResource(page.iconRes)
                title.text = page.title
                subtitle.text = page.subtitle
            }
        }

        inner class PermissionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val icon: ImageView = view.findViewById(R.id.onboarding_perm_icon)
            private val title: TextView = view.findViewById(R.id.onboarding_perm_title)
            private val desc: TextView = view.findViewById(R.id.onboarding_perm_desc)
            private val badge: TextView = view.findViewById(R.id.onboarding_perm_badge)
            private val actionBtn: TextView = view.findViewById(R.id.onboarding_perm_action_btn)
            private val statusContainer: LinearLayout = view.findViewById(R.id.onboarding_perm_status)
            private val statusText: TextView = view.findViewById(R.id.onboarding_perm_status_text)
            private var permissionType: PermissionType? = null

            fun bind(page: OnboardingPage.Permission) {
                permissionType = page.type
                icon.setImageResource(page.iconRes)
                title.text = page.title
                desc.text = page.description
                actionBtn.text = page.buttonText

                // Show "REQUIRED" or "OPTIONAL" 3D badge
                if (page.required) {
                    badge.text = "REQUIRED"
                    badge.setTextColor(android.graphics.Color.WHITE)
                    badge.setBackgroundResource(R.drawable.bg_badge_3d_red_small)
                } else {
                    badge.text = "OPTIONAL"
                    badge.setTextColor(android.graphics.Color.WHITE)
                    badge.setBackgroundResource(R.drawable.bg_badge_3d_green_small)
                }

                actionBtn.setOnClickListener {
                    requestPermission(page.type)
                }

                updateStatus(isPermissionGranted(page.type))
            }

            fun updateStatus(granted: Boolean) {
                if (granted) {
                    actionBtn.alpha = 1f
                    actionBtn.text = "Already Enabled"
                    actionBtn.setBackgroundResource(R.drawable.bg_pill_3d_green)
                    statusContainer.visibility = View.VISIBLE
                    statusText.text = "Permission granted"
                } else {
                    actionBtn.alpha = 1f
                    actionBtn.setBackgroundResource(R.drawable.bg_onboarding_btn_action_3d)
                    val page = permissionType?.let { type ->
                        pages.firstOrNull { it is OnboardingPage.Permission && (it as OnboardingPage.Permission).type == type }
                    } as? OnboardingPage.Permission
                    actionBtn.text = page?.buttonText ?: "Enable"
                    statusContainer.visibility = View.GONE
                }
            }
        }

        inner class TestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val sendBtn: TextView = view.findViewById(R.id.onboarding_test_send_btn)
            private val insistentToggle: MaterialSwitch = view.findViewById(R.id.onboarding_test_insistent_toggle)
            private val statusText: TextView = view.findViewById(R.id.onboarding_test_status)

            fun bind() {
                insistentToggle.isChecked = true

                sendBtn.setOnClickListener {
                    sendTestNotification(insistentToggle.isChecked, statusText)
                }
            }
        }
    }

    // ========= Data classes =========

    sealed class OnboardingPage {
        data class Intro(
            val iconRes: Int,
            val title: String,
            val subtitle: String
        ) : OnboardingPage()

        data class Permission(
            val iconRes: Int,
            val title: String,
            val description: String,
            val buttonText: String,
            val type: PermissionType,
            val required: Boolean = false
        ) : OnboardingPage()

        data object TestNotification : OnboardingPage()
    }

    enum class PermissionType {
        NOTIFICATION, LOCATION, BATTERY
    }

    companion object {
        const val TAG = "NtfyOnboarding"
        private const val VIEW_TYPE_INTRO = 0
        private const val VIEW_TYPE_PERMISSION = 1
        private const val VIEW_TYPE_TEST = 2
        private const val EMERGENCY_TOPIC = "peringatan_gempa_darurat_xyz"
    }
}
