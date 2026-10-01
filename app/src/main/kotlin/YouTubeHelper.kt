package com.praveen.siriai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Searches YouTube (videos / playlists / channels) via the YouTube Data API v3,
 * so a spoken name can be played/opened directly instead of just showing search results.
 */
object YouTubeHelper {

    // Cloudflare Worker - API key Worker లో Secret గా ఉంది, app లో అవసరం లేదు
    private const val YT_BASE = "${Constants.WORKER_BASE}/youtube"

    private val handler = Handler(Looper.getMainLooper())

    /** type = "video" | "playlist" | "channel" */
    fun search(context: Context, query: String, type: String, callback: (String?) -> Unit) {
        Thread {
            var result: String? = null
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val urlStr = "$YT_BASE/v3/search?part=snippet&type=$type&maxResults=1&q=$encoded"
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val code = connection.responseCode
                if (code == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val idObj = items.getJSONObject(0).optJSONObject("id")
                        result = when (type) {
                            "video" -> idObj?.optString("videoId")
                            "playlist" -> idObj?.optString("playlistId")
                            "channel" -> idObj?.optString("channelId")
                            else -> null
                        }
                        if (result.isNullOrBlank()) {
                            handler.post {
                                Toast.makeText(context, "YouTube: రిజల్ట్ దొరకలేదు", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        handler.post {
                            Toast.makeText(context, "YouTube: ఏ రిజల్ట్ దొరకలేదు", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val errorJson = try { JSONObject(errorBody) } catch (e: Exception) { null }
                    val reason = errorJson?.optJSONObject("error")?.optString("message") ?: "HTTP $code"
                    handler.post {
                        Toast.makeText(context, "YouTube API error: $reason", Toast.LENGTH_LONG).show()
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                handler.post {
                    Toast.makeText(context, "YouTube సెర్చ్ ఫెయిల్ అయ్యింది: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            handler.post { callback(result) }
        }.start()
    }

    // Kept for compatibility with any earlier callers.
    fun searchFirstVideoId(context: Context, query: String, callback: (String?) -> Unit) {
        search(context, query, "video", callback)
    }

    /** Gets the first video's ID inside a playlist, so it can be used to autoplay
     * (watch?v=<video>&list=<playlist> plays immediately and continues through the list). */
    fun getFirstPlaylistVideoId(context: Context, playlistId: String, callback: (String?) -> Unit) {
        Thread {
            var result: String? = null
            try {
                val urlStr = "$YT_BASE/v3/playlistItems?part=snippet&maxResults=1&playlistId=$playlistId"
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val items = json.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val snippet = items.getJSONObject(0).optJSONObject("snippet")
                        val resourceId = snippet?.optJSONObject("resourceId")
                        result = resourceId?.optString("videoId")
                    }
                }
                connection.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            handler.post { callback(result) }
        }.start()
    }
}