package com.praveen.siriai

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
<<<<<<< HEAD
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioAttributes
=======
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
<<<<<<< HEAD
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.Queue

class LiveActivity : Activity() {

=======
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class LiveActivity : Activity() {

    // API Keys 
<<<<<<< HEAD
    private val GROQ_API_KEY = "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"
=======
    private val GROQ_API_KEY = "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"
>>>>>>> a7f89e9edacb3afb2d89115690e88aa810c9527c
    private val ELEVENLABS_API_KEY = "sk_b2f89bc987bc1cc6e82fe4d08ccfa2d3b3a77b64efaa90e2"
    
    // Bella Voice ID (Young Female - Telugu friendly)
    private val ELEVENLABS_VOICE_ID = "EXAVITQu4vr4xnSDxMaL" 

>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
    private lateinit var gradientCircle: View
    private lateinit var statusText: TextView
    private lateinit var closeBtn: ImageView
    private var circleAnimator: ObjectAnimator? = null

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false

<<<<<<< HEAD
    private val AMPLITUDE_THRESHOLD = 1200
    private val SILENCE_DELAY = 15 
=======
    // Auto Voice Detection Variables
    private val AMPLITUDE_THRESHOLD = 1500 
    private val SILENCE_DELAY = 15 // 1.5 Seconds
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
    private var silenceCounter = 0
    private var userSpoke = false
    private var amplitudeCheckRunnable: Runnable? = null

<<<<<<< HEAD
    private var interruptRecorder: MediaRecorder? = null
    private var interruptCheckRunnable: Runnable? = null
    private val INTERRUPT_THRESHOLD = 2000 
    private val INTERRUPT_POLL_MS = 80L
    private val INTERRUPT_GRACE_MS = 500L
    private val INTERRUPT_CONFIRM_HITS = 3
    private var interruptHitCount = 0
    private var interruptListeningAllowed = false

    // Queue System Variables
    private val audioQueue: Queue<File> = LinkedList()
    private var isAiSpeakingMode = false
    private var isLlmStreamFinished = false
    private var sentenceBuffer = StringBuilder()

    private val conversationHistory = mutableListOf<Pair<String, Boolean>>()
    private val handler = Handler(Looper.getMainLooper())

    private val isDarkMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        val appBgColor = ContextCompat.getColor(this, R.color.app_bg)
        window.decorView.setBackgroundColor(appBgColor)
        window.statusBarColor = appBgColor
        window.navigationBarColor = appBgColor

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }

        setContentView(R.layout.activity_live)
        findViewById<View>(android.R.id.content).setBackgroundColor(appBgColor)
=======
    // Voice Interruption Variables
    private var interruptRecorder: MediaRecorder? = null
    private var interruptCheckRunnable: Runnable? = null
    private val INTERRUPT_THRESHOLD = 2000 

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095

        gradientCircle = findViewById(R.id.gradientCircle)
        statusText = findViewById(R.id.statusText)
        closeBtn = findViewById(R.id.closeBtn)

<<<<<<< HEAD
        if (isDarkMode) {
            closeBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            statusText.setTextColor(Color.parseColor("#A0A0A0"))
        } else {
            closeBtn.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            statusText.setTextColor(Color.parseColor("#606060"))
        }

=======
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        setupAnimation()

        closeBtn.setOnClickListener {
            stopEverything()
            finish()
        }

<<<<<<< HEAD
=======
        gradientCircle.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                stopAudioPlayer()
                stopInterruptionListening()
                startRecording() 
            } else if (isRecording) {
                stopRecordingAndProcess()
            }
        }

