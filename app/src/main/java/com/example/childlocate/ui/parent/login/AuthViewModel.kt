package com.example.childlocate.ui.parent.login

// AuthViewModel.kt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random



class AuthViewModel : ViewModel() {
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("User ID not found")
                val userId = getUserIdFromUid(uid) ?: throw Exception("User ID not found in the database")
                val user = getUserData(userId)
                updateDeviceToken(user.userId)
                _authState.value = AuthState.LoggedIn(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Login failed")
            }
        }
    }

    private suspend fun getUserIdFromUid(uid: String): String? {
        // Lấy userId từ nhánh "userIds" theo uid
        return database.child("userIds").child(uid).get().await().getValue(String::class.java)
    }

    fun registerUser(email: String, password: String) {
        viewModelScope.launch {
            try {
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val uid = result.user?.uid ?: throw Exception("User ID not found")
                val userId = generateUserId()

                // Lấy userId làm familyId cho phụ huynh đầu tiên
                val familyId = userId
                val user = User(email = email, userId = userId, role = "parent_primary", familyId = familyId)

                saveUserData(uid,userId, user)
                initializeFamilyData(familyId, userId)
                updateDeviceToken(userId)

                _authState.value = AuthState.LoggedIn(user)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Registration failed")
            }
        }
    }



    private suspend fun updateDeviceToken(userId: String) {
        val token = FirebaseMessaging.getInstance().token.await()
        database.child("users").child(userId).child("deviceToken").setValue(token).await()
    }

    private suspend fun getUserData(userId: String): User {
        return database.child("users").child(userId).get().await().getValue(User::class.java)
            ?: throw Exception("User data not found")
    }

    private suspend fun saveUserData(uid:String,userId: String, user: User) {
        database.child("users").child(userId).setValue(user).await()
        database.child("userIds").child(uid).setValue(userId).await()
    }


    private suspend fun initializeFamilyData(familyId: String, userId: String) {
        val familyData = mapOf(
            "primaryParentId" to userId,
            "familyName" to "name's Family",
            "members" to mapOf(
                userId to mapOf(
                    "role" to "parent_primary",
                )
            )
        )
        database.child("families").child(familyId).setValue(familyData).await()
    }

    private fun generateUserId(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    fun logoutUser() {
        firebaseAuth.signOut()
        _authState.value = AuthState.LoggedOut
    }
}




sealed class AuthState {
    data object LoggedOut : AuthState()
    data class LoggedIn(val user: User) : AuthState()
    data class Error(val message: String) : AuthState()
}

data class User(
    val email: String = "",
    val userId: String = "",
    val deviceToken: String? = null,
    val role:String="",
    val familyId:String = ""
)