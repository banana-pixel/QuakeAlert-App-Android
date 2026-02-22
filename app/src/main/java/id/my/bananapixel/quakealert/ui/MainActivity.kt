package id.my.bananapixel.quakealert.ui

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import id.my.bananapixel.quakealert.BuildConfig
import id.my.bananapixel.quakealert.R
import id.my.bananapixel.quakealert.app.Application
import id.my.bananapixel.quakealert.db.Repository
import id.my.bananapixel.quakealert.db.Subscription
import id.my.bananapixel.quakealert.firebase.FirebaseMessenger
import id.my.bananapixel.quakealert.msg.ApiService
import id.my.bananapixel.quakealert.msg.DownloadManager
import id.my.bananapixel.quakealert.msg.DownloadType
import id.my.bananapixel.quakealert.msg.NotificationDispatcher
import id.my.bananapixel.quakealert.msg.Poller
import id.my.bananapixel.quakealert.service.SubscriberService
import id.my.bananapixel.quakealert.service.SubscriberServiceManager
import id.my.bananapixel.quakealert.ui.delegates.MainActionModeDelegate
import id.my.bananapixel.quakealert.ui.delegates.MainBannersDelegate
import id.my.bananapixel.quakealert.ui.delegates.MainConnectionStatusDelegate
import id.my.bananapixel.quakealert.ui.delegates.MainKeyboardDelegate
import id.my.bananapixel.quakealert.ui.delegates.MainNavigationDelegate
import id.my.bananapixel.quakealert.domain.IntentActions
import id.my.bananapixel.quakealert.util.Log
import id.my.bananapixel.quakealert.util.displayName
import id.my.bananapixel.quakealert.util.formatDateShort
import id.my.bananapixel.quakealert.util.isDarkThemeOn
import id.my.bananapixel.quakealert.util.maybeSplitTopicUrl
import id.my.bananapixel.quakealert.util.randomSubscriptionId
import id.my.bananapixel.quakealert.util.shortUrl
import id.my.bananapixel.quakealert.util.topicShortUrl
import id.my.bananapixel.quakealert.work.DeleteWorker
import id.my.bananapixel.quakealert.work.PollWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import java.util.concurrent.TimeUnit
import androidx.core.view.size
import androidx.core.view.get
import androidx.core.net.toUri
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.activity.OnBackPressedCallback
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.activity.viewModels
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent

class MainActivity : AppCompatActivity(), AddFragment.SubscribeListener, NotificationFragment.NotificationSettingsListener, KoinComponent {
    private val viewModel: SubscriptionsViewModel by viewModels { SubscriptionsViewModelFactory(repository) }
    private val repository: Repository by inject()
    private val api by lazy { ApiService(this) }
    private val poller by lazy { Poller(api, repository) }
    private val messenger = FirebaseMessenger()

    // UI elements
    private lateinit var menu: Menu
    private lateinit var mainList: RecyclerView
    private lateinit var mainListContainer: SwipeRefreshLayout
    private lateinit var adapter: MainAdapter
    private lateinit var fab: FloatingActionButton

    // Other stuff
    private var workManager: WorkManager? = null
    private var dispatcher: NotificationDispatcher? = null
    private var appBaseUrl: String? = null