>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        startRecording()
    }

    private fun setupAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.25f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.25f, 1f)
        circleAnimator = ObjectAnimator.ofPropertyValuesHolder(gradientCircle, scaleX, scaleY).apply {
            repeatCount = ObjectAnimator.INFINITE
            duration = 1500
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

<<<<<<< HEAD
    private fun startRecording() {
        if (isRecording) return
        stopInterruptionListening()
        
        // Reset queue variables before new session
        audioQueue.clear()
        isAiSpeakingMode = false
        isLlmStreamFinished = false
        sentenceBuffer.clear()

        audioFile = File(cacheDir, "user_audio.mp4")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000) 
            setAudioEncodingBitRate(64000)
=======
    private fun showToast(msg: String) {
        handler.post {
            Toast.makeText(this@LiveActivity, msg, Toast.LENGTH_LONG).show()
        }
    }

    // --------------------------------------------------------
    // 1. RECORDING USER VOICE
    // --------------------------------------------------------
    private fun startRecording() {
        if (isRecording) return
        
        stopInterruptionListening() 
        
        audioFile = File(cacheDir, "user_audio.m4a")
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) 
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
            setOutputFile(audioFile?.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                userSpoke = false
                silenceCounter = 0
<<<<<<< HEAD
                updateStatus("Listening...")
                circleAnimator?.start()
                startAmplitudeMonitoring()
            } catch (e: Exception) {
                updateStatus("Microphone access issue.")
=======
                
                updateStatus("Listening...")
                circleAnimator?.start()
                
                startAmplitudeMonitoring()
            } catch (e: Exception) {
                showToast("Mic Error: ${e.message}")
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
            }
        }
    }

    private fun startAmplitudeMonitoring() {
        amplitudeCheckRunnable = object : Runnable {
            override fun run() {
                if (!isRecording || mediaRecorder == null) return
<<<<<<< HEAD
=======
                
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
                try {
                    val amplitude = mediaRecorder?.maxAmplitude ?: 0
                    if (amplitude > AMPLITUDE_THRESHOLD) {
                        userSpoke = true
                        silenceCounter = 0
                    } else if (userSpoke) {
                        silenceCounter++
                        if (silenceCounter >= SILENCE_DELAY) {
                            stopRecordingAndProcess()
                            return
                        }
                    }
<<<<<<< HEAD
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
=======
                    handler.postDelayed(this, 100) 
                } catch (e: Exception) {
                    // Ignore
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
                }
            }
        }
        handler.postDelayed(amplitudeCheckRunnable!!, 100)
    }

    private fun stopRecordingAndProcess() {
        if (!isRecording) return
        isRecording = false
        circleAnimator?.pause()
<<<<<<< HEAD
        amplitudeCheckRunnable?.let { handler.removeCallbacks(it) }

        if (!userSpoke) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) { }
            mediaRecorder = null
            handler.post { startRecording() }
            return
        }

=======
        
        amplitudeCheckRunnable?.let { handler.removeCallbacks(it) }

