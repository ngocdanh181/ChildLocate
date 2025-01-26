package com.example.childlocate.ui.parent.detailchat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.ChatMessage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class DetailChatViewModel : ViewModel() {
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val storage = FirebaseStorage.getInstance().reference

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _memberNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val memberNames: StateFlow<Map<String, String>> = _memberNames.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var senderId: String = ""
    private var familyId: String = ""

    fun setChatParticipants(senderId: String, familyId: String) {
        this.senderId = senderId
        this.familyId = familyId
        loadFamilyMembers()
        loadMessages()
    }

    fun sendMessage(messageText: String) {
        viewModelScope.launch {
            try {
                val message = ChatMessage(
                    id = database.push().key ?: "",
                    senderId = senderId,
                    messageText = messageText,
                    timestamp = System.currentTimeMillis()
                )
                database.child("messages")
                    .child(familyId)
                    .push()
                    .setValue(message)
                    .await()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun uploadMessage(uri: Uri) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val imageId = generateNumberId()
                val storageRef = storage.child("messages/$imageId.jpg")

                storageRef.putFile(uri).await()
                val downloadUrl = storageRef.downloadUrl.await()

                sendMessage(downloadUrl.toString())
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            try {
                val chatRef = database.child("messages").child(familyId)
                val snapshot = chatRef.get().await()

                if (!snapshot.exists()) {
                    pushDummyData()
                } else {
                    loadExistingMessages(snapshot)
                }
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun loadFamilyMembers() {
        viewModelScope.launch {
            try {
                val membersRef = database.child("families/$familyId/members")
                val snapshot = membersRef.get().await()

                val memberNames = mutableMapOf<String, String>()
                snapshot.children.forEach { member ->
                    val memberId = member.key ?: return@forEach
                    val role = member.child("role").getValue(String::class.java)

                    memberNames[memberId] = when (role) {
                        "child" -> member.child("name").getValue(String::class.java) ?: "Unknown"
                        else -> "Dad"
                    }
                }
                _memberNames.value = memberNames
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private suspend fun pushDummyData() {
        try {
            val dummyMessage = ChatMessage(
                id = database.push().key ?: "",
                senderId = senderId,
                receiverId = familyId,
                messageText = "Welcome to the chat!"
            )
            database.child("messages")
                .child(familyId)
                .push()
                .setValue(dummyMessage)
                .await()
        } catch (e: Exception) {
            // Handle error
        }
    }

    private fun loadExistingMessages(snapshot: DataSnapshot) {
        val messageList = snapshot.children.mapNotNull { dataSnapshot ->
            dataSnapshot.getValue(ChatMessage::class.java)
        }
        _messages.value = messageList
    }

    private fun generateNumberId(): String = Random.nextInt(0, 999999999).toString()

    fun getPhoneNumber(): String? {
        // Implement phone number retrieval logic
        return null
    }

    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }
}