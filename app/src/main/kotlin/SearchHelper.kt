package com.praveen.siriai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object SearchHelper {
    private val client = OkHttpClient()

    fun searchInternet(query: String, onResult: (String?) -> Unit) {
        val jsonBody = JSONObject().apply { put("q", query) }.toString()

        // X-API-KEY header తీసేశాం - Worker చూసుకుంటుంది
        val request = Request.Builder()
            .url(Constants.SERPER_SEARCH_URL)
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    onResult(responseBody)
                } else {
                    onResult(null)
                }
            }
        })
    }
}