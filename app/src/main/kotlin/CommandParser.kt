package com.praveen.siriai

import android.content.Context

/**
 * Parses an ACTION: line from the AI and dispatches it.
 *
 * @param confirmAction shows a yes/no confirmation to the user; calls onConfirmed()
 *        only if they tap "yes". Used for CALL and WHATSAPP (things that should not
 *        fire silently).
 * @param onResult delivers the chat-facing confirmation/result text once the action
 *        has actually been carried out (may be called synchronously or later, e.g.
 *        after the user confirms or after an async YouTube search finishes).
 * @param userOriginalMessage The exact message the user typed (used to double-check if they really want to open browser).
 * @return true if the reply was a recognized ACTION and was handled (or a confirmation
 *         was shown); false if it wasn't an ACTION line at all (treat as normal chat).
 */
object CommandParser {

    fun handle(
        context: Context,
        reply: String,
        userOriginalMessage: String, // NEW: Added this to check what user actually typed
        confirmAction: (String, () -> Unit) -> Unit,
        onResult: (String) -> Unit
    ): Boolean {
        val line = reply.trim()
        if (!line.startsWith("ACTION:")) return false

        val parts = line.removePrefix("ACTION:").split("|")
        val type = parts.getOrNull(0)?.trim()?.uppercase() ?: return false

        when (type) {
            "CALL" -> {
                val target = parts.getOrNull(1)?.trim().orEmpty()
                val isNumber = target.matches(Regex("^[+0-9 ]{6,}$"))
                if (isNumber) {
                    confirmAction("$target కి కాల్ చేయమంటారా?") {
                        AssistantHelper.placeDirectCall(context, target)
                        onResult("📞 $target కి కాల్ చేస్తున్నాను...")
                    }
                } else {
                    val contact = ContactsHelper.findContact(context, target)
                    if (contact != null) {
                        confirmAction("${contact.name} కి కాల్ చేయమంటారా?") {
                            AssistantHelper.placeDirectCall(context, contact.number)
                            onResult("📞 ${contact.name} కి కాల్ చేస్తున్నాను...")
                        }
                    } else {
                        onResult("⚠️ \"$target\" అనే కాంటాక్ట్ దొరకలేదు")
                    }
                }
            }
            "ALARM" -> {
                val hour = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
                val minute = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                val label = parts.getOrNull(3)?.trim().orEmpty().ifEmpty { "Siri AI Alarm" }
                val ok = AssistantHelper.setAlarm(context, hour, minute, label)
                onResult(
                    if (ok) "⏰ %02d:%02d కి అలారం సెట్ చేసాను".format(hour, minute)
                    else "⚠️ అలారం సెట్ చేయలేకపోయాను"
                )
            }
            "TIMER" -> {
                val seconds = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 60
                val label = parts.getOrNull(2)?.trim().orEmpty().ifEmpty { "Timer" }
                val ok = AssistantHelper.setTimer(context, seconds, label)
                onResult(
                    if (ok) "⏱️ టైమర్ సెట్ చేసాను"
                    else "⚠️ టైమర్ సెట్ చేయలేకపోయాను"
                )
            }
            "WHATSAPP" -> {
                val message = parts.getOrNull(1)?.trim().orEmpty()
                val phone = parts.getOrNull(2)?.trim().orEmpty()
                confirmAction("WhatsApp కి \"$message\" పంపమంటారా?") {
                    AssistantHelper.sendWhatsAppMessage(context, message, phone, autoSend = true)
                    onResult("💬 WhatsApp మెసేజ్ పంపుతున్నాను...")
                }
            }
            "SMS" -> {
                val message = parts.getOrNull(1)?.trim().orEmpty()
                AssistantHelper.sendSMS(context, message)
                onResult("✉️ మెసేజ్ యాప్ తెరుస్తున్నాను...")
            }
            "OPEN_APP" -> {
                val appName = parts.getOrNull(1)?.trim().orEmpty()
                val ok = AssistantHelper.openApp(context, appName)
                onResult(if (ok) "📱 $appName తెరుస్తున్నాను..." else "⚠️ \"$appName\" కనబడలేదు")
            }
            "SEARCH", "OPEN_URL" -> {
                val query = parts.getOrNull(1)?.trim().orEmpty()
                
                // --- STRICT BROWSER FILTER LOGIC ---
                val userTextLower = userOriginalMessage.lowercase()
                val userActuallyWantsBrowser = userTextLower.contains("open google") || 
                                               userTextLower.contains("browser") || 
                                               userTextLower.contains("search") || 
                                               userTextLower.contains("vetuku") || 
                                               userTextLower.contains("వెతుకు") || 
                                               userTextLower.contains("సెర్చ్") || 
                                               userTextLower.contains("ఓపెన్")
                                               
                // If they just asked a normal question containing "google", reject the action and force AI to answer in text
                if (!userActuallyWantsBrowser) {
                    return false // By returning false here, the app treats this as a normal chat message instead of opening browser!
                }
                
                if (type == "SEARCH") {
                    AssistantHelper.webSearch(context, query)
                    onResult("🔍 \"$query\" కోసం సెర్చ్ చేస్తున్నాను...")
                } else {
                    AssistantHelper.openWebsite(context, query)
                    onResult("🌐 తెరుస్తున్నాను...")
                }
            }
            "MAPS" -> {
                val dest = parts.getOrNull(1)?.trim().orEmpty()
                AssistantHelper.openMaps(context, dest)
                onResult("🗺️ $dest కి దారి చూపిస్తున్నాను...")
            }
            "YOUTUBE" -> {
                val query = parts.getOrNull(1)?.trim().orEmpty()
                onResult("▶️ \"$query\" ప్లే చేస్తున్నాను...")
                AssistantHelper.playYouTubeSong(context, query)
            }
            "YOUTUBE_PLAYLIST" -> {
                val query = parts.getOrNull(1)?.trim().orEmpty()
                onResult("▶️ \"$query\" ప్లేలిస్ట్ తెరుస్తున్నాను...")
                AssistantHelper.playYouTubePlaylist(context, query)
            }
            "YOUTUBE_CHANNEL" -> {
                val query = parts.getOrNull(1)?.trim().orEmpty()
                onResult("📺 \"$query\" ఛానెల్ తెరుస్తున్నాను...")
                AssistantHelper.openYouTubeChannel(context, query)
            }
            "REMINDER" -> {
                val title = parts.getOrNull(1)?.trim().orEmpty()
                AssistantHelper.setReminder(context, title)
                onResult("🗓️ \"$title\" రిమైండర్ కోసం Calendar తెరుస్తున్నాను...")
            }
            "PANEL" -> {
                val panelType = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "settings" }
                AssistantHelper.openSystemPanel(context, panelType)
                onResult("⚙️ తెరుస్తున్నాను...")
            }
            else -> return false
        }
        return true
    }
}
