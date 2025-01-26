package com.example.childlocate.data.model

data class ChatMessage(
    val id: String,
    val senderId: String = "",
    val receiverId: String = "",
    val messageText: String = "",
    val timestamp: Long = System.currentTimeMillis()
){
    // No-argument constructor needed for Firebase
    constructor() : this("", "", "","")
}