    // Delegates (navigation, banners, keyboard, connection status, action mode)
    private lateinit var navigationDelegate: MainNavigationDelegate
    private lateinit var bannersDelegate: MainBannersDelegate
    private lateinit var keyboardDelegate: MainKeyboardDelegate
    private lateinit var connectionStatusDelegate: MainConnectionStatusDelegate
    private lateinit var actionModeDelegate: MainActionModeDelegate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_main)
        supportFragmentManager.executePendingTransactions()

        Log.init(this) // Init logs in all entry points
        Log.d(TAG, "Create $this")

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.main_nav_host) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        // Custom nav setup: MainNavigationDelegate uses setPopUpTo to prevent TransactionTooLargeException.
        // See MainNavigationDelegate class KDoc for rationale.
        navigationDelegate = MainNavigationDelegate(this)
        navigationDelegate.setupBottomNavClearBackStack(navController, bottomNav)
        navigationDelegate.addDestinationChangedListener(navController, bottomNav) {
            navigationDelegate.applyBottomInset(bottomNav)
        }

        keyboardDelegate = MainKeyboardDelegate(this, navigationDelegate)
        keyboardDelegate.setupKeyboardListener(bottomNav, navHostFragment, window.decorView.rootView)

        workManager = WorkManager.getInstance(this)
        dispatcher = NotificationDispatcher(this, repository)
        appBaseUrl = BuildConfig.APP_BASE_URL
        connectionStatusDelegate = MainConnectionStatusDelegate(this, repository)
        bannersDelegate = MainBannersDelegate(this, repository, appBaseUrl)

        // Action bar
        val toolbarLayout = findViewById<AppBarLayout>(R.id.app_bar_drawer)
        val dynamicColors = repository.getDynamicColorsEnabled()
        val darkMode = isDarkThemeOn(this)
        val statusBarColor = Colors.statusBarNormal(this, dynamicColors, darkMode)
        val toolbarTextColor = Colors.toolbarTextColor(this, dynamicColors, darkMode)
        toolbarLayout.setBackgroundColor(statusBarColor)

        val toolbar = toolbarLayout.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setTitleTextColor(toolbarTextColor)
        toolbar.setNavigationIconTint(toolbarTextColor)
        toolbar.overflowIcon?.setTint(toolbarTextColor)

        setSupportActionBar(toolbar)

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_history,
                R.id.nav_sensors,
                R.id.nav_warning,
                R.id.nav_chat,
                R.id.nav_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Set system status bar appearance
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            Colors.shouldUseLightStatusBar(dynamicColors, darkMode)

        // Floating action button ("+")
        fab = findViewById(R.id.fab)
        fab.setOnClickListener {
            onSubscribeButtonClick()
        }

        // Handle WindowInsets for FAB - Symmetrical 24dp margins on right and bottom
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

            val marginPx = (24 * resources.displayMetrics.density).toInt()

            layoutParams.marginEnd = marginPx
            layoutParams.bottomMargin = systemBars.bottom + marginPx

            view.layoutParams = layoutParams
            insets
        }

        // Swipe to refresh
        mainListContainer = findViewById(R.id.main_subscriptions_list_container)
        mainListContainer.setOnRefreshListener { refreshAllSubscriptions() }
        mainListContainer.setColorSchemeColors(Colors.swipeToRefreshColor(this))

        // Update main list based on viewModel (& its datasource/livedata)
        val noEntries: View = findViewById(R.id.main_no_subscriptions)
        val onSubscriptionClick = { s: Subscription -> onSubscriptionItemClick(s) }
        val onSubscriptionLongClick = { s: Subscription -> onSubscriptionItemLongClick(s) }

        mainList = findViewById(R.id.main_subscriptions_list)
        adapter = MainAdapter(
            repository,
            onSubscriptionClick,
            onSubscriptionLongClick,
            ResourcesCompat.getDrawable(resources, R.drawable.ic_circle, theme)!!.apply {
                setTint(Colors.primary(this@MainActivity))
            },
            Colors.onPrimary(this)
        )
        mainList.adapter = adapter
        actionModeDelegate = MainActionModeDelegate(this, adapter, fab, viewModel) { redrawList() }

        // Apply window insets to ensure content is not covered by navigation bar
        mainList.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(mainList) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = systemBars.bottom)
            insets
        }

        viewModel.list().observe(this) {
            it?.let { subscriptions ->
                // Update main list
                adapter.submitList(subscriptions as MutableList<Subscription>)
                if (it.isEmpty()) {
                    mainListContainer.visibility = View.GONE
                    noEntries.visibility = View.VISIBLE
                } else {
                    mainListContainer.visibility = View.VISIBLE
                    noEntries.visibility = View.GONE
                }

                // Add scrub terms to log (in case it gets exported)
                subscriptions.forEach { s ->
                    Log.addScrubTerm(shortUrl(s.baseUrl), Log.TermType.Domain)
                    Log.addScrubTerm(s.topic)
                }

                bannersDelegate.showHideBatteryBanner(subscriptions)
                bannersDelegate.showHideWebSocketBanner(subscriptions)
                bannersDelegate.showHideWebSocketReconnectBanner()
            }
        }

        // Add scrub terms to log (in case it gets exported) // FIXME this should be in Log.getFormatted
        repository.getUsersLiveData().observe(this) {
            it?.let { users ->
                users.forEach { u ->
                    Log.addScrubTerm(shortUrl(u.baseUrl), Log.TermType.Domain)
                    Log.addScrubTerm(u.username, Log.TermType.Username)
                    Log.addScrubTerm(u.password, Log.TermType.Password)
                }
            }
        }

        // Scrub terms for last topics // FIXME this should be in Log.getFormatted
        repository.getLastShareTopics().forEach { topicUrl ->
            maybeSplitTopicUrl(topicUrl)?.let {
                Log.addScrubTerm(shortUrl(it.first), Log.TermType.Domain)
                Log.addScrubTerm(shortUrl(it.second), Log.TermType.Term)
            }
        }

        // React to changes in instant delivery setting
        viewModel.listIdsWithInstantStatus().observe(this) {
            SubscriberServiceManager.refresh(this)
        }

        connectionStatusDelegate.observeConnectionDetails(this)

        bannersDelegate.setupClickListeners { bannersDelegate.showHideWebSocketReconnectBanner() }

        // Hide links that lead to payments, see https://github.com/binwiederhier/ntfy/issues/1463
        val howToLink = findViewById<TextView>(R.id.main_how_to_link)
        howToLink.isVisible = BuildConfig.PAYMENT_LINKS_AVAILABLE

        // Create notification channels right away, so we can configure them immediately after installing the app
        dispatcher?.init()

        // Subscribe to control Firebase channel (so we can re-start the foreground service if it dies)
        messenger.subscribe(ApiService.CONTROL_TOPIC)

        // Darrkkkk mode
        AppCompatDelegate.setDefaultNightMode(repository.getDarkMode())

        // Background things
        schedulePeriodicPollWorker()
        schedulePeriodicServiceRestartWorker()
        schedulePeriodicDeleteWorker()

        // Permissions (notification permission is now handled in OnboardingActivity)
        maybeRequestNotificationPermission()

        // Handle intent only once at the end of onCreate
        handleIntent(intent)

        //Yeahhh

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                    val currentDestination = navController.currentDestination

                    // If there is something to pop inside current tab → pop it
                    if (navController.previousBackStackEntry != null &&
                        currentDestination?.id != navController.graph.startDestinationId
                    ) {
                        navController.popBackStack()
                        return
                    }

                    // Otherwise exit app
                    finishAffinity()
                }
            }
        )





    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == IntentActions.OPEN_WARNING_PAGE -> {
                Log.d(TAG, "Intercepted ${IntentActions.OPEN_WARNING_PAGE} action")

                val message = intent.getStringExtra("message") ?: ""
                val distance = intent.getStringExtra("distance") ?: ""

                // 1. Activate the Red UI state
                id.my.bananapixel.quakealert.app.AlertState.setActive(true)
                id.my.bananapixel.quakealert.app.AlertState.setAlertFromRaw(message, distance, System.currentTimeMillis() / 1000)

                // 2. Switch to the Warning Fragment via Navigation graph
                val navController = (supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment)?.navController ?: return
                val navOptions = NavOptions.Builder()
                    .setPopUpTo(navController.graph.id, true)
                    .build()
                navController.navigate(R.id.nav_warning, null, navOptions)
            }
            intent?.data != null -> {
                // Handle URI deep links (e.g. quakealert://warning)
                (supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment)?.navController?.handleDeepLink(intent)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        // Android 13 (SDK 33) requires that we ask for permission to post notifications
        // https://developer.android.com/develop/ui/views/notifications/notification-permission

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    override fun onDestroy() {
        if (::connectionStatusDelegate.isInitialized) connectionStatusDelegate.clear()
        if (::actionModeDelegate.isInitialized) actionModeDelegate.cleanup()
        if (::keyboardDelegate.isInitialized) keyboardDelegate.cleanup()
        if (::navigationDelegate.isInitialized) navigationDelegate.cleanup()
        super.onDestroy()
    }

    override fun onPause() {
        // Trim nav back stack before going to background to avoid TransactionTooLargeException
        // when saved state exceeds Binder limit (e.g. after rapid tab switching, then opening Settings)
        if (::navigationDelegate.isInitialized) navigationDelegate.trimBackStackBeforeBackground()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        showHideNotificationMenuItems()
        connectionStatusDelegate.showHideConnectionErrorMenuItem(repository.getConnectionDetails())
        redrawList()
    }

    private fun schedulePeriodicPollWorker() {
        val workerVersion = repository.getPollWorkerVersion()
        val workPolicy = if (workerVersion == PollWorker.VERSION) {
            Log.d(TAG, "Poll worker version matches: choosing KEEP as existing work policy")
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            Log.d(TAG, "Poll worker version DOES NOT MATCH: choosing REPLACE as existing work policy")
            repository.setPollWorkerVersion(PollWorker.VERSION)
            ExistingPeriodicWorkPolicy.REPLACE
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val work = PeriodicWorkRequestBuilder<PollWorker>(POLL_WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(PollWorker.TAG)
            .addTag(PollWorker.WORK_NAME_PERIODIC_ALL)
            .build()
        Log.d(TAG, "Poll worker: Scheduling period work every $POLL_WORKER_INTERVAL_MINUTES minutes")
        workManager!!.enqueueUniquePeriodicWork(PollWorker.WORK_NAME_PERIODIC_ALL, workPolicy, work)
    }

    private fun schedulePeriodicDeleteWorker() {
        val workerVersion = repository.getDeleteWorkerVersion()
        val workPolicy = if (workerVersion == DeleteWorker.VERSION) {
            Log.d(TAG, "Delete worker version matches: choosing KEEP as existing work policy")
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            Log.d(TAG, "Delete worker version DOES NOT MATCH: choosing REPLACE as existing work policy")
            repository.setDeleteWorkerVersion(DeleteWorker.VERSION)
            ExistingPeriodicWorkPolicy.REPLACE
        }
        val work = PeriodicWorkRequestBuilder<DeleteWorker>(DELETE_WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .addTag(DeleteWorker.TAG)
            .addTag(DeleteWorker.WORK_NAME_PERIODIC_ALL)
            .build()
        Log.d(TAG, "Delete worker: Scheduling period work every $DELETE_WORKER_INTERVAL_MINUTES minutes")
        workManager!!.enqueueUniquePeriodicWork(DeleteWorker.WORK_NAME_PERIODIC_ALL, workPolicy, work)
    }

    private fun schedulePeriodicServiceRestartWorker() {
        val workerVersion = repository.getAutoRestartWorkerVersion()
        val workPolicy = if (workerVersion == SubscriberService.SERVICE_START_WORKER_VERSION) {
            Log.d(TAG, "ServiceStartWorker version matches: choosing KEEP as existing work policy")
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            Log.d(TAG, "ServiceStartWorker version DOES NOT MATCH: choosing REPLACE as existing work policy")
            repository.setAutoRestartWorkerVersion(SubscriberService.SERVICE_START_WORKER_VERSION)
            ExistingPeriodicWorkPolicy.REPLACE
        }
        val work = PeriodicWorkRequestBuilder<SubscriberServiceManager.ServiceStartWorker>(SERVICE_START_WORKER_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .addTag(SubscriberService.TAG)
            .addTag(SubscriberService.SERVICE_START_WORKER_WORK_NAME_PERIODIC)
            .build()
        Log.d(TAG, "ServiceStartWorker: Scheduling period work every $SERVICE_START_WORKER_INTERVAL_MINUTES minutes")
        workManager?.enqueueUniquePeriodicWork(SubscriberService.SERVICE_START_WORKER_WORK_NAME_PERIODIC, workPolicy, work)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main_action_bar, menu)
        this.menu = menu
        connectionStatusDelegate.setMenu(menu)

        val toolbarTextColor = Colors.toolbarTextColor(this, repository.getDynamicColorsEnabled(), isDarkThemeOn(this))
        for (i in 0 until menu.size) {
            menu[i].icon?.setTint(toolbarTextColor)
        }

        showHideNotificationMenuItems()
        connectionStatusDelegate.showHideConnectionErrorMenuItem(repository.getConnectionDetails())
        checkSubscriptionsMuted()
        return true
    }

    private fun checkSubscriptionsMuted(delayMillis: Long = 0L) {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(delayMillis) // Just to be sure we've initialized all the things, we wait a bit ...
            Log.d(TAG, "Checking global and subscription-specific 'muted until' timestamp")

            // Check global
            val changed = repository.checkGlobalMutedUntil()
            if (changed) {
                Log.d(TAG, "Global muted until timestamp expired; updating prefs")
                showHideNotificationMenuItems()
            }

            // Check subscriptions
            var rerenderList = false
            repository.getSubscriptions().forEach { subscription ->
                val mutedUntilExpired = subscription.mutedUntil > 1L && System.currentTimeMillis()/1000 > subscription.mutedUntil
                if (mutedUntilExpired) {
                    Log.d(TAG, "Subscription ${subscription.id}: Muted until timestamp expired, updating subscription")
                    val newSubscription = subscription.copy(mutedUntil = 0L)
                    repository.updateSubscription(newSubscription)
                    rerenderList = true
                }
            }
            if (rerenderList) {
                mainList.post {
                    redrawList()
                }
            }
        }
    }

    private fun showHideNotificationMenuItems() {
        if (!this::menu.isInitialized) {
            return
        }
        val mutedUntilSeconds = repository.getGlobalMutedUntil()
        runOnUiThread {
            // Show/hide menu items based on build config
            val rateAppItem = menu.findItem(R.id.main_menu_rate)
            val docsItem = menu.findItem(R.id.main_menu_docs)
            val reportBugItem = menu.findItem(R.id.main_menu_report_bug)
            rateAppItem.isVisible = BuildConfig.RATE_APP_AVAILABLE
            docsItem.isVisible = BuildConfig.PAYMENT_LINKS_AVAILABLE // Google Payments Policy, see https://github.com/binwiederhier/ntfy/issues/1463
            reportBugItem.isVisible = BuildConfig.PAYMENT_LINKS_AVAILABLE // Google Payments Policy, see https://github.com/binwiederhier/ntfy/issues/1463

            // Pause notification icons
            val notificationsEnabledItem = menu.findItem(R.id.main_menu_notifications_enabled)
            val notificationsDisabledUntilItem = menu.findItem(R.id.main_menu_notifications_disabled_until)
            val notificationsDisabledForeverItem = menu.findItem(R.id.main_menu_notifications_disabled_forever)
            notificationsEnabledItem?.isVisible = mutedUntilSeconds == 0L
            notificationsDisabledForeverItem?.isVisible = mutedUntilSeconds == 1L
            notificationsDisabledUntilItem?.isVisible = mutedUntilSeconds > 1L
            if (mutedUntilSeconds > 1L) {
                val formattedDate = formatDateShort(mutedUntilSeconds)
                notificationsDisabledUntilItem?.title = getString(R.string.main_menu_notifications_disabled_until, formattedDate)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.main_menu_notifications_enabled -> {
                onNotificationSettingsClick(enable = false)
                true
            }
            R.id.main_menu_notifications_disabled_forever -> {
                onNotificationSettingsClick(enable = true)
                true
            }
            R.id.main_menu_notifications_disabled_until -> {
                onNotificationSettingsClick(enable = true)
                true
            }
            R.id.main_menu_connection_error -> {
                onConnectionErrorClick()
                true
            }
            R.id.main_menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.main_menu_report_bug -> {
                startActivity(
                    Intent(Intent.ACTION_VIEW, getString(R.string.main_menu_report_bug_url).toUri())
                )
                true
            }
            R.id.main_menu_rate -> {
                try {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
                    )
                } catch (_: ActivityNotFoundException) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri())
                    )
                }
                true
            }
            R.id.main_menu_docs -> {
                startActivity(
                    Intent(Intent.ACTION_VIEW, getString(R.string.main_menu_docs_url).toUri())
                )
                true
            }
            R.id.main_menu_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onNotificationSettingsClick(enable: Boolean) {
        if (!enable) {
            Log.d(TAG, "Showing global notification settings dialog")
            (supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment)?.navController?.navigate(
                R.id.nav_dialog_notification, null,
                NavOptions.Builder().setLaunchSingleTop(true).build()
            )
        } else {
            Log.d(TAG, "Re-enabling global notifications")
            onNotificationMutedUntilChanged(Repository.MUTED_UNTIL_SHOW_ALL)
        }
    }

    private fun onConnectionErrorClick() {
        Log.d(TAG, "Showing connection error dialog")
        (supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment)?.navController?.navigate(
            R.id.nav_dialog_connection_error, null,
            NavOptions.Builder().setLaunchSingleTop(true).build()
        )
    }

    override fun onNotificationMutedUntilChanged(mutedUntilTimestamp: Long) {
        repository.setGlobalMutedUntil(mutedUntilTimestamp)
        showHideNotificationMenuItems()
        runOnUiThread {
            redrawList() // Update the "muted until" icons
            when (mutedUntilTimestamp) {
                0L -> Toast.makeText(this@MainActivity, getString(R.string.notification_dialog_enabled_toast_message), Toast.LENGTH_LONG).show()
                1L -> Toast.makeText(this@MainActivity, getString(R.string.notification_dialog_muted_forever_toast_message), Toast.LENGTH_LONG).show()
                else -> {
                    val formattedDate = formatDateShort(mutedUntilTimestamp)
                    Toast.makeText(this@MainActivity, getString(R.string.notification_dialog_muted_until_toast_message, formattedDate), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onSubscribeButtonClick() {
        (supportFragmentManager.findFragmentById(R.id.main_nav_host) as? NavHostFragment)?.navController?.navigate(
            R.id.nav_dialog_add, null,
            NavOptions.Builder().setLaunchSingleTop(true).build()
        )
    }

    override fun onSubscribe(topic: String, baseUrl: String, instant: Boolean) {
        Log.d(TAG, "Adding subscription ${topicShortUrl(baseUrl, topic)} (instant = $instant)")

        // Add subscription to database
        val subscription = Subscription(
            id = randomSubscriptionId(),
            baseUrl = baseUrl,
            topic = topic,
            instant = instant,
            dedicatedChannels = false,
            mutedUntil = 0,
            minPriority = Repository.MIN_PRIORITY_USE_GLOBAL,
            autoDelete = Repository.AUTO_DELETE_USE_GLOBAL,
            insistent = Repository.INSISTENT_MAX_PRIORITY_USE_GLOBAL,
            lastNotificationId = null,
            icon = null,
            upAppId = null,
            upConnectorToken = null,
            displayName = null,
            totalCount = 0,
            newCount = 0,
            lastActive = Date().time/1000
        )
        viewModel.add(subscription)

        // Subscribe to Firebase topic if ntfy.sh (even if instant, just to be sure!)
        if (baseUrl == appBaseUrl) {
            Log.d(TAG, "Subscribing to Firebase topic $topic")
            messenger.subscribe(topic)
        }

        // Fetch cached messages
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val notifications = poller.poll(subscription)
                notifications.forEach { notification ->
                    if (notification.icon != null) {
                        DownloadManager.enqueue(this@MainActivity, notification.id, userAction = false, DownloadType.ICON)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to fetch notifications: ${e.message}", e)
            }
        }

        // Switch to detail view after adding it
        onSubscriptionItemClick(subscription)
    }

    private fun onSubscriptionItemClick(subscription: Subscription) {
        if (actionModeDelegate.isActive()) {
            actionModeDelegate.handleItemClick(subscription)
        } else if (subscription.upAppId != null) {
            startDetailSettingsView(subscription)
        } else {
            startDetailView(subscription)
        }
    }

    private fun onSubscriptionItemLongClick(subscription: Subscription) {
        if (!actionModeDelegate.isActive()) {
            actionModeDelegate.startActionMode(subscription)
        }
    }

    private fun refreshAllSubscriptions() {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Polling for new notifications")
            var errors = 0
            var errorMessage = "" // First error
            var newNotificationsCount = 0
            repository.getSubscriptions().forEach { subscription ->
                Log.d(TAG, "Polling subscription: $subscription")
                try {
                    val newNotifications = poller.poll(subscription)
                    newNotificationsCount += newNotifications.size
                    newNotifications.forEach { notification ->
                        dispatcher?.dispatch(subscription, notification)
                    }
                } catch (e: Exception) {
                    val topic = displayName(appBaseUrl, subscription)
                    if (errorMessage == "") errorMessage = "$topic: ${e.message}"
                    errors++
                }
            }
            val toastMessage = if (errors > 0) {
                getString(R.string.refresh_message_error, errors, errorMessage)
            } else if (newNotificationsCount == 0) {
                getString(R.string.refresh_message_no_results)
            } else {
                getString(R.string.refresh_message_result, newNotificationsCount)
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, toastMessage, Toast.LENGTH_LONG).show()
                mainListContainer.isRefreshing = false
            }
            Log.d(TAG, "Finished polling for new notifications")
        }
    }

    private fun startDetailView(subscription: Subscription) {
        Log.d(TAG, "Entering detail view for subscription $subscription")

        val intent = Intent(this, DetailActivity::class.java)
        intent.putExtra(EXTRA_SUBSCRIPTION_ID, subscription.id)
        intent.putExtra(EXTRA_SUBSCRIPTION_BASE_URL, subscription.baseUrl)
        intent.putExtra(EXTRA_SUBSCRIPTION_TOPIC, subscription.topic)
        intent.putExtra(EXTRA_SUBSCRIPTION_DISPLAY_NAME, displayName(appBaseUrl, subscription))
        intent.putExtra(EXTRA_SUBSCRIPTION_INSTANT, subscription.instant)
        intent.putExtra(EXTRA_SUBSCRIPTION_MUTED_UNTIL, subscription.mutedUntil)
        startActivity(intent)
    }

    private fun startDetailSettingsView(subscription: Subscription) {
        Log.d(TAG, "Opening subscription settings for ${topicShortUrl(subscription.baseUrl, subscription.topic)}")

        val intent = Intent(this, DetailSettingsActivity::class.java)
        intent.putExtra(DetailActivity.EXTRA_SUBSCRIPTION_ID, subscription.id)
        intent.putExtra(DetailActivity.EXTRA_SUBSCRIPTION_BASE_URL, subscription.baseUrl)
        intent.putExtra(DetailActivity.EXTRA_SUBSCRIPTION_TOPIC, subscription.topic)
        intent.putExtra(DetailActivity.EXTRA_SUBSCRIPTION_DISPLAY_NAME, displayName(appBaseUrl, subscription))
        startActivity(intent)
    }

    private fun redrawList() {
        if (!this::mainList.isInitialized) {
            return
        }
        adapter.notifyItemRangeChanged(0, adapter.currentList.size)
    }

    companion object {
        const val TAG = "NtfyMainActivity"
        const val EXTRA_SUBSCRIPTION_ID = "subscriptionId"
        const val EXTRA_SUBSCRIPTION_BASE_URL = "subscriptionBaseUrl"
        const val EXTRA_SUBSCRIPTION_TOPIC = "subscriptionTopic"
        const val EXTRA_SUBSCRIPTION_DISPLAY_NAME = "subscriptionDisplayName"
        const val EXTRA_SUBSCRIPTION_INSTANT = "subscriptionInstant"
        const val EXTRA_SUBSCRIPTION_MUTED_UNTIL = "subscriptionMutedUntil"
        const val ANIMATION_DURATION = 80L
        const val ONE_DAY_MILLIS = 86400000L

        // As per documentation: The minimum repeat interval that can be defined is 15 minutes
        // (same as the JobScheduler API), but in practice 15 doesn't work. Using 16 here.
        // Thanks to varunon9 (https://gist.github.com/varunon9/f2beec0a743c96708eb0ef971a9ff9cd) for this!

        const val POLL_WORKER_INTERVAL_MINUTES = 60L
        const val DELETE_WORKER_INTERVAL_MINUTES = 8 * 60L
        const val SERVICE_START_WORKER_INTERVAL_MINUTES = 3 * 60L
    }
}