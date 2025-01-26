package com.example.childlocate.ui.child.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.Task
import com.example.childlocate.repository.ChildRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch

class MainChildViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChildRepository(application)

    private val _warningRequestStatus = MutableLiveData<Boolean>()
    val warningRequestStatus: LiveData<Boolean> get() = _warningRequestStatus

    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> get() = _tasks

    private lateinit var databaseRef : DatabaseReference


    fun sendWarningToParent(projectId: String) {
        viewModelScope.launch {
            val success = repository.sendWarningToParent(projectId)
            _warningRequestStatus.postValue(success)
        }
    }

    fun stopSendWarningToParent(projectId: String) {
        viewModelScope.launch {
            val success = repository.stopWarningToParent(projectId)
            _warningRequestStatus.postValue(success)
        }
    }

    fun updateTaskStatus(childId: String, taskId: String, isCompleted: Boolean) {
        val taskRef = FirebaseDatabase.getInstance().getReference("tasks/$childId/$taskId")
        val updates = hashMapOf<String, Any>(
            "childCompleted" to isCompleted,
            "parentApproved" to false  // Reset trạng thái duyệt khi trẻ thay đổi
        )
        taskRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("MainChildViewModel", "Task status updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("MainChildViewModel", "Failed to update task status", e)
            }
    }


    fun loadTasksForChild(childId: String) {
        databaseRef = FirebaseDatabase.getInstance().getReference("tasks/$childId")
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasksList = mutableListOf<Task>()
                for (taskSnapshot in snapshot.children) {
                    val task = taskSnapshot.getValue(Task::class.java)
                    task?.let { tasksList.add(it) }
                }
                _tasks.value = tasksList
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("TaskViewModel", "Failed to load tasks", error.toException())
            }
        })
    }


}