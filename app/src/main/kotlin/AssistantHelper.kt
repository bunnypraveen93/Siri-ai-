package com.praveen.siriai

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast

object AssistantHelper {

    // 1. OPEN DIALER (fallback path — no direct-call permission)
    fun makePhoneCall(context: Context, numberOrQuery: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(numberOrQuery)}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Dialer app not available", Toast.LENGTH_SHORT).show()
        }
    }

    // 1b. PLACE A DIRECT CALL (no dialer screen) — needs CALL_PHONE permission granted
    fun placeDirectCall(context: Context, number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
            }
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Toast.makeText(context, "కాల్ పర్మిషన్ లేదు — డయలర్ తెరుస్తున్నాను", Toast.LENGTH_SHORT).show()
            makePhoneCall(context, number)
        } catch (e: Exception) {
            Toast.makeText(context, "కాల్ చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. SET ALARM — hands off to the SYSTEM Clock app (EXTRA_SKIP_UI = true means it's
    // added silently in the background; Clock app UI never opens, but the alarm shows up
    // there, toggled ON, using the system's own status-bar alarm icon and ringing reliably.
    fun setAlarm(context: Context, hour: Int, minute: Int, label: String = "Siri AI Alarm"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "అలారం సెట్ చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // 3. SET TIMER — same idea, via the system Clock app's timer, UI skipped
    fun setTimer(context: Context, seconds: Int, label: String = "Siri AI Timer"): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "టైమర్ సెట్ చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // 4. SET REMINDER / EVENT (opens Calendar with title pre-filled; startMillis optional, defaults to now)
    fun setReminder(context: Context, title: String, startMillis: Long = System.currentTimeMillis()) {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Calendar app not available", Toast.LENGTH_SHORT).show()
        }
    }

    // 5. SEND WHATSAPP MESSAGE — prefills text; if autoSend=true and the Accessibility
    // service is enabled, taps Send automatically once the user has already confirmed.
    fun sendWhatsAppMessage(context: Context, message: String, phoneNumber: String = "", autoSend: Boolean = false) {
        if (autoSend) {
            if (isAccessibilityServiceEnabled(context)) {
                WhatsAppAutoSendService.armAutoSend()
            } else {
                Toast.makeText(
                    context,
                    "Auto-send కోసం Settings > Accessibility లో Siri AI ఆన్ చెయ్యి. ఈసారి నువ్వే Send నొక్కు.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        try {
            val uri = if (phoneNumber.isNotEmpty()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            }
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(context, "Accessibility settings open చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
        }
    }

    // 6. SEND REGULAR SMS
    fun sendSMS(context: Context, message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("sms:")
                putExtra("sms_body", message)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Messaging app not available", Toast.LENGTH_SHORT).show()
        }
    }

    // 7. OPEN ANY INSTALLED APP BY NAME (fuzzy match on visible app label)
    fun openApp(context: Context, appName: String): Boolean {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            val match = apps.firstOrNull {
                pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
            }
            val launchIntent = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                true
            } else {
                Toast.makeText(context, "\"$appName\" app కనబడలేదు", Toast.LENGTH_SHORT).show()
                false
            }
        } catch (e: Exception) {
            Toast.makeText(context, "App open చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // 8. OPEN A WEBSITE
    fun openWebsite(context: Context, url: String) {
        try {
            val fixedUrl = if (!url.startsWith("http")) "https://$url" else url
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fixedUrl)))
        } catch (e: Exception) {
            Toast.makeText(context, "Website open చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
        }
    }

    // 9. WEB SEARCH
    fun webSearch(context: Context, query: String) {
        try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH)
            intent.putExtra(SearchManager.QUERY, query)
            context.startActivity(intent)
        } catch (e: Exception) {
            openWebsite(context, "https://www.google.com/search?q=${Uri.encode(query)}")
        }
    }

    // 10. OPEN MAPS / NAVIGATION
    fun openMaps(context: Context, destination: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e2: Exception) {
                Toast.makeText(context, "Maps open చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 11. PLAY A SONG DIRECTLY ON YOUTUBE (top search result autoplays)
    fun playYouTubeSong(context: Context, query: String) {
        YouTubeHelper.searchFirstVideoId(context, query) { videoId ->
            if (!videoId.isNullOrBlank()) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))
                } catch (e: Exception) {
                    openWebsite(context, "https://www.youtube.com/watch?v=$videoId")
                }
            } else {
                openYouTubeSearch(context, query)
            }
        }
    }

    // NOTE: vnd.youtube: is a VIDEO-ID scheme, not a search scheme — using it with plain
    // text broke things (blank/garbage watch page). For a text query we go straight to the
    // web search-results URL, which reliably opens in the YouTube app if installed.
    fun openYouTubeSearch(context: Context, query: String) {
        openWebsite(context, "https://www.youtube.com/results?search_query=${Uri.encode(query)}")
    }

    // 11b. OPEN A PLAYLIST/ALBUM AND AUTOPLAY IT (finds the first video in the playlist,
    // then opens watch?v=<video>&list=<playlist> — this starts playing immediately and
    // continues through the rest of the playlist automatically).
    fun playYouTubePlaylist(context: Context, query: String) {
        YouTubeHelper.search(context, query, "playlist") { playlistId ->
            if (!playlistId.isNullOrBlank()) {
                YouTubeHelper.getFirstPlaylistVideoId(context, playlistId) { videoId ->
                    if (!videoId.isNullOrBlank()) {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId?list=$playlistId"))
                            )
                        } catch (e: Exception) {
                            openWebsite(context, "https://www.youtube.com/watch?v=$videoId&list=$playlistId")
                        }
                    } else {
                        // Couldn't get the first video — fall back to just opening the playlist page
                        openWebsite(context, "https://www.youtube.com/playlist?list=$playlistId")
                    }
                }
            } else {
                openYouTubeSearch(context, query)
            }
        }
    }

    // 11c. OPEN A CHANNEL DIRECTLY (top matching channel's page opens)
    fun openYouTubeChannel(context: Context, query: String) {
        YouTubeHelper.search(context, query, "channel") { channelId ->
            if (!channelId.isNullOrBlank()) {
                openWebsite(context, "https://www.youtube.com/channel/$channelId")
            } else {
                openYouTubeSearch(context, query)
            }
        }
    }

    // 12. OPEN SYSTEM PANELS (wifi, bluetooth, location, general settings)
    fun openSystemPanel(context: Context, type: String) {
        val action = when (type.trim().lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        try {
            context.startActivity(Intent(action))
        } catch (e: Exception) {
            Toast.makeText(context, "Settings open చేయలేకపోయాం", Toast.LENGTH_SHORT).show()
        }
    }
}