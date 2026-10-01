package com.praveen.siriai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.WindowCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsAnimationCompat

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var chatRootLayout: RelativeLayout
    private lateinit var mainBackgroundImage: ImageView
    private lateinit var menuBtn: ImageView
    private lateinit var chatWebView: WebView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var voiceBtn: ImageView
    
    // Layout variables for Welcome Screen
    private lateinit var welcomeLayout: RelativeLayout
    private lateinit var welcomeTopLayout: LinearLayout
    private lateinit var welcomeBottomLayout: LinearLayout

    private lateinit var settingsLayout: LinearLayout
    private lateinit var settingsBackBtn: ImageView
    private lateinit var settingsProfileAvatar: ImageView
    private lateinit var settingsProfileName: TextView
    private lateinit var menuLogout: TextView

    private lateinit var loginLayout: LinearLayout
    private lateinit var loginBackBtn: ImageView
    private lateinit var googleLoginBtn: LinearLayout

    private lateinit var menuSearchBtn: ImageView
    private lateinit var menuNewChat: LinearLayout
    private lateinit var historyListContainer: LinearLayout
    private lateinit var menuSettingsLoggedOut: LinearLayout
    private lateinit var menuLogin: LinearLayout
    private lateinit var menuProfile: LinearLayout
    private lateinit var ivProfileAvatar: ImageView
    private lateinit var tvProfileName: TextView

    private lateinit var settingsAppLanguage: TextView
    private lateinit var settingsTermsPrivacy: TextView
    private lateinit var settingsFeedbackSupport: TextView
    private lateinit var settingsAbout: TextView

    private lateinit var guestSettingsLayout: LinearLayout
    private lateinit var guestSettingsBackBtn: ImageView
    private lateinit var guestSettingsAppLanguage: TextView
    private lateinit var guestSettingsTermsPrivacy: TextView
    private lateinit var guestSettingsAbout: TextView
    private lateinit var guestSettingsLoginBtn: LinearLayout

    private lateinit var languageLayout: LinearLayout
    private lateinit var languageBackBtn: ImageView
    private lateinit var aboutLayout: LinearLayout
    private lateinit var aboutBackBtn: ImageView
    private lateinit var termsPrivacyLayout: LinearLayout
    private lateinit var termsPrivacyBackBtn: ImageView
    private lateinit var feedbackSupportLayout: LinearLayout
    private lateinit var feedbackSupportBackBtn: ImageView

    private lateinit var rgLanguage: RadioGroup
    private lateinit var rbLangEnglish: RadioButton
    private lateinit var rbLangTelugu: RadioButton

    private lateinit var topFadeView: View
    private lateinit var bottomInputLayout: LinearLayout

    private lateinit var sharedPrefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var speechRecognizer: SpeechRecognizer
    private val REQUEST_CODE_PERMISSION = 200

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val WEB_CLIENT_ID = "1092066177158-v1f0l4rtflivigr78eksn5mmm399hg93.apps.googleusercontent.com"

    private lateinit var chatRepository: ChatRepository
    private var currentChatId: String? = null
    private var currentMessageId = 0
    
    private val chatHistoryForAI = mutableListOf<Pair<String, Boolean>>()
    private var userPreferences: Map<String, Any>? = null
    
    private var originalLiveIcon: Drawable? = null

    // Forcing Dark Mode true
    private val isDarkMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        val appBgColor = Color.parseColor("#121212")
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.TRANSPARENT 
        window.navigationBarColor = appBgColor

        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.decorView.systemUiVisibility = flags

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val logFile = File(getExternalFilesDir(null), "crash_log.txt")
                logFile.writeText(sw.toString())
            } catch (ignored: Exception) {}
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        sharedPrefs = getSharedPreferences("SiriAppPrefs", Context.MODE_PRIVATE)
        val savedLang = sharedPrefs.getString("app_lang", "en") ?: "en"

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != savedLang) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        findViewById<TextView>(R.id.topTitleText)?.text = "Nakshatra AI"
        findViewById<TextView>(R.id.menuTitleText)?.text = "Nakshatra AI"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this) && !sharedPrefs.getBoolean("overlay_asked", false)) {
                sharedPrefs.edit().putBoolean("overlay_asked", true).apply()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, 1234)
            }
        }

        Thread { writeActualSigningSha1ToFile() }.start()
        handler.post { TTSHelper.init(this) }

        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setProjectId("siri-ai-455a6")
                .setApplicationId("1:1092066177158:android:977705bc14288f720ba107")
                .setApiKey("AIzaSyAGZpX1zjXkVaho_MU7gErF8Jsd3CHto00")
                .setStorageBucket("siri-ai-455a6.firebasestorage.app")
                .build()
            FirebaseApp.initializeApp(this, options)
        }

        firebaseAuth = FirebaseAuth.getInstance()
        chatRepository = ChatRepository(firebaseAuth, FirebaseFirestore.getInstance())

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        try {
            drawerLayout = findViewById(R.id.drawerLayout)
            chatRootLayout = findViewById(R.id.chatRootLayout)
            mainBackgroundImage = findViewById(R.id.mainBackgroundImage)

            val rootViewFrame = window.decorView.findViewById<ViewGroup>(android.R.id.content)
            rootViewFrame?.layoutTransition = null
            val appRoot = drawerLayout.parent as? ViewGroup
            appRoot?.layoutTransition = null

            menuBtn = findViewById(R.id.menuBtn)
            chatWebView = findViewById(R.id.chatWebView)
            chatWebView.visibility = View.INVISIBLE
            handler.postDelayed({ chatWebView.visibility = View.VISIBLE }, 3000)
            
            messageInput = findViewById(R.id.messageInput)
            messageInput.hint = "Ask Nakshatra AI..."
            
            sendBtn = findViewById(R.id.sendBtn)
            
            originalLiveIcon = sendBtn.drawable 
            
            voiceBtn = findViewById(R.id.voiceBtn)
            topFadeView = findViewById(R.id.topFadeView)
            bottomInputLayout = findViewById(R.id.bottomInputLayout)

            chatWebView.post { setupChatWebView() }

            val solidColor = Color.parseColor("#121212")
            val transColor = solidColor and 0x00FFFFFF 

            val topGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(solidColor, transColor)
            )
            topFadeView.background = topGradient

            val bottomGradient = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(transColor, solidColor, solidColor)
            )
            bottomInputLayout.background = bottomGradient

            val inputBoxContainer = messageInput.parent as LinearLayout
            val boxShape = GradientDrawable()
            boxShape.shape = GradientDrawable.RECTANGLE
            boxShape.cornerRadius = 60f 
            boxShape.setColor(Color.parseColor("#1E1E1E"))
            inputBoxContainer.background = boxShape
            inputBoxContainer.backgroundTintList = null 

            sendBtn.scaleX = 1.3f
            sendBtn.scaleY = 1.3f
            voiceBtn.scaleX = 1.3f
            voiceBtn.scaleY = 1.3f

            val iconTint = Color.WHITE
            sendBtn.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
            voiceBtn.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)
            menuBtn.setColorFilter(iconTint, PorterDuff.Mode.SRC_IN)

            welcomeLayout = findViewById(R.id.welcomeLayout)
            welcomeTopLayout = findViewById(R.id.welcomeTopLayout)
            welcomeBottomLayout = findViewById(R.id.welcomeBottomLayout)

            // Setup Suggestion Chips Listeners (Explain removed)
            findViewById<LinearLayout>(R.id.chipWrite).setOnClickListener { populateInputBox("Write a ") }
            findViewById<LinearLayout>(R.id.chipSolve).setOnClickListener { populateInputBox("Solve this problem: ") }
            findViewById<LinearLayout>(R.id.chipIdeas).setOnClickListener { populateInputBox("Give me ideas for ") }

            settingsLayout = findViewById(R.id.settingsLayout)
            settingsBackBtn = findViewById(R.id.settingsBackBtn)
            settingsProfileAvatar = findViewById(R.id.settingsProfileAvatar)
            settingsProfileName = findViewById(R.id.settingsProfileName)
            menuLogout = findViewById(R.id.menuLogout)

            loginLayout = findViewById(R.id.loginLayout)
            loginBackBtn = findViewById(R.id.loginBackBtn)
            googleLoginBtn = findViewById(R.id.googleLoginBtn)

            menuSearchBtn = findViewById(R.id.menuSearchBtn)
            menuNewChat = findViewById(R.id.menuNewChat)
            historyListContainer = findViewById(R.id.historyListContainer)
            menuSettingsLoggedOut = findViewById(R.id.menuSettingsLoggedOut)
            menuLogin = findViewById(R.id.menuLogin)
            menuProfile = findViewById(R.id.menuProfile)
            ivProfileAvatar = findViewById(R.id.ivProfileAvatar)
            tvProfileName = findViewById(R.id.tvProfileName)

            settingsAppLanguage = findViewById(R.id.settingsAppLanguage)
            settingsTermsPrivacy = findViewById(R.id.settingsTermsPrivacy)
            settingsFeedbackSupport = findViewById(R.id.settingsFeedbackSupport)
            settingsAbout = findViewById(R.id.settingsAbout)

            guestSettingsLayout = findViewById(R.id.guestSettingsLayout)
            guestSettingsBackBtn = findViewById(R.id.guestSettingsBackBtn)
            guestSettingsAppLanguage = findViewById(R.id.guestSettingsAppLanguage)
            guestSettingsTermsPrivacy = findViewById(R.id.guestSettingsTermsPrivacy)
            guestSettingsAbout = findViewById(R.id.guestSettingsAbout)
            guestSettingsLoginBtn = findViewById(R.id.guestSettingsLoginBtn)

            languageLayout = findViewById(R.id.languageLayout)
            languageBackBtn = findViewById(R.id.languageBackBtn)
            aboutLayout = findViewById(R.id.aboutLayout)
            aboutBackBtn = findViewById(R.id.aboutBackBtn)
            termsPrivacyLayout = findViewById(R.id.termsPrivacyLayout)
            termsPrivacyBackBtn = findViewById(R.id.termsPrivacyBackBtn)
            feedbackSupportLayout = findViewById(R.id.feedbackSupportLayout)
            feedbackSupportBackBtn = findViewById(R.id.feedbackSupportBackBtn)

            try {
                val tvAboutVersion: TextView? = findViewById(R.id.tvAboutVersion)
                if (tvAboutVersion != null) {
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    val versionText = pInfo.versionName
                    val currentText = tvAboutVersion.text.toString()
                    tvAboutVersion.text = "$currentText\n\nApp Version: $versionText"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            rgLanguage = findViewById(R.id.rgLanguage)
            rbLangEnglish = findViewById(R.id.rbLangEnglish)
            rbLangTelugu = findViewById(R.id.rbLangTelugu)

            val overlayBgColor = Color.parseColor("#121212")
            val allOverlays = listOf(
                settingsLayout, guestSettingsLayout, loginLayout,
                languageLayout, aboutLayout,
                termsPrivacyLayout, feedbackSupportLayout
            )
            for (overlay in allOverlays) {
                overlay.setBackgroundColor(overlayBgColor)
                overlay.isClickable = true
                overlay.isFocusable = true
                overlay.alpha = 1f
            }

            // Keyboard/WindowInsets Handling (Flawless Fade Out - Zero movement)
            ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                val sysBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                
                val top = sysBars.top
                val bottom = insets.bottom

                // Only pad the dynamic chat root layout, keeping background static
                chatRootLayout.setPadding(0, top, 0, bottom)
                
                val extraPadding = (16 * resources.displayMetrics.density).toInt()
                drawerLayout.getChildAt(1)?.setPadding(0, top + extraPadding, 0, sysBars.bottom)

                for (overlay in allOverlays) {
                    overlay.setPadding(0, top, 0, bottom)
                }

                val isKeyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())
                
                // Keyboard open: Fade out only the bottom cards/greeting smoothly without moving
                if (isKeyboardVisible) {
                    if (welcomeBottomLayout.visibility == View.VISIBLE && welcomeLayout.visibility == View.VISIBLE) {
                        welcomeBottomLayout.animate().cancel()
                        welcomeBottomLayout.animate()
                            .alpha(0f)
                            .setDuration(150)
                            .withEndAction {
                                welcomeBottomLayout.visibility = View.INVISIBLE 
                            }
                            .start()
                    }
                    
                    chatWebView.postDelayed({ 
                        chatWebView.evaluateJavascript("javascript:window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });", null)
                    }, 50)
                } else {
                    // Keyboard closed: Fade back in smoothly IF we are on a new chat
                    if (currentChatId == null && chatHistoryForAI.isEmpty()) {
                        if (welcomeBottomLayout.visibility == View.INVISIBLE || welcomeBottomLayout.alpha < 1f) {
                            welcomeBottomLayout.visibility = View.VISIBLE
                            welcomeBottomLayout.animate().cancel()
                            welcomeBottomLayout.animate()
                                .alpha(1f)
                                .setDuration(250)
                                .start()
                        }
                    }
                }

                WindowInsetsCompat.CONSUMED
            }

            ViewCompat.setWindowInsetsAnimationCallback(
                drawerLayout,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {
                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>
                    ): WindowInsetsCompat {
                        val currentInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
                        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                        val bottom = currentInsets.bottom
                        
                        chatRootLayout.setPadding(0, top, 0, bottom)
                        
                        for (overlay in allOverlays) {
                            overlay.setPadding(0, top, 0, bottom)
                        }
                        return insets
                    }
                }
            )

            setupSettingsStateAndListeners(savedLang)

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (languageLayout.visibility == View.VISIBLE) closeSettingsSubWindow(languageLayout)
                    else if (aboutLayout.visibility == View.VISIBLE) closeSettingsSubWindow(aboutLayout)
                    else if (termsPrivacyLayout.visibility == View.VISIBLE) closeSettingsSubWindow(termsPrivacyLayout)
                    else if (feedbackSupportLayout.visibility == View.VISIBLE) closeSettingsSubWindow(feedbackSupportLayout)
                    else if (settingsLayout.visibility == View.VISIBLE) goBackToMenuFromSettings()
                    else if (guestSettingsLayout.visibility == View.VISIBLE) goBackToMenuFromGuestSettings()
                    else if (loginLayout.visibility == View.VISIBLE) goBackToMenuFromLogin()
                    else if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START)
                    else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            })

            drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) { if (slideOffset > 0) hideKeyboard() }
                override fun onDrawerOpened(drawerView: View) { hideKeyboard() }
                override fun onDrawerClosed(drawerView: View) {}
                override fun onDrawerStateChanged(newState: Int) {}
            })

            menuBtn.setOnClickListener { 
                hideKeyboard()
                drawerLayout.openDrawer(GravityCompat.START) 
            }

            menuSearchBtn.setOnClickListener { 
                chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Search history feature coming soon.'); setTimeout(hideLimitBanner, 3000); }", null)
            }

            menuNewChat.setOnClickListener {
                currentChatId = null
                clearChatWebView()
                chatHistoryForAI.clear() 
                updateWelcomeGreeting() 
                
                // Show Welcome screen & background again on new chat
                welcomeLayout.visibility = View.VISIBLE
                welcomeBottomLayout.visibility = View.VISIBLE
                welcomeBottomLayout.alpha = 1f
                mainBackgroundImage.visibility = View.VISIBLE
                chatRootLayout.setBackgroundColor(Color.TRANSPARENT)
                
                drawerLayout.closeDrawer(GravityCompat.START)
            }

            settingsBackBtn.setOnClickListener { goBackToMenuFromSettings() }
            menuLogin.setOnClickListener { openTopLevelMenu(loginLayout) }
            menuSettingsLoggedOut.setOnClickListener { openTopLevelMenu(guestSettingsLayout) }
            menuProfile.setOnClickListener {
                updateLoginUI()
                openTopLevelMenu(settingsLayout)
            }

            menuLogout.setOnClickListener {
                firebaseAuth.signOut()
                googleSignInClient.signOut().addOnCompleteListener {
                    updateLoginUI()
                    goBackToMenuFromSettings()
                }
            }

            updateLoginUI()
            loginBackBtn.setOnClickListener { goBackToMenuFromLogin() }
            googleLoginBtn.setOnClickListener {
                val signInIntent = googleSignInClient.signInIntent
                @Suppress("DEPRECATION")
                startActivityForResult(signInIntent, RC_SIGN_IN)
            }

            settingsAppLanguage.setOnClickListener { openSettingsSubWindow(languageLayout) }
            settingsTermsPrivacy.setOnClickListener { openSettingsSubWindow(termsPrivacyLayout) }
            settingsFeedbackSupport.setOnClickListener { openSettingsSubWindow(feedbackSupportLayout) }
            settingsAbout.setOnClickListener { openSettingsSubWindow(aboutLayout) }

            guestSettingsAppLanguage.setOnClickListener { openSettingsSubWindow(languageLayout) }
            guestSettingsTermsPrivacy.setOnClickListener { openSettingsSubWindow(termsPrivacyLayout) }
            guestSettingsAbout.setOnClickListener { openSettingsSubWindow(aboutLayout) }

            guestSettingsBackBtn.setOnClickListener { goBackToMenuFromGuestSettings() }
            guestSettingsLoginBtn.setOnClickListener {
                loginLayout.bringToFront()
                loginLayout.visibility = View.VISIBLE
                guestSettingsLayout.visibility = View.GONE
            }

            languageBackBtn.setOnClickListener { closeSettingsSubWindow(languageLayout) }
            aboutBackBtn.setOnClickListener { closeSettingsSubWindow(aboutLayout) }
            termsPrivacyBackBtn.setOnClickListener { closeSettingsSubWindow(termsPrivacyLayout) }
            feedbackSupportBackBtn.setOnClickListener { closeSettingsSubWindow(feedbackSupportLayout) }

            val neededPermissions = mutableListOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.CALL_PHONE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missingPermissions = neededPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_CODE_PERMISSION)
            }

            setupDynamicSendButton()
            try { setupSpeechRecognizer() } catch (e: Exception) { e.printStackTrace() }

            sendBtn.setOnClickListener {
                if (sendBtn.tag == "send") {
                    sendCurrentMessage()
                } else {
                    startActivity(Intent(this@MainActivity, LiveActivity::class.java))
                }
            }

            voiceBtn.setOnClickListener {
                if (!checkRateLimit()) return@setOnClickListener
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListeningWithoutBeep()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
                }
            }

            voiceBtn.setOnLongClickListener {
                toggleWakeWordService()
                true
            }

            handleIncomingVoiceCommand(intent)

            chatWebView.postDelayed({ checkRateLimit() }, 1000)

        } catch (e: Exception) {
            // Error handling
        }
    }

    private fun populateInputBox(text: String) {
        messageInput.setText(text)
        messageInput.setSelection(messageInput.text.length)
        messageInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupChatWebView() {
        chatWebView.settings.javaScriptEnabled = true
        chatWebView.setBackgroundColor(Color.TRANSPARENT)
        
        chatWebView.isHorizontalScrollBarEnabled = false
        chatWebView.overScrollMode = View.OVER_SCROLL_NEVER
        
        val textColor = "#E0E0E0"
        val userBgColor = "#303030"
        val userTextColor = "#FFFFFF"

        val htmlTemplate = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=0">
                <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
                <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                <style>
                    html, body {
                        max-width: 100vw;
                        overflow-x: hidden;
                    }
                    body { 
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; 
                        color: $textColor; 
                        margin: 0; 
                        padding: 16px; 
                        padding-bottom: 80px; 
                        box-sizing: border-box;
                    }
                    .msg-container { margin-bottom: 24px; display: flex; width: 100%; flex-direction: column; }
                    
                    .user-msg { 
                        background-color: $userBgColor; 
                        padding: 12px 18px; 
                        border-radius: 20px; 
                        max-width: 85%; 
                        margin-left: auto; 
                        color: $userTextColor; 
                        font-size: 16px;
                        line-height: 1.5;
                        word-wrap: break-word; 
                        overflow-wrap: break-word; 
                        word-break: break-word;
                        white-space: pre-wrap; 
                        box-sizing: border-box;
                    }
                    
                    .ai-msg { 
                        padding: 4px 0; 
                        max-width: 100%; 
                        margin-right: auto; 
                        line-height: 1.6; 
                        font-size: 16px;
                        word-wrap: break-word; 
                        overflow-wrap: break-word;
                        word-break: break-word;
                        overflow-x: hidden; 
                    }
                    
                    pre { 
                        background-color: #1E1E1E; 
                        padding: 14px; 
                        border-radius: 12px; 
                        overflow-x: auto; 
                        color: #E0E0E0; 
                        max-width: 100%; 
                        box-sizing: border-box; 
                        margin: 8px 0;
                    }
                    code { font-family: 'Courier New', Courier, monospace; font-size: 14px; }
                    
                    table { 
                        border-collapse: collapse; 
                        width: 100%; 
                        margin-top: 8px;
                        display: block; 
                        overflow-x: auto;
                        white-space: nowrap;
                    }
                    th, td { border: 1px solid #555; padding: 10px; }
                    img { max-width: 100%; height: auto; border-radius: 8px; }

                    .bubble-loader {
                        display: grid;
                        grid-template-columns: repeat(3, 1fr);
                        gap: 3px;
                        width: 20px;
                        height: 20px;
                        margin-right: 12px;
                    }
                    .bubble {
                        border-radius: 50%;
                        animation: bubbleBlink 1.2s infinite ease-in-out;
                    }
                    
                    .bubble:nth-child(1) { background-color: #1E90FF; animation-delay: 0.2s; }
                    .bubble:nth-child(2) { background-color: #1E90FF; animation-delay: 0.3s; }
                    .bubble:nth-child(3) { background-color: #1E90FF; animation-delay: 0.4s; }
                    
                    .bubble:nth-child(4) { background-color: #63B8FF; animation-delay: 0.1s; }
                    .bubble:nth-child(5) { background-color: #63B8FF; animation-delay: 0.2s; }
                    .bubble:nth-child(6) { background-color: #63B8FF; animation-delay: 0.3s; }
                    
                    .bubble:nth-child(7) { background-color: #B0E2FF; animation-delay: 0s; }
                    .bubble:nth-child(8) { background-color: #B0E2FF; animation-delay: 0.1s; }
                    .bubble:nth-child(9) { background-color: #B0E2FF; animation-delay: 0.2s; }

                    @keyframes bubbleBlink {
                        0%, 70%, 100% { transform: scale(1); opacity: 0.2; }
                        35% { transform: scale(0.6); opacity: 1; }
                    }
                    
                    .thinking-box {
                        display: flex;
                        align-items: center;
                        color: #FFFFFF; 
                        font-weight: bold;
                        font-size: 16px;
                    }
                </style>
            </head>
            <body>
                <div id="chat-history"></div>
                <script>
                    function clearChat() {
                        document.getElementById('chat-history').innerHTML = '';
                    }
                    function addChatMessage(id, isUser, encodedText) {
                        const text = decodeURIComponent(escape(window.atob(encodedText)));
                        const chatDiv = document.getElementById('chat-history');
                        
                        let msgDiv = document.getElementById('msg-' + id);
                        if (!msgDiv) {
                            msgDiv = document.createElement('div');
                            msgDiv.id = 'msg-' + id;
                            msgDiv.className = 'msg-container';
                            
                            const innerDiv = document.createElement('div');
                            innerDiv.className = isUser ? 'user-msg' : 'ai-msg';
                            msgDiv.appendChild(innerDiv);
                            
                            chatDiv.appendChild(msgDiv);
                        }
                        
                        if (isUser) {
                            msgDiv.firstChild.textContent = text; 
                        } else {
                            if (text === 'Thinking...') {
                                msgDiv.firstChild.innerHTML = '<div class="thinking-box"><div class="bubble-loader"><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div><div class="bubble"></div></div>Thinking...</div>';
                            } else {
                                msgDiv.firstChild.innerHTML = marked.parse(text);
                                msgDiv.querySelectorAll('pre code').forEach((block) => {
                                    hljs.highlightElement(block);
                                });
                            }
                        }
                        
                        setTimeout(() => {
                            window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
                        }, 50);
                    }
                    
                    function showLimitBanner(msg) {
                        let existing = document.getElementById('limit-banner');
                        if(existing) existing.remove();
                        let banner = document.createElement('div');
                        banner.id = 'limit-banner';
                        banner.innerHTML = msg;
                        banner.style.cssText = 'position:fixed; bottom:10px; left:50%; transform:translateX(-50%); background:#757575; color:white; padding:8px 16px; border-radius:20px; font-size:13px; z-index:9999; box-shadow: 0 4px 6px rgba(0,0,0,0.3); font-weight:bold; white-space:nowrap; text-align:center;';
                        document.body.appendChild(banner);
                    }
                    
                    function hideLimitBanner() {
                        let existing = document.getElementById('limit-banner');
                        if(existing) existing.remove();
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        chatWebView.webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 100) {
                    chatWebView.postDelayed({ chatWebView.visibility = View.VISIBLE }, 80)
                }
            }
        }
        chatWebView.loadDataWithBaseURL(null, htmlTemplate, "text/html", "UTF-8", null)
    }

    private fun addMessage(text: String, isUser: Boolean) {
        currentMessageId++
        updateMessageInWebView(currentMessageId, text, isUser)
    }

    private fun updateLastMessage(text: String) {
        updateMessageInWebView(currentMessageId, text, false)
    }

    private fun updateMessageInWebView(id: Int, text: String, isUser: Boolean) {
        val base64Text = Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP)
        val jsCommand = "javascript:addChatMessage('$id', $isUser, '$base64Text');"
        chatWebView.evaluateJavascript(jsCommand, null)
    }

    private fun clearChatWebView() {
        chatWebView.evaluateJavascript("javascript:clearChat();", null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingVoiceCommand(intent)
    }

    private fun handleIncomingVoiceCommand(intent: Intent?) {
        val command = intent?.getStringExtra("voice_command") ?: return
        intent.removeExtra("voice_command")
        if (command.isBlank()) return
        
        if (!checkRateLimit()) return

        messageInput.setText(command)
        handler.post { sendCurrentMessage() }
    }

    private fun toggleWakeWordService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
            return
        }
        if (WakeWordService.isRunning) {
            stopService(Intent(this, WakeWordService::class.java))
            chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Wake word disabled.'); setTimeout(hideLimitBanner, 3000); }", null)
        } else {
            val serviceIntent = Intent(this, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Wake word active — Say \"Hey Nakshatra AI\"'); setTimeout(hideLimitBanner, 4000); }", null)
        }
    }

    private fun checkRateLimit(): Boolean {
        val limitCount = sharedPrefs.getInt("msg_count", 0)
        val resetTime = sharedPrefs.getLong("reset_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (limitCount >= 15) {
            if (currentTime >= resetTime) {
                sharedPrefs.edit().putInt("msg_count", 0).putLong("reset_time", 0L).apply()
                hideLimitWarning()
                return true
            } else {
                showLimitWarning(resetTime)
                return false
            }
        }
        return true
    }

    private fun incrementMessageCount() {
        val currentCount = sharedPrefs.getInt("msg_count", 0)
        val newCount = currentCount + 1
        val editor = sharedPrefs.edit()
        editor.putInt("msg_count", newCount)
        
        if (newCount >= 15) {
            val resetTime = System.currentTimeMillis() + (60 * 60 * 1000)
            editor.putLong("reset_time", resetTime)
            handler.post { showLimitWarning(resetTime) }
        }
        editor.apply()
    }

    private fun showLimitWarning(resetTimeMillis: Long) {
        val sdf = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStr = sdf.format(java.util.Date(resetTimeMillis))
        val msg = "Your limit reached. Resets at $timeStr"
        chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('$msg'); }", null)
    }
    
    private fun hideLimitWarning() {
        chatWebView.evaluateJavascript("javascript:if(typeof hideLimitBanner === 'function') { hideLimitBanner(); }", null)
    }

    private fun sendCurrentMessage() {
        if (!checkRateLimit()) return 

        if (firebaseAuth.currentUser == null) {
            val guestCount = sharedPrefs.getInt("guest_msg_count", 0)
            if (guestCount >= 2) {
                hideKeyboard()
                openTopLevelMenu(loginLayout)
                return
            }
            sharedPrefs.edit().putInt("guest_msg_count", guestCount + 1).apply()
        }

        val userText = messageInput.text.toString().trim()
        if (userText.isEmpty()) return

        // Hide home screen UI completely and transition to Black Theme
        welcomeLayout.visibility = View.GONE
        mainBackgroundImage.visibility = View.GONE
        chatRootLayout.setBackgroundColor(Color.parseColor("#000000"))

        if (currentChatId == null && firebaseAuth.currentUser != null) {
            currentChatId = System.currentTimeMillis().toString()
        }

        addMessage(userText, true)
        messageInput.text.clear()
        
        incrementMessageCount() 
        
        currentMessageId++
        updateMessageInWebView(currentMessageId, "Thinking...", false)

        currentChatId?.let { chatRepository.saveMessage(it, userText, isUser = true) { loadChatHistory() } }

        SearchHelper.searchInternet(userText) { searchResult ->
            val contextPrompt = if (searchResult != null) {
                "Recent Web Search Information: $searchResult\n\n" + AssistantSystemPrompt.PROMPT
            } else {
                AssistantSystemPrompt.PROMPT
            }

            val finalPrompt = if (userPreferences != null) {
                val name = userPreferences?.get("name") ?: "User"
                "The user's name is $name. \n$contextPrompt"
            } else {
                contextPrompt
            }

            val identityInjectedPrompt = "$finalPrompt\n\n[CRITICAL IDENTITY RULE]: Your name is \"Nakshatra AI\" (నక్షత్ర AI). You were developed and created by \"Bunny Praveen\" (బన్నీ ప్రవీణ్). If the user asks \"What is your name?\" (నీ పేరు ఏంటి?), say you are Nakshatra AI. If they ask \"Who developed/created you?\" (నిన్ను ఎవరు తయారు చేసారు / డెవలప్ చేసారు?), say you were developed by Bunny Praveen. Answer naturally in the language the user asked."

            GroqHelper.getChatCompletion(
                userText = userText,
                systemPrompt = identityInjectedPrompt,
                chatHistory = chatHistoryForAI, 
                callback = object : GroqHelper.ChatCallback {
                    override fun onSuccess(reply: String) {
                        handler.post {
                            if (isFinishing || isDestroyed) return@post

                            fun deliverWithTypingEffect(text: String) {
                                val words = text.split(" ")
                                var currentIndex = 0
                                val typingHandler = Handler(Looper.getMainLooper())
                                
                                typingHandler.post(object : Runnable {
                                    override fun run() {
                                        if (currentIndex < words.size) {
                                            val chunkSize = if (words.size > 40) 2 else 1 
                                            currentIndex += chunkSize
                                            if (currentIndex > words.size) currentIndex = words.size
                                            
                                            val currentText = words.subList(0, currentIndex).joinToString(" ")
                                            updateMessageInWebView(currentMessageId, currentText, false)
                                            typingHandler.postDelayed(this, 30) 
                                        } else {
                                            updateMessageInWebView(currentMessageId, text, false)
                                            currentChatId?.let { chatRepository.saveMessage(it, text, isUser = false) { loadChatHistory() } }
                                            
                                            chatHistoryForAI.add(Pair(userText, true))
                                            chatHistoryForAI.add(Pair(text, false))
                                        }
                                    }
                                })
                            }

                            val handled = CommandParser.handle(
                                context = this@MainActivity,
                                reply = reply,
                                userOriginalMessage = userText, 
                                confirmAction = { prompt, onConfirmed ->
                                    val dialog = android.app.Dialog(this@MainActivity)
                                    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
                                    dialog.setContentView(R.layout.dialog_modern_call)
                                    dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                                    dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

                                    val tvMessage = dialog.findViewById<TextView>(R.id.tvDialogMessage)
                                    val btnYes = dialog.findViewById<Button>(R.id.btnDialogYes)
                                    val btnNo = dialog.findViewById<Button>(R.id.btnDialogNo)

                                    tvMessage.text = prompt
                                    btnNo.setOnClickListener { dialog.dismiss(); deliverWithTypingEffect("సరే, cancel చేసాను") }
                                    btnYes.setOnClickListener { dialog.dismiss(); onConfirmed() }
                                    dialog.show()
                                },
                                onResult = { text -> deliverWithTypingEffect(text) }
                            )

                            if (!handled) deliverWithTypingEffect(reply)
                        }
                    }

                    override fun onError(message: String) {
                        handler.post {
                            if (isFinishing || isDestroyed) return@post
                            updateLastMessage("I am currently experiencing connection issues. Please check your internet connection and try again.")
                        }
                    }
                }
            )
        }
    }

    private fun setupSettingsStateAndListeners(savedLang: String) {
        if (savedLang == "te") rbLangTelugu.isChecked = true else rbLangEnglish.isChecked = true

        rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val lang = if (checkedId == R.id.rbLangTelugu) "te" else "en"
            sharedPrefs.edit().putString("app_lang", lang).apply()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
    }

    private fun writeActualSigningSha1ToFile() {
        try {
            val sha1List = StringBuilder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signatures = packageInfo.signingInfo?.apkContentsSigners
                signatures?.forEach { sig -> sha1List.append(sha1Of(sig.toByteArray())).append("\n") }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                signatures?.forEach { sig -> sha1List.append(sha1Of(sig.toByteArray())).append("\n") }
            }
            val file = File(getExternalFilesDir(null), "sha1_key.txt")
            file.writeText(sha1List.toString())
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun sha1Of(byteArray: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(byteArray).joinToString("") { "%02x".format(it) }
    }

    private fun openTopLevelMenu(layout: LinearLayout) { layout.visibility = View.VISIBLE; layout.bringToFront() }
    private fun goBackToMenuFromSettings() { settingsLayout.visibility = View.GONE }
    private fun goBackToMenuFromGuestSettings() { guestSettingsLayout.visibility = View.GONE }
    private fun goBackToMenuFromLogin() { loginLayout.visibility = View.GONE }
    private fun openSettingsSubWindow(layout: LinearLayout) { layout.visibility = View.VISIBLE; layout.bringToFront() }
    private fun closeSettingsSubWindow(layout: LinearLayout) { layout.visibility = View.GONE }

    private fun updateLoginUI() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            sharedPrefs.edit().putInt("guest_msg_count", 0).apply()
            
            menuLogin.visibility = View.GONE
            menuSettingsLoggedOut.visibility = View.GONE
            menuProfile.visibility = View.VISIBLE
            tvProfileName.text = user.displayName ?: "User"
            settingsProfileName.text = user.displayName ?: "User"

            Glide.with(this).load(user.photoUrl).circleCrop().into(ivProfileAvatar)
            Glide.with(this).load(user.photoUrl).circleCrop().into(settingsProfileAvatar)
            
            chatRepository.saveUserProfile(user.displayName ?: "User")
            chatRepository.getUserProfile { profileData -> userPreferences = profileData }
            loadChatHistory()
        } else {
            menuLogin.visibility = View.VISIBLE
            menuSettingsLoggedOut.visibility = View.VISIBLE
            menuProfile.visibility = View.GONE
            historyListContainer.removeAllViews()
            userPreferences = null
        }
        updateWelcomeGreeting()
    }

    private fun loadChatHistory() {
        val user = firebaseAuth.currentUser ?: return
        chatRepository.loadChatList { chatList ->
            handler.post {
                historyListContainer.removeAllViews()
                if (chatList.isEmpty()) {
                    val noHistoryView = TextView(this@MainActivity)
                    noHistoryView.text = "No history found"
                    noHistoryView.setTextColor(Color.WHITE) 
                    noHistoryView.setPadding(16, 16, 16, 16)
                    historyListContainer.addView(noHistoryView)
                    return@post
                }

                for (chat in chatList) {
                    val chatId = chat.first
                    val chatData = chat.second
                    val isPinned = (chatData["isPinned"] as? Boolean) ?: false
                    val rawTitle = chatData["title"] as? String ?: "Chat: ${chatId.takeLast(4)}"
                    val chatTitle = if (isPinned) "📌 $rawTitle" else rawTitle

                    val chatItem = TextView(this@MainActivity)
                    chatItem.text = chatTitle
                    chatItem.setTextColor(Color.WHITE) 
                    chatItem.textSize = 16f
                    chatItem.setPadding(16, 24, 16, 24)
                    
                    val shape = GradientDrawable()
                    shape.shape = GradientDrawable.RECTANGLE
                    shape.cornerRadius = 24f
                    shape.setColor(Color.parseColor("#333333"))
                    chatItem.background = shape

                    chatItem.setOnClickListener {
                        currentChatId = chatId
                        clearChatWebView()
                        chatHistoryForAI.clear() 
                        
                        // Transition to Black Theme immediately on loading old chat
                        welcomeLayout.visibility = View.GONE
                        mainBackgroundImage.visibility = View.GONE
                        chatRootLayout.setBackgroundColor(Color.parseColor("#000000"))

                        chatRepository.loadChatMessages(chatId) { messages ->
                            handler.post {
                                for (msg in messages) {
                                    chatHistoryForAI.add(msg)
                                    addMessage(text = msg.first, isUser = msg.second)
                                }
                            }
                        }
                        drawerLayout.closeDrawer(GravityCompat.START)
                    }

                    var touchX = 0f; var touchY = 0f
                    chatItem.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) { touchX = event.rawX; touchY = event.rawY }
                        false
                    }

                    chatItem.setOnLongClickListener { showChatOptionsPopupExactlyAtTouch(touchX, touchY, chatId, chatData); true }

                    historyListContainer.addView(chatItem)
                    val divider = View(this@MainActivity)
                    divider.setBackgroundColor(Color.parseColor("#333333"))
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                    params.setMargins(0, 4, 0, 4)
                    divider.layoutParams = params
                    historyListContainer.addView(divider)
                }
            }
        }
    }

    private fun showChatOptionsPopupExactlyAtTouch(x: Float, y: Float, chatId: String, chatData: Map<String, Any?>) {
        val isPinned = (chatData["isPinned"] as? Boolean) ?: false
        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val anchorView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            this.x = x; this.y = y
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootView.addView(anchorView)

        anchorView.post {
            val popupMenu = PopupMenu(this, anchorView)
            popupMenu.menu.add(0, 0, 0, if (isPinned) "📌 Unpin" else "📌 Pin")
            popupMenu.menu.add(0, 1, 1, "✏️ Rename")
            popupMenu.menu.add(0, 2, 2, "🗑️ Delete")

            popupMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    0 -> chatRepository.pinChat(chatId, isPinned, onDone = { handler.post { chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('${if (isPinned) "Chat unpinned" else "Chat pinned"}'); setTimeout(hideLimitBanner, 2000); }", null); loadChatHistory() } }, onLimitReached = { handler.post { chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Max 5 pinned chats allowed'); setTimeout(hideLimitBanner, 3000); }", null) } })
                    1 -> showRenameDialog(chatId, chatData["title"] as? String ?: "")
                    2 -> showDeleteConfirmDialog(chatId)
                }
                true
            }
            popupMenu.setOnDismissListener { rootView.removeView(anchorView) }
            popupMenu.show()
        }
    }

    private fun showRenameDialog(chatId: String, currentTitle: String) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(60, 40, 60, 10)
        val input = EditText(this)
        input.setText(currentTitle)
        input.setSelection(currentTitle.length)
        input.setSingleLine(true)

        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.setStroke(4, Color.parseColor("#808080"))
        shape.cornerRadius = 24f
        input.background = shape
        input.setPadding(40, 35, 40, 35)
        layout.addView(input)

        AlertDialog.Builder(this)
            .setTitle("✏️ Rename chat")
            .setView(layout)
            .setPositiveButton("Save") { dialog, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) chatRepository.renameChat(chatId, newTitle) { handler.post { loadChatHistory() } }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showDeleteConfirmDialog(chatId: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle("🗑️ Delete chat?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { d, _ ->
                chatRepository.deleteChat(chatId) {
                    handler.post {
                        chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Chat deleted successfully'); setTimeout(hideLimitBanner, 2000); }", null)
                        if (currentChatId == chatId) {
                            currentChatId = null
                            clearChatWebView()
                            updateWelcomeGreeting() 
                            
                            welcomeLayout.visibility = View.VISIBLE
                            welcomeBottomLayout.visibility = View.VISIBLE
                            welcomeBottomLayout.alpha = 1f
                            mainBackgroundImage.visibility = View.VISIBLE
                            chatRootLayout.setBackgroundColor(Color.TRANSPARENT)
                        }
                        loadChatHistory()
                    }
                }
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED) }
        dialog.show()
    }

    private fun setupDynamicSendButton() {
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrEmpty()) {
                    sendBtn.setImageResource(android.R.drawable.ic_menu_send)
                    sendBtn.tag = "send"
                    voiceBtn.visibility = View.GONE
                } else {
                    sendBtn.setImageDrawable(originalLiveIcon)
                    sendBtn.tag = "live"
                    voiceBtn.visibility = View.VISIBLE
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { 
                val msg = when(error) {
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network issue. Please check your connection."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't quite catch that. Please try again."
                    else -> "Voice recognition interrupted. Please try again."
                }
                chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('$msg'); setTimeout(hideLimitBanner, 3000); }", null)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) messageInput.setText(matches[0])
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListeningWithoutBeep() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        speechRecognizer.startListening(intent)
        handler.postDelayed({ audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0) }, 500)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) { 
                chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Authentication failed. Please verify your connection and try again.'); setTimeout(hideLimitBanner, 4000); }", null)
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    loginLayout.visibility = View.GONE
                    updateLoginUI()
                } else { 
                    chatWebView.evaluateJavascript("javascript:if(typeof showLimitBanner === 'function') { showLimitBanner('Sign-in failed. Please try again.'); setTimeout(hideLimitBanner, 4000); }", null)
                }
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        TTSHelper.shutdown()
    }

    private fun getShortName(fullName: String): String {
        if (fullName.isBlank()) return "User"
        val firstName = fullName.trim().split(" ")[0]
        return if (firstName.length > 7) firstName.substring(0, 4).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        else firstName
    }

    private fun updateWelcomeGreeting() {
        val tvName = findViewById<TextView>(R.id.welcomeTextName) ?: return
        val user = firebaseAuth.currentUser
        if (user != null) {
            val shortName = getShortName(user.displayName ?: "User")
            tvName.text = "Hello, $shortName,"
        } else { 
            tvName.text = "Hello,"
        }
    }

    private fun hideKeyboard() {
        val view = this.currentFocus ?: window.decorView
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        messageInput.clearFocus()
    }
}
