package com.example.childlocate.data.model

data class FcmRequest(
    val message: Message
)

data class Message(
    val token: String,
    val data: Data
)

data class Data(
    val request_type: String,
)

