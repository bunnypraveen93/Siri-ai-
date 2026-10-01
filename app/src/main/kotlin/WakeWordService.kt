package com.praveen.siriai

import android.Manifest
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.app.Service
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import java.util.Locale

class WakeWordService : Service(), RecognitionListener, TextToSpeech.OnInitListener {

    private var model: Model? = null
    private var wakeRecognizer: Recognizer? = null
    private var wakeSpeechService: SpeechService? = null
    private var mediaRecorder: MediaRecorder? = null
    private var listeningForCommand = false
    private var wakeHandled = false

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val handler = Handler(Looper.getMainLooper())
    private val silenceHandler = Handler(Looper.getMainLooper())
    private var silenceRunnable: Runnable? = null

    private val okHttpClient = OkHttpClient()

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var rgbAnimator: ValueAnimator? = null

    companion object {
        private const val CHANNEL_ID = "nakshatra_ai_wake_word_channel"
        private const val NOTIFICATION_ID = 501
        private const val SAMPLE_RATE = 16000.0f

        // Wake phrases updated to Nakshatra
        private val WAKE_PHRASES = listOf("hey nakshatra ai", "hey nakshatra", "ok nakshatra ai", "ok nakshatra", "nakshatra ai")
        private val WAKE_ACKS = listOf("Yes boss, tell me!", "Yeah, tell me boss!", "Yes boss, I'm listening!")

        const val DEBUG_MODE = true

        @Volatile var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        System.setProperty("jna.nosys", "true")
        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification("మోడల్ లోడ్ అవుతోంది..."))
        
        textToSpeech = TextToSpeech(this, this)
        
        loadModel()
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            textToSpeech?.language = Locale.getDefault()
            
            textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    when (utteranceId) {
                        "WakeAck" -> handler.post { if (isRunning) startWhisperRecording() }
                        "FinalAnswer" -> handler.post {
                            if (isRunning) {
                                hideRGBOverlay()
                                startWakeListening(250L)
                            }
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    handler.post {
                        if (!isRunning) return@post
                        hideRGBOverlay()
                        startWakeListening(250L)
                    }
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    private fun loadModel() {
        try {
            val files = assets.list("model")
            if (files == null || files.isEmpty()) {
                debugToast("assets/model ఖాళీగా ఉంది లేదా లేదు")
                stopSelf()
                return
            }
        } catch (e: Exception) {
            debugToast("assets/model చదవలేకపోయాం: ${e.message}")
            stopSelf()
            return
        }

        StorageService.unpack(
            this, "model", "model",
            { loadedModel ->
                debugToast("Vosk model లోడ్ అయ్యింది ✓")
                model = loadedModel
                initWakeRecognizer()
                startWakeListening()
            },
            { exception ->
                debugToast("Vosk model load ఫెయిల్: ${exception.message}")
                stopSelf()
            }
        )
    }

    private fun initWakeRecognizer() {
        val m = model ?: return
        val grammar = JSONArray(WAKE_PHRASES + "[unk]").toString()
        val recognizer = Recognizer(m, SAMPLE_RATE, grammar)
        recognizer.setWords(false)
        recognizer.setPartialWords(false)
        wakeRecognizer = recognizer
        wakeSpeechService = SpeechService(recognizer, SAMPLE_RATE)
    }

    private fun buildNotification(text: String): android.app.Notification {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Nakshatra AI Wake Word", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Nakshatra AI")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun debugToast(text: String) {
        if (!DEBUG_MODE) return
        handler.post { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
    }

    // ==========================================
    // RGB Capsule Overlay Logic
    // ==========================================
    private fun showRGBOverlay() {
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    debugToast("Overlay Permission లేదు! సెట్టింగ్స్ చెక్ చేయండి.")
                    return@post
                }

                if (overlayView != null) {
                    overlayView?.visibility = View.VISIBLE
                    return@post
                }

                windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

                val windowParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                )

                windowParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                windowParams.y = 200

                val capsuleLayout = object : LinearLayout(this) {

                    private val strokeThickness = 6f * resources.displayMetrics.density
                    private val glowRadius = 16f * resources.displayMetrics.density

                    private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = strokeThickness * 2.5f
                        maskFilter = android.graphics.BlurMaskFilter(glowRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    }

                    private val solidPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = strokeThickness
                    }

                    private val innerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                        color = Color.parseColor("#1A1A1A")
                    }

                    private var rotationAngle = 0f
                    private val rectF = android.graphics.RectF()
                    private val matrix = android.graphics.Matrix()

                    private var rotationAnimator: ValueAnimator? = null

                    init {
                        setWillNotDraw(false)
                        setLayerType(LAYER_TYPE_SOFTWARE, null)
                        setBackgroundColor(Color.TRANSPARENT)

                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER

                        val safeInset = (glowRadius + strokeThickness).toInt()
                        val textPaddingHor = (32 * resources.displayMetrics.density).toInt()
                        val textPaddingVer = (12 * resources.displayMetrics.density).toInt()

                        setPadding(safeInset + textPaddingHor, safeInset + textPaddingVer, safeInset + textPaddingHor, safeInset + textPaddingVer)

                        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                            duration = 1500
                            repeatCount = ValueAnimator.INFINITE
                            interpolator = android.view.animation.LinearInterpolator()
                            addUpdateListener {
                                rotationAngle = it.animatedValue as Float
                                invalidate()
                            }
                            start()
                        }
                    }

                    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                        super.onSizeChanged(w, h, oldw, oldh)
                        val cx = w / 2f
                        val cy = h / 2f

