package com.praveen.siriai

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ChatMessage(
    val text: String = "",
    val isUser: Boolean = false,
    @ServerTimestamp
    val timestamp: Date? = null
)

data class Chat(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)