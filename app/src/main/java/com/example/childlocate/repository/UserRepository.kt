package com.example.childlocate.repository

import android.util.Log
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UserRepository {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    fun getUserData(userId: String, onSuccess: (String, String, String, String) -> Unit, onFailure: () -> Unit) {
        val userRef = database.child("users").child(userId)
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userId = snapshot.child("userId").value as? String ?: ""
                val email = snapshot.child("email").value as? String ?: ""
                val phone = snapshot.child("phone").value as? String ?: ""

                val childrenSnapshot = snapshot.child("children")
                var childName = ""
                for (child in childrenSnapshot.children) {
                    childName = child.child("childName").value as? String ?: ""
                    break // Chỉ lấy tên của đứa trẻ đầu tiên
                }
                onSuccess(userId, childName, email, phone)
            }

            override fun onCancelled(error: DatabaseError) {
                onFailure()
            }
        })
    }

    fun getAvatarUrl(userId: String, onSuccess: (String) -> Unit) {
        val userRef = database.child("users").child(userId).child("avatarUrl")
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val avatarUrl = snapshot.value as? String ?: ""
                onSuccess(avatarUrl)
            }

            override fun onCancelled(error: DatabaseError) {
                onSuccess("")
            }
        })
    }

    fun updateAvatarUrl(userId: String, avatarUrl: String) {
        val userRef = database.child("users").child(userId)
        userRef.child("avatarUrl").setValue(avatarUrl)
    }


    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String) -> Unit) {
        val user = firebaseAuth.currentUser
        if (user != null) {
            val email = user.email
            if (email != null) {
                Log.d("RepoEmail","$email")
                // Re-authenticate the user with current password
                val credential = EmailAuthProvider.getCredential(email, currentPassword)
                user.reauthenticate(credential)
                    .addOnCompleteListener { reauthTask ->
                        if (reauthTask.isSuccessful) {
                            // User re-authenticated, update password
                            user.updatePassword(newPassword)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        onResult(true, "Password changed successfully")
                                    } else {
                                        onResult(false, task.exception?.message ?: "Password change failed")
                                    }
                                }
                        } else {
                            onResult(false, reauthTask.exception?.message ?: "Re-authentication failed")
                        }
                    }
            } else {
                onResult(false, "User email is null")
            }
        } else {
            onResult(false, "User not logged in")
        }
    }

    fun logout() {
        firebaseAuth.signOut()
    }
}

