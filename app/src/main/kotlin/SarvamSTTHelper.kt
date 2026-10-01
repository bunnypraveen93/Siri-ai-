package com.praveen.siriai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import android.util.Log

object SarvamSTTHelper {

    private val client = OkHttpClient()

    interface TranscriptionCallback {
        fun onSuccess(text: String)
        fun onError(message: String)
    }

    fun transcribeAudio(audioFile: File, callback: TranscriptionCallback) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            // ERROR FIX: 'audio/m4a' ని 'audio/mp4' గా మార్చాం
            .addFormDataPart("file", audioFile.name, audioFile.asRequestBody("audio/mp4".toMediaTypeOrNull()))
            .addFormDataPart("language_code", "te-IN") 
            .build()

        // api-subscription-key header తీసేశాం - Worker చూసుకుంటుంది
        val request = Request.Builder()
            .url(Constants.SARVAM_STT_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("SiriAI_STT", "Sarvam STT Network error: ${e.message}")
                callback.onError("Network Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e("SiriAI_STT", "Sarvam API error ${response.code}: $responseBody")
                    callback.onError("API Error ${response.code}: $responseBody")
                    return
                }

                try {
                    val json = JSONObject(responseBody ?: "")
                    val text = json.optString("transcript", "").trim()

                    Log.d("SiriAI_STT", "Sarvam Heard: \"$text\"")

                    if (text.isNotBlank() && text.length > 2) {
                        callback.onSuccess(text)
                    } else {
                        callback.onError("Empty transcription")
                    }
                } catch (e: Exception) {
                    Log.e("SiriAI_STT", "Parse error: ${e.message}")
                    callback.onError("Parse Error: ${e.message}")
                }
            }
        })
    }
}