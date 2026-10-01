package com.praveen.siriai

import android.util.Base64
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

object VoiceHelper {

    private val client = OkHttpClient()

    interface TtsCallback {
        fun onSuccess(audioFile: File)
        fun onError(message: String)
    }

    fun textToSpeech(text: String, cacheDir: File, callback: TtsCallback) {
        val jsonBody = JSONObject().apply {
            put("model", Constants.SARVAM_TTS_MODEL)
            put("text", text)
            put("language_code", Constants.SARVAM_LANGUAGE)
            put("speaker", Constants.SARVAM_SPEAKER)
            
            put("pace", 1.0) 
            put("temperature", 0.8) 
            put("speech_sample_rate", 22050) 
        }

        // api-subscription-key header తీసేశాం - Worker చూసుకుంటుంది
        val request = Request.Builder()
            .url(Constants.SARVAM_TTS_URL)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback.onError("TTS Network Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string()

                if (!response.isSuccessful || bodyStr == null) {
                    callback.onError("Sarvam Error ${response.code}: $bodyStr")
                    return
                }

                try {
                    val respJson = JSONObject(bodyStr)
                    val audioBase64 = respJson.getJSONArray("audios").getString(0)
                    val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)

                    // క్యూ సిస్టమ్ కోసం ప్రతి వాక్యానికి వేర్వేరు ఫైల్ నేమ్స్ క్రియేట్ చేయడం
                    val outputFile = File(cacheDir, "ai_response_${System.nanoTime()}.wav")
                    outputFile.writeBytes(audioBytes)

                    callback.onSuccess(outputFile)
                } catch (e: Exception) {
                    callback.onError("TTS Save Error: ${e.message}")
                }
            }
        })
    }
}