package com.example.childlocate.ui.child.childchat

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.childlocate.data.model.ChatMessage
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlin.random.Random

class ChildChatDetailViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().reference

    private val _messages = MutableLiveData<List<ChatMessage>>()
    val messages: LiveData<List<ChatMessage>> get() = _messages

    private lateinit var senderId: String
    private lateinit var receiverId: String

    private val _memberNames = MutableLiveData<Map<String,String>>()
    val memberNames: LiveData<Map<String, String>> get() = _memberNames

    fun setChatParticipants(senderId: String, receiverId: String) {
        this.senderId = senderId
        this.receiverId = receiverId
        loadFamilyMembers(receiverId)

    }

    fun loadMessages() {
        val chatRef = database.child("messages").child(receiverId)
        chatRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    // If no messages exist, push dummy data
                    pushDummyData()
                } else {
                    // Load existing messages
                    loadExistingMessages(snapshot)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }
    fun uploadAvatar(uri: Uri) {
        val randomNumber : String = generateNumberId()

        val storageRef = FirebaseStorage.getInstance().reference.child("messages/${randomNumber}.jpg")
        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                val messageImageUrl = downloadUrl.toString()
                sendMessage(messageImageUrl)
            }
        }.addOnFailureListener {
            // Handle failure
        }
    }
    private fun generateNumberId(): String {
        val id = Random.nextInt(0, 999999999).toString()
        return id
    }

    private fun pushDummyData() {
        val dummyMessage = ChatMessage(
            id = database.push().key ?: "",
            senderId = senderId,
            messageText = "Welcome to the chat!"
        )
        //database.child("messages").child(senderId).child(receiverId).push().setValue(dummyMessage)
        database.child("messages").child(receiverId).push().setValue(dummyMessage)
    }

    private fun loadExistingMessages(snapshot: DataSnapshot) {
        val messageList = mutableListOf<ChatMessage>()
        snapshot.children.forEach { dataSnapshot ->
            val message = dataSnapshot.getValue(ChatMessage::class.java)
            if (message != null) {
                messageList.add(message)
            }
        }
        _messages.value = messageList
    }

    fun sendMessage(messageText: String) {
        val message = ChatMessage(
            id = database.push().key ?: "",
            senderId = senderId,
            messageText = messageText,
            timestamp = System.currentTimeMillis()
        )

        database.child("messages").child(receiverId).push().setValue(message)
    }

    fun loadFamilyMembers(familyId: String) {
        val membersRef = FirebaseDatabase.getInstance().getReference("families/$familyId/members")
        membersRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val memberNames = mutableMapOf<String, String>()
                snapshot.children.forEach { member ->
                    val memberId = member.key ?: return@forEach
                    val role= member.child("role").getValue(String::class.java)
                    if (role== "child"){
                        val name = member.child("name").getValue(String::class.java) ?: "Unknown"
                        memberNames[memberId] = name
                    }else{
                        val name = "Dad"
                        memberNames[memberId] = name
                    }


                }
                _memberNames.value = memberNames // Lưu vào LiveData hoặc trực tiếp truyền vào Adapter
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }
}