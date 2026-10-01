package com.praveen.siriai

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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

    private lateinit var gradientCircle: View
    private lateinit var statusText: TextView
    private lateinit var closeBtn: ImageView
    private var circleAnimator: ObjectAnimator? = null

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val AMPLITUDE_THRESHOLD = 1200
    private val SILENCE_DELAY = 15 
    private var silenceCounter = 0
    private var userSpoke = false
    private var amplitudeCheckRunnable: Runnable? = null

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

        gradientCircle = findViewById(R.id.gradientCircle)
        statusText = findViewById(R.id.statusText)
        closeBtn = findViewById(R.id.closeBtn)

        if (isDarkMode) {
            closeBtn.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            statusText.setTextColor(Color.parseColor("#A0A0A0"))
        } else {
            closeBtn.setColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN)
            statusText.setTextColor(Color.parseColor("#606060"))
        }

        setupAnimation()

        closeBtn.setOnClickListener {
            stopEverything()
            finish()
        }

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
            setOutputFile(audioFile?.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                userSpoke = false
                silenceCounter = 0
                updateStatus("Listening...")
                circleAnimator?.start()
                startAmplitudeMonitoring()
            } catch (e: Exception) {
                updateStatus("Microphone access issue.")
            }
        }
    }

    private fun startAmplitudeMonitoring() {
        amplitudeCheckRunnable = object : Runnable {
            override fun run() {
                if (!isRecording || mediaRecorder == null) return
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
                    handler.postDelayed(this, 100)
                } catch (e: Exception) {
                }
            }
        }
        handler.postDelayed(amplitudeCheckRunnable!!, 100)
    }

    private fun stopRecordingAndProcess() {
        if (!isRecording) return
        isRecording = false
        circleAnimator?.pause()
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

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
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
            }
        })
    }

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
        }
    }

    private fun startInterruptionListening() {
        try {
            interruptHitCount = 0
            interruptListeningAllowed = false
            val dummyFile = File(cacheDir, "interrupt_check.m4a")
            interruptRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(dummyFile.absolutePath)
                prepare()
                start()
            }

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
        } catch (e: Exception) {
            Log.e("LiveActivity", "Interrupt listener failed: ${e.message}")
        }
    }

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
        try {
            interruptRecorder?.stop()
            interruptRecorder?.release()
            interruptRecorder = null
        } catch (e: Exception) {
        }
    }

    private fun stopAudioPlayer() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
            isAiSpeakingMode = false
        } catch (e: Exception) {
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
        stopAudioAndClearQueue()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            circleAnimator?.cancel()
        } catch (e: Exception) {
        }
        conversationHistory.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }
}