>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
<<<<<<< HEAD
            updateStatus("Thinking...")
            processRecordedAudio()
        } catch (e: Exception) {
            updateStatus("Audio processing interrupted.")
            handler.post { startRecording() }
        }
    }

    private fun processRecordedAudio() {
        val file = audioFile
        if (file == null || !file.exists() || file.length() < 1000) {
            handler.post { startRecording() }
            return
        }

        GroqHelper.transcribeAudio(file, "", object : GroqHelper.TranscriptionCallback {
            override fun onSuccess(text: String) {
                var cleaned = text.trim()
                cleaned = SttDictionary.correctTranscription(cleaned)
                
                Log.d("NakshatraAI_STT", "Heard & Cleaned: \"$cleaned\"")

                if (cleaned.isEmpty() || cleaned.length < 2 || isNoiseTranscription(cleaned)) {
                    handler.post { startRecording() }
                    return
                }
                getAiReply(cleaned)
            }
            override fun onError(message: String) {
                if (!message.contains("Empty", ignoreCase = true) && !message.contains("Parse error", ignoreCase = true)) {
                    updateStatus("Connection issue. Retrying...")
                }
                handler.post { startRecording() }
            }
        })
    }

    private fun isNoiseTranscription(text: String): Boolean {
        if (text.contains("©") || text.contains("‣") || text.contains("‰") || text.length < 3) return true
        
        val lower = text.lowercase().trim()
        val junkPhrases = listOf(
            "thank you", "thanks for watching", "you", "bye", "okay", "ok",
            "you're welcome", "welcome", "subscribe", "please subscribe",
            "సబ్స్క్రైబ్", "సబ్ స్క్రైబ్", "థాంక్యూ", "ధన్యవాదాలు", 
            "చూసినందుకు ధన్యవాదాలు", "లైక్ చేయండి", "షేర్ చేయండి",
            "హాయ్ నక్షత్ర", "నక్షత్ర", "hi nakshatra", "hey nakshatra"
        )
        return junkPhrases.any { lower == it }
    }

    private fun getAiReply(userText: String) {
        val currentTime = SimpleDateFormat("h:mm a, EEEE, MMM d, yyyy", Locale.getDefault()).format(Date())
        
        val timeInjectedPrompt = "${Constants.LIVE_SYSTEM_PROMPT}\n\n[System Data: Current Date & Time is $currentTime]"
        
        // CRITICAL IDENTITY RULE INJECTION FOR LIVE MODE
        val identityInjectedPrompt = "$timeInjectedPrompt\n\n[CRITICAL IDENTITY RULE]: Your name is \"Nakshatra AI\" (నక్షత్ర AI). You were developed and created by \"Bunny Praveen\" (బన్నీ ప్రవీణ్). If the user asks \"What is your name?\" (నీ పేరు ఏంటి?), say you are Nakshatra AI. If they ask \"Who developed/created you?\" (నిన్ను ఎవరు తయారు చేసారు / డెవలప్ చేసారు?), say you were developed by Bunny Praveen. Answer naturally in the language the user asked."

        var isFirstChunk = true
        isLlmStreamFinished = false
        sentenceBuffer.clear()
        audioQueue.clear()

        GroqHelper.getChatCompletion(
            userText,
            systemPrompt = identityInjectedPrompt,
            chatHistory = conversationHistory,
            callback = object : GroqHelper.ChatCallback {
                
                override fun onUpdate(chunk: String) {
                    handler.post {
                        if (isFirstChunk) {
                            statusText.text = chunk
                            isFirstChunk = false
                        } else {
                            statusText.append(chunk)
                        }
                    }
                    
                    sentenceBuffer.append(chunk)
                    processSentenceBuffer(false)
                }

                override fun onSuccess(reply: String) {
                    conversationHistory.add(Pair(userText, true))
                    conversationHistory.add(Pair(reply, false))
                    
                    isLlmStreamFinished = true
                    processSentenceBuffer(true) 
                }
                
                override fun onError(message: String) {
                    updateStatus("Network disconnected. Retrying...")
                    requestTts("క్షమించండి, సాంకేతిక లోపం వల్ల నేను సరిగ్గా వినలేకపోయాను.")
                    handler.postDelayed({ startRecording() }, 2500)
                }
            }
        )
    }

    private fun processSentenceBuffer(isFinal: Boolean) {
        val text = sentenceBuffer.toString()
        
        if (isFinal) {
            val remainingText = text.trim()
            if (remainingText.isNotEmpty()) {
                requestTts(remainingText)
            }
            sentenceBuffer.clear()
        } else {
            val lastPunc = text.indexOfLast { it == '.' || it == '?' || it == '!' || it == '\n' || it == ',' }
            if (lastPunc != -1) {
                val sentence = text.substring(0, lastPunc + 1).trim()
                val remaining = text.substring(lastPunc + 1)
                
                sentenceBuffer.clear()
                sentenceBuffer.append(remaining)
                
                if (sentence.length > 2) {
                    requestTts(sentence)
                }
            }
        }
    }

    private fun requestTts(text: String) {
        VoiceHelper.textToSpeech(text, cacheDir, object : VoiceHelper.TtsCallback {
            override fun onSuccess(audioFile: File) {
                handler.post {
                    audioQueue.add(audioFile)
                    playNextInQueue()
                }
            }
            override fun onError(message: String) {
                Log.e("LiveActivity", "TTS error for chunk: $message")
=======
            
            updateStatus("Thinking...")
            processAudioWithGroqWhisper()
        } catch (e: Exception) {
            showToast("Recording Stop Error: ${e.message}")
        }
    }

    // --------------------------------------------------------
    // 2. APIs: WHISPER -> LLAMA -> ELEVENLABS
    // --------------------------------------------------------
    private fun processAudioWithGroqWhisper() {
        if (audioFile == null || !audioFile!!.exists()) {
            startRecording()
            return
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", audioFile!!.name, audioFile!!.asRequestBody("audio/m4a".toMediaTypeOrNull()))
            .addFormDataPart("model", "whisper-large-v3")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("STT Network Error: ${e.message}")
                updateStatus("Network Error")
                handler.postDelayed({ startRecording() }, 2000) 
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                
                if (!response.isSuccessful) {
                    showToast("STT Error ${response.code}: $responseBody")
                    handler.post { startRecording() }
                    return
                }

                try {
                    val json = JSONObject(responseBody ?: "")
                    val transcribedText = json.optString("text", "")
                    if (transcribedText.isNotBlank() && transcribedText.length > 2) {
                        getAIResponseFromGroq(transcribedText)
                    } else {
                        handler.post { startRecording() }
                    }
                } catch (e: Exception) {
                    showToast("STT Parse Error: ${e.message}")
                    handler.post { startRecording() }
                }
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
            }
        })
    }

