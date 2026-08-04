package com.praveen.siriai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var menuBtn: ImageView
    private lateinit var chatContainer: LinearLayout
    private lateinit var chatScrollView: ScrollView
    private lateinit var messageInput: EditText
    private lateinit var sendBtn: ImageView
    private lateinit var voiceBtn: ImageView
    private lateinit var welcomeLayout: LinearLayout
    
    // Side Menu items
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        
        // Side Menu bindings
        menuNewChat = findViewById(R.id.menuNewChat)
        menuProjects = findViewById(R.id.menuProjects)
        menuHistory = findViewById(R.id.menuHistory)
        menuSettings = findViewById(R.id.menuSettings)
        menuLogin = findViewById(R.id.menuLogin)

        // 3-Lines Icon Click -> Opens Side Menu
        menuBtn.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Side Menu Click Listeners
        menuNewChat.setOnClickListener {
            chatContainer.removeAllViews()
            welcomeLayout.visibility = View.VISIBLE
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "New Chat Started", Toast.LENGTH_SHORT).show()
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
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Settings Clicked", Toast.LENGTH_SHORT).show()
        }

        menuLogin.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            Toast.makeText(this, "Login Clicked", Toast.LENGTH_SHORT).show()
        }

        // Request Audio Permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
        }
        
        setupDynamicSendButton()
        setupSpeechRecognizer()

        // Handle Send and Live Mode button click
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
                // Open Live Mode Window when tag is "live"
                val intent = Intent(this@MainActivity, LiveActivity::class.java)
                startActivity(intent)
            }
        }
        
        // Standard Mic Button Click
        voiceBtn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startListeningWithoutBeep()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_CODE_PERMISSION)
            }
        }
    }

    // Toggle between Live Wave icon and Send icon dynamically
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
        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        muteBeepSound(true)
        speechRecognizer.startListening(speechRecognizerIntent)
    }

    private fun muteBeepSound(mute: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val adjust = if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
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
        jsonBody.put("model", "llama-3.1-8b-instant") 
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
