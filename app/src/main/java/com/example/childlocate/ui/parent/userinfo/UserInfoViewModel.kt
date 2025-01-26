package com.example.childlocate.ui.parent.userinfo


import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.childlocate.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage


class UserInfoViewModel(application: Application) : AndroidViewModel(application) {
    private val userRepository = UserRepository()
    private val _userId = MutableLiveData<String>()
    val userId: LiveData<String> get() = _userId

    private val _childName = MutableLiveData<String>()
    val childName: LiveData<String> get() = _childName

    private val _email = MutableLiveData<String>()
    val email: LiveData<String> get() = _email

    private val _phone = MutableLiveData<String>()
    val phone: LiveData<String> get() = _phone

    private val _logoutStatus = MutableLiveData<Boolean>()
    val logoutStatus: LiveData<Boolean> get() = _logoutStatus

    private val _avatarUrl = MutableLiveData<String>()
    val avatarUrl: LiveData<String> get() = _avatarUrl

    private val _appVersion = MutableLiveData<String>()
    val appVersion: LiveData<String> get() = _appVersion

    private val _passwordChangeResult = MutableLiveData<String>()
    val passwordChangeResult: LiveData<String> get() = _passwordChangeResult

    init {
        fetchAvatar()
        //fetchAppVersion()
    }

    fun fetchUserData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            userRepository.getUserData(user.uid,
                { userId, childName, email, phone ->
                    _userId.value = userId
                    _childName.value = childName
                    _email.value = email
                    _phone.value = phone
                },
                {
                    // Handle error
                }
            )
        }
    }

    fun uploadAvatar(uri: Uri) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("avatars/${user.uid}.jpg")
        storageRef.putFile(uri).addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                userRepository.updateAvatarUrl(user.uid, downloadUrl.toString())
                _avatarUrl.value = downloadUrl.toString()
            }
        }.addOnFailureListener {
            // Handle failure
        }
    }

    private fun fetchAvatar() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        userRepository.getAvatarUrl(user.uid) { url ->
            _avatarUrl.value = url
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        userRepository.changePassword(currentPassword, newPassword) { success, message ->
            _passwordChangeResult.value = if (success) {
                "Password changed successfully"
            } else {
                "Failed to change password: $message"
            }
        }
    }

    private fun fetchAppVersion() {
        _appVersion.value = "1.0.0" // Replace with actual app version logic
    }


    fun onLogoutClick() {
        userRepository.logout()
        _logoutStatus.value = true
    }
}

