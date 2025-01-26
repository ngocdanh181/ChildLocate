package com.example.childlocate.ui.child.childstart

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.random.Random

class ChildViewModel : ViewModel() {
    private val database = FirebaseDatabase.getInstance().reference

    private val _verificationStatus = MutableLiveData<Boolean>()
    val verificationStatus: LiveData<Boolean> get() = _verificationStatus

    private val _childId = MutableLiveData<String>()
    val childId: LiveData<String> get() = _childId

    fun verifyAndSaveChild(parentUserId: String, childName: String) {
        // Kiểm tra xem parentUserId có tồn tại không
        database.child("users").child(parentUserId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Nếu parentUserId tồn tại, lấy familyId của phụ huynh đó
                        val familyId = dataSnapshot.child("familyId").value as? String
                        if (familyId != null) {
                            // Tạo childId cho trẻ nhỏ
                            val childId = generateChildId()
                            _childId.value = childId

                            // Lưu thông tin trẻ nhỏ trong nhánh "users"
                            val childData = mapOf(
                                "userId" to childId,
                                "familyId" to familyId,
                                "name" to childName,
                                "role" to "child"
                            )
                            database.child("users").child(childId).setValue(childData)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        // Lưu thông tin trẻ nhỏ vào nhánh "families"
                                        saveChildToFamily(familyId, childId, childName)
                                    } else {
                                        _verificationStatus.value = false
                                    }
                                }
                        } else {
                            _verificationStatus.value = false
                        }
                    } else {
                        _verificationStatus.value = false
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    _verificationStatus.value = false
                }
            })
    }

    private fun saveChildToFamily(familyId: String, childId: String, childName: String) {
        // Cập nhật thông tin trẻ nhỏ vào nhánh "families"
        val childFamilyData = mapOf(
            "role" to "child",
            "name" to childName
        )
        database.child("families").child(familyId).child("members").child(childId)
            .setValue(childFamilyData)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Nếu thành công, lưu deviceToken của trẻ nhỏ
                    getTokenAndSaveChildToken(childId)
                } else {
                    _verificationStatus.value = false
                }
            }
    }

    private fun generateChildId(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    private fun getTokenAndSaveChildToken(childId: String) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                database.child("users").child(childId).child("deviceToken").setValue(token)
                    .addOnCompleteListener { dbTask ->
                        _verificationStatus.value = dbTask.isSuccessful
                    }
            } else {
                _verificationStatus.value = false
            }
        }
    }
}

