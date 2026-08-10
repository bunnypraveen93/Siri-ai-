package com.praveen.siriai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout

import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuBtn: ImageView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var voiceBtn: ImageView
    private lateinit var welcomeLayout: LinearLayout
    
    private lateinit var settingsLayout: LinearLayout
    private lateinit var settingsBackBtn: ImageView

    private lateinit var loginLayout: LinearLayout
    private lateinit var loginBackBtn: ImageView
    private lateinit var googleLoginBtn: LinearLayout
    
    private lateinit var menuNewChat: TextView
    private lateinit var menuProjects: TextView
    private lateinit var menuHistory: TextView
    private lateinit var menuSettings: TextView
    private lateinit var menuLogin: TextView
    
    private val client = OkHttpClient()
    private val apiKey = "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" 
    private val handler = Handler(Looper.getMainLooper())
    
    private lateinit var speechRecognizer: SpeechRecognizer
    private val REQUEST_CODE_PERMISSION = 200

    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val WEB_CLIENT_ID = "1092066177158-v1f0l4rtflivigr78eksn5mmm399hg93.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val logFile = File(getExternalFilesDir(null), "crash_log.txt")
                logFile.writeText(sw.toString())
            } catch (ignored: Exception) {
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        super.onCreate(savedInstanceState)

        writeActualSigningSha1ToFile()

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

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        try {
            window.setBackgroundDrawableResource(android.R.color.black)
            setContentView(R.layout.activity_main)
            
            drawerLayout = findViewById(R.id.drawerLayout)
            menuBtn = findViewById(R.id.menuBtn)
            chatContainer = findViewById(R.id.chatContainer)
            chatScrollView = findViewById(R.id.chatScrollView)
            messageInput = findViewById(R.id.messageInput)
            sendBtn = findViewById(R.id.sendBtn)
            voiceBtn = findViewById(R.id.voiceBtn)
            welcomeLayout = findViewById(R.id.welcomeLayout)
            
            settingsLayout = findViewById(R.id.settingsLayout)
            settingsBackBtn = findViewById(R.id.settingsBackBtn)

            loginLayout = findViewById(R.id.loginLayout)
            loginBackBtn = findViewById(R.id.loginBackBtn)
            googleLoginBtn = findViewById(R.id.googleLoginBtn)
            
            menuNewChat = findViewById(R.id.menuNewChat)
            menuProjects = findViewById(R.id.menuProjects)
            menuHistory = findViewById(R.id.menuHistory)
            menuSettings = findViewById(R.id.menuSettings)
            menuLogin = findViewById(R.id.menuLogin)

            menuBtn.setOnClickListener {
                drawerLayout.openDrawer(GravityCompat.START)
            }

            menuNewChat.setOnClickListener {
                chatContainer.removeAllViews()
                welcomeLayout.visibility = View.VISIBLE
                drawerLayout.closeDrawer(GravityCompat.START)
            }

            menuProjects.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                Toast.makeText(this, "Projects Clicked", Toast.LENGTH_SHORT).show()
            }

            menuHistory.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                Toast.makeText(this, "Recent History Clicked", Toast.LENGTH_SHORT).show()
            }

            menuSettings.setOnClickListener {
                settingsLayout.alpha = 0f
                settingsLayout.visibility = View.VISIBLE
                settingsLayout.animate().alpha(1f).setDuration(250).start()
            }

            settingsBackBtn.setOnClickListener {
                goBackToMenuFromSettings()
            }

            menuLogin.setOnClickListener {
                drawerLayout.closeDrawer(GravityCompat.START)
                loginLayout.alpha = 0f
                loginLayout.visibility = View.VISIBLE
                loginLayout.animate().alpha(1f).setDuration(250).start()
            }

            updateMenuLoginText()

            loginBackBtn.setOnClickListener {
                goBackToMenuFromLogin()
            }

            googleLoginBtn.setOnClickListener {
                val signInIntent = googleSignInClient.signInIntent
                startActivityForResult(signInIntent, RC_SIGN_IN)
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
            }
            
            setupDynamicSendButton()
            
            try {
                setupSpeechRecognizer()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            sendBtn.setOnClickListener {
                if (sendBtn.tag == "send") {
                    val userText = messageInput.text.toString().trim()
                    if (userText.isNotEmpty()) {
                        welcomeLayout.visibility = View.GONE
                        addMessage(userText, true)
                        messageInput.text.clear()
                        addMessage("Thinking...", false)
                        callGroqAPI(userText)
                    }
                } else {
                    val intent = Intent(this@MainActivity, LiveActivity::class.java)
                    startActivity(intent)
                }
            }
            
            voiceBtn.setOnClickListener {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListeningWithoutBeep()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
                }
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeActualSigningSha1ToFile() {
        try {
            val sha1List = StringBuilder()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signatures = packageInfo.signingInfo?.apkContentsSigners
                signatures?.forEach { sig ->
                    val md = MessageDigest.getInstance("SHA-1")
                    md.update(sig.toByteArray())
                    val sha1 = md.digest().joinToString(":") { String.format("%02X", it) }
                    sha1List.append(sha1).append("\n")
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                signatures?.forEach { sig ->
                    val md = MessageDigest.getInstance("SHA-1")
                    md.update(sig.toByteArray())
                    val sha1 = md.digest().joinToString(":") { String.format("%02X", it) }
                    sha1List.append(sha1).append("\n")
                }
            }
            val outFile = File(getExternalFilesDir(null), "actual_sha1.txt")
            outFile.writeText(sha1List.toString())
        } catch (e: Exception) {
            try {
                val outFile = File(getExternalFilesDir(null), "actual_sha1.txt")
                outFile.writeText("ERROR: ${e.message}")
            } catch (ignored: Exception) {
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account.idToken != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Sign-in failed: ${e.statusCode}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    Toast.makeText(this, "Welcome ${user?.displayName ?: ""}", Toast.LENGTH_SHORT).show()
                    updateMenuLoginText()
                    goBackToMenuFromLogin()
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun updateMenuLoginText() {
        val user = firebaseAuth.currentUser
        menuLogin.text = if (user != null) (user.displayName ?: user.email ?: "Logged in") else "Login"
    }

    private fun goBackToMenuFromSettings() {
        settingsLayout.animate().alpha(0f).setDuration(250).withEndAction {
            settingsLayout.visibility = View.GONE
        }.start()
    }

    private fun goBackToMenuFromLogin() {
        loginLayout.animate().alpha(0f).setDuration(250).withEndAction {
            loginLayout.visibility = View.GONE
        }.start()
    }

    override fun onBackPressed() {
        if (settingsLayout.visibility == View.VISIBLE) {
            goBackToMenuFromSettings()
        } else if (loginLayout.visibility == View.VISIBLE) {
            goBackToMenuFromLogin()
        } else if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun setupDynamicSendButton() {
        sendBtn.tag = "live"
        sendBtn.setImageResource(R.drawable.ic_gemini_live)

        messageInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    sendBtn.setImageResource(R.drawable.ic_gemini_live)
                    sendBtn.tag = "live"
                } else {
                    sendBtn.setImageResource(R.drawable.ic_send_pro)
                    sendBtn.tag = "send"
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                muteBeepSound(false) 
                messageInput.hint = "Listening..."
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                messageInput.hint = "Ask Siri AI..."
            }
            override fun onError(error: Int) {
                muteBeepSound(false) 
                messageInput.hint = "Ask Siri AI..."
            }
            override fun onResults(results: Bundle?) {
                muteBeepSound(false)
                messageInput.hint = "Ask Siri AI..."
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val currentText = messageInput.text.toString()
                    val newText = if (currentText.isEmpty()) matches[0] else "$currentText ${matches[0]}"
                    messageInput.setText(newText)
                    messageInput.setSelection(messageInput.text.length)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListeningWithoutBeep() {
        try {
            val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            muteBeepSound(true)
            speechRecognizer.startListening(speechRecognizerIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun muteBeepSound(mute: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val adjust = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            speechRecognizer.destroy()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 16
        
        if (isUser) {
            tv.setPadding(36, 20, 36, 20)
            tv.setBackgroundResource(R.drawable.bg_message_user)
            tv.setTextColor(android.graphics.Color.WHITE)
            params.gravity = android.view.Gravity.END
            params.marginStart = 80
        } else {
            tv.setPadding(8, 8, 8, 8)
            tv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            tv.setTextColor(android.graphics.Color.parseColor("#E0E0E0")) 
            params.gravity = android.view.Gravity.START
            params.marginEnd = 40
        }
        
        tv.layoutParams = params
        chatContainer.addView(tv)
        chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun updateLastMessage(text: String) {
        val lastChild = chatContainer.getChildAt(chatContainer.childCount - 1)
        if (lastChild is TextView) { 
            lastChild.text = text 
        }
    }

    private fun callGroqAPI(userMessage: String) {
        val url = "https://api.groq.com/openai/v1/chat/completions"
        
        val messageObj = JSONObject()
        messageObj.put("role", "user")
        messageObj.put("content", userMessage)
        
        val messagesArray = JSONArray()
        messagesArray.put(messageObj)
        
        val jsonBody = JSONObject()
        jsonBody.put("model", "openai/gpt-oss-20b") 
        jsonBody.put("messages", messagesArray)
        
        val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post { 
                    if (isFinishing || isDestroyed) return@post
                    updateLastMessage("Network Error: " + e.message) 
                }
            }
            
            override fun onResponse(call: Call, response: Response) {
                val raw = response.body?.string()
                handler.post {
                    if (isFinishing || isDestroyed) return@post
                    
                    try {
                        val json = JSONObject(raw ?: "")
                        if (json.has("error")) {
                            updateLastMessage("API Error: " + json.getJSONObject("error").optString("message"))
                            return@post
                        }
                        
                        val txt = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                            
                        updateLastMessage(txt.trim())
                        chatScrollView.post { chatScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                    } catch (ex: Exception) {
                        updateLastMessage("System Error: Could not process the response.")
                    }
                }
            }
        })
    }
}