                        val colors = intArrayOf(
                            Color.parseColor("#00FFFF"),
                            Color.parseColor("#FF00FF"),
                            Color.parseColor("#FFFF00"),
                            Color.parseColor("#39FF14"),
                            Color.parseColor("#00FFFF")
                        )
                        val gradient = android.graphics.SweepGradient(cx, cy, colors, null)
                        glowPaint.shader = gradient
                        solidPaint.shader = gradient

                        val inset = glowRadius + (strokeThickness / 2f)
                        rectF.set(inset, inset, w - inset, h - inset)
                    }

                    override fun onDraw(canvas: android.graphics.Canvas) {
                        glowPaint.shader?.let { shader ->
                            matrix.setRotate(rotationAngle, width / 2f, height / 2f)
                            shader.setLocalMatrix(matrix)
                            solidPaint.shader?.setLocalMatrix(matrix)
                        }

                        canvas.drawRoundRect(rectF, 100f, 100f, glowPaint)
                        canvas.drawRoundRect(rectF, 100f, 100f, solidPaint)
                        canvas.drawRoundRect(rectF, 100f, 100f, innerPaint)

                        super.onDraw(canvas)
                    }

                    override fun onDetachedFromWindow() {
                        super.onDetachedFromWindow()
                        rotationAnimator?.cancel()
                    }
                }

                val textView = TextView(this).apply {
                    text = "Listening..."
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                }

                capsuleLayout.addView(textView)
                overlayView = capsuleLayout

                windowManager?.addView(overlayView, windowParams)

                rgbAnimator = ValueAnimator.ofFloat(0.96f, 1.04f).apply {
                    duration = 1000
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { animator ->
                        val scale = animator.animatedValue as Float
                        capsuleLayout.scaleX = scale
                        capsuleLayout.scaleY = scale
                    }
                    start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                debugToast("UI క్రియేషన్ ఎర్రర్: ${e.message}")
            }
        }
    }

    private fun setOverlayText(text: String) {
        handler.post {
            val tv = (overlayView as? LinearLayout)?.getChildAt(0) as? TextView
            tv?.text = text
        }
    }

    private fun hideRGBOverlay() {
        handler.post {
            try {
                rgbAnimator?.cancel()
                rgbAnimator = null
                if (overlayView != null) {
                    windowManager?.removeView(overlayView)
                    overlayView = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ==========================================
    // VOICE HANDLING LOGIC
    // ==========================================
    private fun startWakeListening(delayMs: Long = 0L) {
        listeningForCommand = false
        wakeHandled = false
        updateNotification("వేక్ వర్డ్ కోసం వింటోంది... (\"Hey Nakshatra AI\")")

        val resume = Runnable {
            if (!isRunning) return@Runnable
            wakeRecognizer?.reset()
            wakeSpeechService?.startListening(this)
        }
        if (delayMs > 0) handler.postDelayed(resume, delayMs) else resume.run()
    }

    private fun startCommandListening() {
        wakeSpeechService?.stop()

        listeningForCommand = true
        updateNotification("వింటోంది... ఇప్పుడు చెప్పు")

        showRGBOverlay()
        setOverlayText("Speaking...")

        if (isTtsInitialized && textToSpeech != null) {
            val randomAck = WAKE_ACKS.random()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.speak(randomAck, TextToSpeech.QUEUE_FLUSH, null, "WakeAck")
            } else {
                val params = java.util.HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "WakeAck"
                @Suppress("DEPRECATION")
                textToSpeech?.speak(randomAck, TextToSpeech.QUEUE_FLUSH, params)
            }
        } else {
            handler.postDelayed({
                if (isRunning) startWhisperRecording()
            }, 300) 
        }
    }

    private fun startWhisperRecording() {
        val audioFile = File(cacheDir, "whisper_command.m4a")
        try {
            if (audioFile.exists()) audioFile.delete()

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start() 
            }
            
            setOverlayText("Listening...")

            var currentSilenceDuration = 0L
            var totalRecordingTime = 0L
            var hasDetectedSpeech = false
            
            // Optimized timings for fast response
            val pollInterval = 50L 
            val speechAmplitudeThreshold = 250 
            val maxSilenceAllowed = 1500L 
            val maxWaitForSpeechStart = 4000L
            val maxRecordingLimit = 12000L
            
            silenceRunnable = object : Runnable {
                override fun run() {
                    if (!listeningForCommand || mediaRecorder == null) return

                    try {
                        val amplitude = mediaRecorder?.maxAmplitude ?: 0
                        totalRecordingTime += pollInterval

                        if (amplitude > speechAmplitudeThreshold) {
                            hasDetectedSpeech = true
                            currentSilenceDuration = 0L 
                        } else if (hasDetectedSpeech) {
                            currentSilenceDuration += pollInterval 
                        }

                        val shouldStop = when {
                            hasDetectedSpeech && currentSilenceDuration >= maxSilenceAllowed -> true
                            !hasDetectedSpeech && totalRecordingTime >= maxWaitForSpeechStart -> true
                            totalRecordingTime >= maxRecordingLimit -> true
                            else -> false
                        }

                        if (shouldStop) {
                            silenceHandler.removeCallbacks(this)
                            stopAndTranscribeWhisper(audioFile)
                        } else {
                            silenceHandler.postDelayed(this, pollInterval)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        silenceHandler.removeCallbacks(this)
                        stopAndTranscribeWhisper(audioFile)
                    }
                }
            }
            silenceHandler.postDelayed(silenceRunnable!!, pollInterval)

        } catch (e: Exception) {
            debugToast("Recording ఫెయిల్: ${e.message}")
            listeningForCommand = false
            hideRGBOverlay()
            startWakeListening(250L)
        }
    }

    private fun stopAndTranscribeWhisper(audioFile: File) {
        if (!isRunning || !listeningForCommand) return

        silenceHandler.removeCallbacksAndMessages(null)
        
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setOverlayText("Processing...")
        updateNotification("ప్రాసెస్ చేస్తోంది...")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            )
            .addFormDataPart("model", Constants.GROQ_WHISPER_MODEL)
            .addFormDataPart("temperature", "0.0")
            .addFormDataPart(
                "prompt",
                "Voice commands for a Telugu-English mobile assistant app. " +
                    "Examples: open WhatsApp, call Ravi, set an alarm for 7 AM, " +
                    "set a timer for 10 minutes, play song Naatu Naatu, ఓపెన్ చెయ్యి, కాల్ చెయ్యి, turn on wifi, turn on bluetooth."
            )
            .build()

        val request = Request.Builder()
            .url(Constants.GROQ_TRANSCRIBE_URL)
            .header("Authorization", "Bearer ${Constants.GROQ_API_KEY}")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    debugToast("Whisper API ఎర్రర్: ${e.message}")
                    listeningForCommand = false
                    hideRGBOverlay() 
                    startWakeListening(250L)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                handler.post {
                    listeningForCommand = false
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val text = JSONObject(responseBody).optString("text").trim()
                            val lowerText = text.lowercase(Locale.getDefault())
                            val cleanText = lowerText.replace(Regex("[.,!?]"), "").trim()
                            val isHallucination = cleanText.matches(Regex("^(thank you|thanks for watching|end|bye|)$"))

                            if (cleanText.isNotBlank() && !isHallucination) {
                                debugToast("కమాండ్: \"$cleanText\"")
                                val handledAsQuickAction = handleVoiceCommand(text)
                                if (handledAsQuickAction) {
                                    hideRGBOverlay()
                                    startWakeListening(250L)
                                }
                                return@post
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    hideRGBOverlay()
                    startWakeListening(250L)
                }
            }
        })
    }
    
    // ==========================================
    // Voice Command Router
    // ==========================================
    private fun handleVoiceCommand(rawCommand: String): Boolean {
        val lower = rawCommand.lowercase(Locale.getDefault()).trim()

        if (lower.contains("bluetooth")) {
            if (openSystemSetting(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")) return true
        }
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            if (openSystemSetting(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")) return true
        }
        if (lower.contains("mobile data") || lower.contains("internet")) {
            if (openSystemSetting(Settings.ACTION_DATA_ROAMING_SETTINGS, "Mobile Data")) return true
        }

        if (lower.contains("timer")) {
            if (trySetTimer(lower)) return true
        }

        if (lower.contains("alarm")) {
            if (trySetAlarm(lower)) return true
        }

        if (Regex("\\bcall\\b").containsMatchIn(lower)) {
            if (tryMakeCall(lower)) return true
        }

        if (lower.contains("play ") || lower.contains(" song") || lower.startsWith("song")) {
            val songName = lower.replace("play", "").replace("song", "").trim()
            if (songName.isNotBlank()) {
                playSongViaYouTube(songName)
                return false 
            }
        }

        if (tryOpenAnyApp(lower)) return true

        askAIAndSpeak(rawCommand)
        return false
    }

    private fun openSystemSetting(action: String, name: String): Boolean {
        return try {
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            debugToast("$name సెట్టింగ్స్ ఓపెన్ చేస్తున్నాను...")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun trySetAlarm(lower: String): Boolean {
        val match = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower) ?: return false
        var hour = match.groupValues[1].toIntOrNull() ?: return false
        val minute = match.groupValues[2].toIntOrNull() ?: 0
        val meridian = match.groupValues[3]

        if (hour !in 0..23) return false
        if (meridian == "pm" && hour < 12) hour += 12
        if (meridian == "am" && hour == 12) hour = 0

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            debugToast("అలారం పెట్టాను: %02d:%02d".format(hour, minute))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun trySetTimer(lower: String): Boolean {
        val match = Regex("(\\d+)\\s*(hours?|hrs?|minutes?|mins?|seconds?|secs?)").find(lower) ?: return false
        val amount = match.groupValues[1].toIntOrNull() ?: return false
        val unit = match.groupValues[2]

        val seconds = when {
            unit.startsWith("hour") || unit.startsWith("hr") -> amount * 3600
            unit.startsWith("min") -> amount * 60
            else -> amount
        }
        if (seconds <= 0) return false

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            debugToast("టైమర్ పెట్టాను: $amount $unit")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun tryMakeCall(lower: String): Boolean {
        var name = lower.substringAfter("call").trim()
        name = name.removePrefix("to").trim().removeSuffix("ki").trim()
        if (name.isBlank()) return false

        val phoneNumber = lookupContactNumber(name) ?: run {
            debugToast("\"$name\" కాంటాక్ట్ దొరకలేదు")
            return false
        }

        return try {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            debugToast("$name కి కాల్ చేస్తున్నాను...")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun lookupContactNumber(name: String): String? {
        val hasReadContacts = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasReadContacts) return null

        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val contactName = cursor.getString(nameIndex) ?: continue
                if (contactName.lowercase(Locale.getDefault()).contains(name)) {
                    return cursor.getString(numberIndex)
                }
            }
        }
        return null
    }

    private fun playSongViaYouTube(songName: String) {
        setOverlayText("Searching...")
        updateNotification("పాట వెతుకుతోంది...")

        YouTubeHelper.search(this, songName, "video") { videoId ->
            val targetUri = if (videoId != null) {
                "https://www.youtube.com/watch?v=$videoId"
            } else {
                "https://www.youtube.com/results?search_query=${Uri.encode(songName)}"
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUri)).apply {
                    setPackage("com.google.android.youtube")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                if (videoId != null) {
                    debugToast("\"$songName\" ప్లే చేస్తున్నాను...")
                }
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(targetUri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(fallback)
                } catch (e2: Exception) {
                    e2.printStackTrace()
                }
            }

            hideRGBOverlay()
            startWakeListening(250L)
        }
    }

    private fun tryOpenAnyApp(lower: String): Boolean {
        val triggers = listOf("open", "launch", "start")
        val hasTrigger = triggers.any { lower.contains(it) }
        if (!hasTrigger) return false

        var appName = lower
        triggers.forEach { appName = appName.replace(it, "") }
        appName = appName.trim()
        if (appName.isBlank()) return false

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        val matched = apps.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase(Locale.getDefault())
            label.contains(appName) || appName.contains(label)
        } ?: return false

        return try {
            val launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                debugToast("${matched.loadLabel(pm)} ఓపెన్ చేస్తున్నాను...")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun askAIAndSpeak(userText: String) {
        updateNotification("ఆలోచిస్తోంది...")
        
        setOverlayText("Thinking...")

        // [CRITICAL IDENTITY RULE] యాడ్ చేశాను
        val finalPrompt = "${AssistantSystemPrompt.PROMPT}\n\n[CRITICAL IDENTITY RULE]: Your name is \"Nakshatra AI\" (నక్షత్ర AI). You were developed and created by \"Bunny Praveen\" (బన్నీ ప్రవీణ్). If the user asks \"What is your name?\", say you are Nakshatra AI. Answer naturally in the language the user asked."

        GroqHelper.getChatCompletion(
            userText = userText,
            systemPrompt = finalPrompt,
            chatHistory = mutableListOf(),
            callback = object : GroqHelper.ChatCallback {
                override fun onSuccess(reply: String) {
                    handler.post { speakFinalAnswer(reply) }
                }

                override fun onError(message: String) {
                    handler.post { speakFinalAnswer("I am having trouble connecting. Please check your internet.") }
                }
            }
        )
    }

    private fun speakFinalAnswer(text: String) {
        
        setOverlayText("Speaking...")
        updateNotification("సమాధానం చెప్తోంది...")

        if (isTtsInitialized && textToSpeech != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "FinalAnswer")
            } else {
                val params = java.util.HashMap<String, String>()
                params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = "FinalAnswer"
                @Suppress("DEPRECATION")
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params)
            }
        } else {
            hideRGBOverlay()
            startWakeListening(250L)
        }
    }

    override fun onResult(text: String?) {
        if (text == null) return
        try {
            val json = JSONObject(text)
            val recognized = json.optString("text", "")
            if (WAKE_PHRASES.any { recognized.contains(it) }) {
                if (!wakeHandled) {
                    wakeHandled = true
                    startCommandListening()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPartialResult(text: String?) {
    }

    override fun onFinalResult(text: String?) {
    }

    override fun onError(e: Exception?) {
        startWakeListening(250L)
    }

    override fun onTimeout() {
        startWakeListening(250L)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        hideRGBOverlay()
        
        if (textToSpeech != null) {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        }
        
        wakeSpeechService?.stop()
        wakeSpeechService?.shutdown()
        model?.close()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
