package com.praveen.siriai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import android.util.Log
import okio.BufferedSource

object GroqHelper {

    private val client = OkHttpClient()

    // Worker URL లు Constants.kt లో ఒక్క చోటే ఉన్నాయి
    private const val CHAT_URL = Constants.GROQ_CHAT_URL
    private const val STT_URL = Constants.GROQ_TRANSCRIBE_URL

    interface TranscriptionCallback {
        fun onSuccess(text: String)
        fun onError(message: String)
    }

    interface ChatCallback {
        // కొత్తగా యాడ్ చేసిన ఫంక్షన్: వర్డ్-బై-వర్డ్ అప్‌డేట్స్ కోసం
        fun onUpdate(chunk: String) {} 
        fun onSuccess(reply: String)
        fun onError(message: String)
    }

    fun transcribeAudio(
        audioFile: File,
        sttPrompt: String, 
        callback: TranscriptionCallback
    ) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(
                    "audio/m4a".toMediaTypeOrNull()
                )
            )
            .addFormDataPart(
                "model",
                Constants.GROQ_WHISPER_MODEL
            )
            .addFormDataPart(
                "language",
                "te"
            )
            .addFormDataPart(
                "prompt",
                "కొండారెడ్డి బురుజు, కర్నూలు, హైదరాబాద్, ఆంధ్రప్రదేశ్, తెలంగాణ, అనంతపురం. $sttPrompt"
            )
            .build()

        // Authorization header తీసేశాం - Worker చూసుకుంటుంది
        val request = Request.Builder()
            .url(STT_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                Log.e("SiriAI_STT", "Network error: ${e.message}")
                DebugLogger.log("STT", "Network error: ${e.message}")
                callback.onError(
                    "STT Network Error: ${e.message}"
                )
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e("SiriAI_STT", "API error ${response.code}: $responseBody")
                    DebugLogger.log("STT", "API error ${response.code}: $responseBody")
                    callback.onError(
                        "STT Error ${response.code}: $responseBody"
                    )
                    return
                }

                try {
                    val json = JSONObject(responseBody ?: "")
                    val text = json.optString("text", "")

                    Log.d("SiriAI_STT", "Raw Whisper response: $responseBody")
                    DebugLogger.log("STT", "Heard: \"$text\"")

                    if (text.isNotBlank() && text.length > 2) {
                        callback.onSuccess(text)
                    } else {
                        Log.e("SiriAI_STT", "Empty/short transcription: \"$text\"")
                        DebugLogger.log("STT", "Empty/short transcription: \"$text\"")
                        callback.onError("Empty transcription")
                    }

                } catch (e: Exception) {
                    Log.e("SiriAI_STT", "Parse error: ${e.message}")
                    DebugLogger.log("STT", "Parse error: ${e.message}")
                    callback.onError(
                        "STT Parse Error: ${e.message}"
                    )
                }
            }
        })
    }

    fun getChatCompletion(
        userText: String,
        systemPrompt: String? = null,
        chatHistory: List<Pair<String, Boolean>> = emptyList(),
        callback: ChatCallback
    ) {
        val messagesArray = JSONArray()

        if (systemPrompt != null) {
            messagesArray.put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt)
            )
        }

        val recentHistory = chatHistory.takeLast(5)
        for (message in recentHistory) {
            val role = if (message.second) "user" else "assistant"
            messagesArray.put(
                JSONObject()
                    .put("role", role)
                    .put("content", message.first)
            )
        }

        messagesArray.put(
            JSONObject()
                .put("role", "user")
                .put("content", userText)
        )

        val jsonBody = JSONObject().apply {
            put("model", Constants.GROQ_CHAT_MODEL)
            put("messages", messagesArray)
            put("stream", true) // స్ట్రీమింగ్ ఎనేబుల్ చేయబడింది 
        }

        // Authorization header తీసేశాం - Worker చూసుకుంటుంది
        val request = Request.Builder()
            .url(CHAT_URL)
            .addHeader(
                "Content-Type",
                "application/json"
            )
            .post(
                jsonBody.toString()
                    .toRequestBody(
                        "application/json".toMediaType()
                    )
            )
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(
                call: Call,
                e: IOException
            ) {
                DebugLogger.log("LLM", "Network error: ${e.message}")
                callback.onError(
                    "LLM Network Error: ${e.message}"
                )
            }

            override fun onResponse(
                call: Call,
                response: Response
            ) {
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    DebugLogger.log("LLM", "API error ${response.code}: $errorBody")
                    callback.onError(
                        "LLM Error ${response.code}: $errorBody"
                    )
                    return
                }

                try {
                    val source: BufferedSource? = response.body?.source()
                    var fullReply = StringBuilder()

                    // Server-Sent Events (SSE) ని లైన్-బై-లైన్ చదవడం
                    while (true) {
                        val line = source?.readUtf8Line() ?: break
                        
                        if (line.startsWith("data: ")) {
                            val data = line.removePrefix("data: ").trim()
                            
                            // స్ట్రీమ్ పూర్తయితే బ్రేక్ అవ్వాలి
                            if (data == "[DONE]") {
                                break
                            }

                            try {
                                val chunkJson = JSONObject(data)
                                val choices = chunkJson.optJSONArray("choices")
                                if (choices != null && choices.length() > 0) {
                                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                                    val content = delta?.optString("content", "") ?: ""
                                    
                                    if (content.isNotEmpty()) {
                                        fullReply.append(content)
                                        // వర్డ్-బై-వర్డ్ అప్‌డేట్ ని పంపుతుంది
                                        callback.onUpdate(content) 
                                    }
                                }
                            } catch (e: Exception) {
                                // చిన్న చంక్స్ పార్సింగ్ ఎర్రర్స్ ని ఇగ్నోర్ చేయాలి
                            }
                        }
                    }

                    val finalString = fullReply.toString().trim()
                    
                    if (finalString.isBlank()) {
                        DebugLogger.log("LLM", "Reply was blank")
                        callback.onError("LLM Error: Empty reply")
                        return
                    }

                    DebugLogger.log("LLM", "User asked: \"$userText\" -> AI replied: \"$finalString\"")
                    // మొత్తం కంప్లీట్ అయిన తర్వాత ఫైనల్ ఆన్సర్ ని పంపుతుంది
                    callback.onSuccess(finalString)

                } catch (e: Exception) {
                    DebugLogger.log("LLM", "Stream Parse error: ${e.message}")
                    callback.onError(
                        "LLM Parse Error: ${e.message}"
                    )
                }
            }
        })
    }
}