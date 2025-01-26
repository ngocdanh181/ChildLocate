package com.example.childlocate.ui.parent.task

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.Task
import com.example.childlocate.repository.TaskRepository
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TaskViewModel (application: Application): AndroidViewModel(application)  {

    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> get() = _tasks

    private val _taskRequestStatus = MutableLiveData<Boolean?>()

    private val repository = TaskRepository(application)

    private lateinit var databaseRef : DatabaseReference // Replace with actual child ID

    fun assignTask(childId:String,taskName: String, taskTime: String) {
        databaseRef = FirebaseDatabase.getInstance().getReference("tasks/$childId")
        val taskId = databaseRef.push().key ?: return
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val formattedTime = try {
            val date = inputFormat.parse(taskTime)
            outputFormat.format(date)
        } catch (e: Exception) {
            taskTime // fallback to original format if parsing fails
        }
        val childCompleted =  Boolean ?: false
        val parentApproved =  Boolean ?: false
        val task = Task(taskId, taskName, formattedTime)
        databaseRef.child(taskId).setValue(task)
        sendTaskNotification(childId,taskName,taskTime)
        Log.d("TaskViewModel","$taskTime and $formattedTime")
    }

    private fun sendTaskNotification(childId: String, taskName: String, taskTime: String) {
        viewModelScope.launch {
            val success = repository.sendTaskRequest(childId,taskName,taskTime)
            _taskRequestStatus.postValue(success)
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
    fun approveTask(childId: String, taskId: String, isApproved: Boolean) {
        val taskRef = FirebaseDatabase.getInstance().getReference("tasks/$childId/$taskId")
        taskRef.child("parentApproved").setValue(isApproved)
            .addOnFailureListener { e ->
                Log.e("ParentViewModel", "Failed to approve task", e)
            }
            .addOnSuccessListener {
                Log.d("ParentViewModel", "Task approved successfully")
            }
    }
}