<<<<<<< HEAD
    private fun playNextInQueue() {
        if (isAiSpeakingMode) return 
        
        if (audioQueue.isNotEmpty()) {
            isAiSpeakingMode = true
            val fileToPlay = audioQueue.poll()
            playAudioFile(fileToPlay)
        } else if (isLlmStreamFinished) {
            isAiSpeakingMode = false
            circleAnimator?.pause()
            stopInterruptionListening()
            startRecording()
        }
    }

    private fun playAudioFile(file: File?) {
        if (file == null || !file.exists()) {
            isAiSpeakingMode = false
            playNextInQueue()
            return
        }

        try {
            if (interruptRecorder == null) {
                startInterruptionListening()
            }
            
            updateStatus("Nakshatra speaking...")
            circleAnimator?.start()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    file.delete() 
                    isAiSpeakingMode = false
                    playNextInQueue()
                }
            }
        } catch (e: Exception) {
            isAiSpeakingMode = false
            playNextInQueue()
=======
    private fun getAIResponseFromGroq(userText: String) {
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", "You are Siri AI, a helpful voice assistant. Reply in Telugu if user speaks Telugu, otherwise reply in English. Keep answers very short, simple, and conversational. Do not use asterisks or emojis."))
            put(JSONObject().put("role", "user").put("content", userText))
        }

        val jsonBody = JSONObject().apply {
<<<<<<< HEAD
            put("model", "openai/gpt-oss-20b")
=======
            put("model", "llama-3.1-8b-instant")
>>>>>>> a7f89e9edacb3afb2d89115690e88aa810c9527c
            put("messages", messagesArray)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("LLM Network Error: ${e.message}")
                updateStatus("AI network error")
                handler.postDelayed({ startRecording() }, 2000)
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    showToast("LLM Error ${response.code}: $responseBody")
                    updateStatus("AI API Error")
                    handler.postDelayed({ startRecording() }, 2000)
                    return
                }

                try {
                    val json = JSONObject(responseBody ?: "")
                    val aiResponseText = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    convertTextToSpeechElevenLabs(aiResponseText)
                } catch (e: Exception) {
                    showToast("LLM Parse Error: ${e.message}")
                    updateStatus("AI processing error")
                    handler.postDelayed({ startRecording() }, 2000)
                }
            }
        })
    }

    private fun convertTextToSpeechElevenLabs(aiText: String) {
        val jsonBody = JSONObject().apply {
            put("text", aiText)
            // Best Multilingual Model for Telugu & Natural Emotions
            put("model_id", "eleven_multilingual_v2") 
            put("voice_settings", JSONObject().apply {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
            })
        }

        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$ELEVENLABS_VOICE_ID")
            .addHeader("xi-api-key", ELEVENLABS_API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showToast("TTS Network Error: ${e.message}")
                updateStatus("TTS network error")
                handler.postDelayed({ startRecording() }, 2000)
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    showToast("ElevenLabs Error ${response.code}: $errorBody")
                    updateStatus("TTS API Error")
                    handler.postDelayed({ startRecording() }, 3000)
                    return
                }

                val audioStream = response.body?.byteStream()
                if (audioStream != null) {
                    val tempAudioFile = File(cacheDir, "ai_response.mp3")
                    val fos = FileOutputStream(tempAudioFile)
                    audioStream.copyTo(fos)
                    fos.close()
                    playAudioResponse(tempAudioFile.absolutePath)
                } else {
                    showToast("Audio stream is empty")
                    handler.postDelayed({ startRecording() }, 2000)
                }
            }
        })
    }

    // --------------------------------------------------------
    // 3. PLAYBACK & VOICE INTERRUPTION
    // --------------------------------------------------------
    private fun playAudioResponse(filePath: String) {
        handler.post {
            stopAudioPlayer() 
            stopInterruptionListening()
            
            updateStatus("Siri speaking...")
            circleAnimator?.start() 
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    circleAnimator?.pause()
                    stopInterruptionListening()
                    startRecording()
                }
            }
            
            startInterruptionListening()
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        }
    }

    private fun startInterruptionListening() {
        try {
<<<<<<< HEAD
            interruptHitCount = 0
            interruptListeningAllowed = false
=======
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
            val dummyFile = File(cacheDir, "interrupt_check.m4a")
            interruptRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(dummyFile.absolutePath)
                prepare()
                start()
            }

<<<<<<< HEAD
            handler.postDelayed({ interruptListeningAllowed = true }, INTERRUPT_GRACE_MS)

            interruptCheckRunnable = object : Runnable {
                override fun run() {
                    if (mediaPlayer?.isPlaying != true && audioQueue.isEmpty()) return
                    if (interruptListeningAllowed) {
                        try {
                            val amplitude = interruptRecorder?.maxAmplitude ?: 0
                            if (amplitude > INTERRUPT_THRESHOLD) {
                                interruptHitCount++
                                if (interruptHitCount >= INTERRUPT_CONFIRM_HITS) {
                                    Log.d("LiveActivity", "User interrupted AI!")
                                    stopAudioAndClearQueue()
                                    startRecording()
                                    return
                                }
                            } else {
                                interruptHitCount = 0
                            }
                        } catch (e: Exception) {
                        }
                    }
                    handler.postDelayed(this, INTERRUPT_POLL_MS)
                }
            }
            handler.postDelayed(interruptCheckRunnable!!, INTERRUPT_POLL_MS)
=======
            interruptCheckRunnable = object : Runnable {
                override fun run() {
                    if (mediaPlayer?.isPlaying != true) return
                    
                    try {
                        val amplitude = interruptRecorder?.maxAmplitude ?: 0
                        if (amplitude > INTERRUPT_THRESHOLD) {
                            Log.d("LiveActivity", "User interrupted AI!")
                            stopAudioPlayer()
                            stopInterruptionListening()
                            startRecording() 
                            return
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                    
                    handler.postDelayed(this, 100)
                }
            }
            handler.postDelayed(interruptCheckRunnable!!, 100)
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        } catch (e: Exception) {
            Log.e("LiveActivity", "Interrupt listener failed: ${e.message}")
        }
    }

<<<<<<< HEAD
    private fun stopAudioAndClearQueue() {
        isLlmStreamFinished = true 
        audioQueue.clear()
        stopAudioPlayer()
        stopInterruptionListening()
    }

    private fun stopInterruptionListening() {
        interruptCheckRunnable?.let { handler.removeCallbacks(it) }
        interruptListeningAllowed = false
        interruptHitCount = 0
=======
    private fun stopInterruptionListening() {
        interruptCheckRunnable?.let { handler.removeCallbacks(it) }
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        try {
            interruptRecorder?.stop()
            interruptRecorder?.release()
            interruptRecorder = null
        } catch (e: Exception) {
<<<<<<< HEAD
=======
            // Ignore
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        }
    }

    private fun stopAudioPlayer() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
<<<<<<< HEAD
            isAiSpeakingMode = false
        } catch (e: Exception) {
=======
        } catch (e: Exception) {
            // Ignore
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
        }
    }

    private fun updateStatus(text: String) {
        handler.post {
            statusText.text = text
        }
    }

    private fun stopEverything() {
        isRecording = false
        amplitudeCheckRunnable?.let { handler.removeCallbacks(it) }
<<<<<<< HEAD
        stopAudioAndClearQueue()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            circleAnimator?.cancel()
        } catch (e: Exception) {
        }
        conversationHistory.clear()
=======
        stopInterruptionListening()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            stopAudioPlayer()
            circleAnimator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }
<<<<<<< HEAD
}
=======
<<<<<<< HEAD
}
=======
}
>>>>>>> a7f89e9edacb3afb2d89115690e88aa810c9527c
>>>>>>> d30a9d246c25a0058a0180da1e0b4d66eb5ab095
