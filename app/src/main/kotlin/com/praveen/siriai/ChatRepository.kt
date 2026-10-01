package com.praveen.siriai

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions

/**
 * Single place for all Firestore chat-history reads/writes.
 * MainActivity calls this instead of touching Firestore directly —
 * keeps the Activity focused on UI, and makes this logic easy to unit-test
 * (or swap for a fake) since it depends only on FirebaseAuth/FirebaseFirestore.
 */
class ChatRepository(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    private fun currentUserId(): String? = firebaseAuth.currentUser?.uid

    private fun chatsCollection(userId: String) =
        firestore.collection("users").document(userId).collection("chats")

    // ====================================================
    // NEW: User Profile & Preferences Logic (AI Context)
    // ====================================================
    
    /** యూజర్ పేరు మరియు వారి ప్రిఫరెన్సెస్ (ఇష్టాలు) ఫైర్‌బేస్ లో సేవ్ చేయడానికి */
    fun saveUserProfile(name: String, preferences: Map<String, Any> = emptyMap(), onDone: () -> Unit = {}) {
        val userId = currentUserId() ?: return
        val userData = hashMapOf<String, Any>(
            "name" to name,
            "updatedAt" to Timestamp.now()
        )
        userData.putAll(preferences)

        firestore.collection("users").document(userId)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener { onDone() }
    }

    /** ఏఐ ప్రాంప్ట్ కోసం యూజర్ ప్రొఫైల్ డేటాను తిరిగి తీసుకోవడానికి */
    fun getUserProfile(onResult: (Map<String, Any>?) -> Unit) {
        val userId = currentUserId() ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onResult(doc.data)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }
    // ====================================================

    /** Saves one message under [chatId] and updates the chat's title/timestamp. */
    fun saveMessage(chatId: String, text: String, isUser: Boolean, onSaved: () -> Unit = {}) {
        val userId = currentUserId() ?: return
        val message = hashMapOf(
            "text" to text,
            "isUser" to isUser,
            "timestamp" to Timestamp.now()
        )

        chatsCollection(userId).document(chatId)
            .collection("messages")
            .add(message)
            .addOnSuccessListener {
                if (isUser) {
                    setTitleIfMissing(userId, chatId, text, onSaved)
                } else {
                    chatsCollection(userId).document(chatId)
                        .update("updatedAt", Timestamp.now())
                        .addOnSuccessListener { onSaved() }
                }
            }
    }

    private fun setTitleIfMissing(userId: String, chatId: String, firstUserText: String, onDone: () -> Unit) {
        val chatDoc = chatsCollection(userId).document(chatId)
        chatDoc.get().addOnSuccessListener { doc ->
            if (doc.exists() && !doc.getString("title").isNullOrEmpty()) {
                onDone()
                return@addOnSuccessListener
            }
            val title = firstUserText.take(40) + (if (firstUserText.length > 40) "..." else "")
            val chatData = mapOf(
                "title" to title,
                "updatedAt" to Timestamp.now(),
                "isPinned" to false
            )
            chatDoc.set(chatData, SetOptions.merge()).addOnSuccessListener { onDone() }
        }
    }

    /** Loads up to 50 chats, pinned chats first, most recently updated first. */
    fun loadChatList(onResult: (List<Pair<String, Map<String, Any?>>>) -> Unit) {
        val userId = currentUserId() ?: return
        chatsCollection(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(50)
            .get()
            .addOnSuccessListener { result ->
                val chats = result.map { doc -> Pair(doc.id, doc.data) }
                onResult(chats.sortedWith(chatOrdering))
            }
    }

    /** Pinned chats first; within each group, most recently updated first. */
    private val chatOrdering = Comparator<Pair<String, Map<String, Any?>>> { a, b ->
        val aPinned = (a.second["isPinned"] as? Boolean) ?: false
        val bPinned = (b.second["isPinned"] as? Boolean) ?: false
        if (aPinned != bPinned) {
            return@Comparator bPinned.compareTo(aPinned)
        }
        val aTime = (a.second["updatedAt"] as? Timestamp)?.toDate()?.time ?: 0L
        val bTime = (b.second["updatedAt"] as? Timestamp)?.toDate()?.time ?: 0L
        bTime.compareTo(aTime)
    }

    fun loadChatMessages(chatId: String, onResult: (List<Pair<String, Boolean>>) -> Unit) {
        val userId = currentUserId() ?: return
        chatsCollection(userId).document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                val messages = result.map { doc ->
                    Pair(doc.getString("text") ?: "", doc.getBoolean("isUser") ?: false)
                }
                onResult(messages)
            }
    }

    companion object {
        private const val MAX_PINNED_CHATS = 5
    }

    fun pinChat(chatId: String, currentlyPinned: Boolean, onDone: () -> Unit, onLimitReached: () -> Unit) {
        val userId = currentUserId() ?: return
        val chats = chatsCollection(userId)

        if (currentlyPinned) {
            chats.document(chatId).update("isPinned", false).addOnSuccessListener { onDone() }
            return
        }

        chats.whereEqualTo("isPinned", true).get().addOnSuccessListener { result ->
            if (result.size() >= MAX_PINNED_CHATS) {
                onLimitReached()
                return@addOnSuccessListener
            }
            chats.document(chatId).update("isPinned", true).addOnSuccessListener { onDone() }
        }
    }

    fun renameChat(chatId: String, newTitle: String, onDone: () -> Unit) {
        val userId = currentUserId() ?: return
        chatsCollection(userId).document(chatId)
            .update("title", newTitle)
            .addOnSuccessListener { onDone() }
    }

    fun deleteChat(chatId: String, onDone: () -> Unit) {
        val userId = currentUserId() ?: return
        chatsCollection(userId).document(chatId)
            .delete()
            .addOnSuccessListener { onDone() }
    }
}
