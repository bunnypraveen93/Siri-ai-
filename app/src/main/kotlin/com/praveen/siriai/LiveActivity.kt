package com.praveen.siriai

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
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
    private val GROQ_API_KEY = "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"
    private val ELEVENLABS_API_KEY = "sk_b2f89bc987bc1cc6e82fe4d08ccfa2d3b3a77b64efaa90e2"
    
    // Bella Voice ID (Young Female - Telugu friendly)
    private val ELEVENLABS_VOICE_ID = "EXAVITQu4vr4xnSDxMaL" 

    private lateinit var gradientCircle: View
    private lateinit var statusText: TextView
    private lateinit var closeBtn: ImageView
    private var circleAnimator: ObjectAnimator? = null

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false

    // Auto Voice Detection Variables
    private val AMPLITUDE_THRESHOLD = 1500 
    private val SILENCE_DELAY = 15 // 1.5 Seconds
    private var silenceCounter = 0
    private var userSpoke = false
    private var amplitudeCheckRunnable: Runnable? = null

    // Voice Interruption Variables
    private var interruptRecorder: MediaRecorder? = null
    private var interruptCheckRunnable: Runnable? = null
    private val INTERRUPT_THRESHOLD = 2000 

    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)

        gradientCircle = findViewById(R.id.gradientCircle)
        statusText = findViewById(R.id.statusText)
        closeBtn = findViewById(R.id.closeBtn)

        setupAnimation()

        closeBtn.setOnClickListener {
            stopEverything()
            finish()
        }

        gradientCircle.setOnClickListener {
            if (mediaPlayer?.isPlaying == true) {
                stopAudioPlayer()
                stopInterruptionListening()
                startRecording() 
            } else if (isRecording) {
                stopRecordingAndProcess()
            }
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
                showToast("Mic Error: ${e.message}")
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
                    // Ignore
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

        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            
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
            }
        })
    }

    private fun getAIResponseFromGroq(userText: String) {
        val messagesArray = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", "You are Siri AI, a helpful voice assistant. Reply in Telugu if user speaks Telugu, otherwise reply in English. Keep answers very short, simple, and conversational. Do not use asterisks or emojis."))
            put(JSONObject().put("role", "user").put("content", userText))
        }

        val jsonBody = JSONObject().apply {
            put("model", "openai/gpt-oss-20b")
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
        }
    }

    private fun startInterruptionListening() {
        try {
            val dummyFile = File(cacheDir, "interrupt_check.m4a")
            interruptRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(dummyFile.absolutePath)
                prepare()
                start()
            }

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
        } catch (e: Exception) {
            Log.e("LiveActivity", "Interrupt listener failed: ${e.message}")
        }
    }

    private fun stopInterruptionListening() {
        interruptCheckRunnable?.let { handler.removeCallbacks(it) }
        try {
            interruptRecorder?.stop()
            interruptRecorder?.release()
            interruptRecorder = null
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun stopAudioPlayer() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignore
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
        stopInterruptionListening()
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            stopAudioPlayer()
            circleAnimator?.cancel()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }
}