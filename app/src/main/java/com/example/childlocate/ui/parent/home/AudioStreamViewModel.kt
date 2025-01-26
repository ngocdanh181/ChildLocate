package com.example.childlocate.ui.parent.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.childlocate.repository.AudioRepository
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// AudioStreamViewModel.kt
class AudioStreamViewModel(application: Application) : AndroidViewModel(application) {
    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val audioRepository: AudioRepository by lazy {
        AudioRepository(application) // Khởi tạo repository
    }

    private var audioPlayer: AudioPlayer? = null

    fun requestRecording(childId: String){
        viewModelScope.launch {
            _streamingState.value= StreamingState.Idle
            try{
                val success = audioRepository.sendAudioRequest(childId)
                if (!success) {
                    _streamingState.value = StreamingState.Error("Failed to request recording")
                    return@launch
                }
                startListening(childId)
            }catch (e: Exception){
                _streamingState.value = StreamingState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun startListening(childId: String) {
        viewModelScope.launch {
            try {
                _streamingState.value = StreamingState.Connecting

                audioPlayer = AudioPlayer()
                audioPlayer?.startPlaying(childId) { error ->
                    viewModelScope.launch {
                        _streamingState.value = StreamingState.Error(error)
                    }
                }
                _streamingState.value = StreamingState.Listening
            } catch (e: Exception) {
                _streamingState.value = StreamingState.Error(e.message ?: "Unknown error")
            }
        }
    }
    fun stopRecording(childId: String) {
        viewModelScope.launch {
            try {
                val success = audioRepository.sendStopAudioRequest(childId)
                if (!success) {
                    _streamingState.value = StreamingState.Error("Failed to stop recording")
                    return@launch
                }
                stopListening()
                _streamingState.value = StreamingState.Idle
            } catch (e: Exception) {
                _streamingState.value = StreamingState.Error(e.message ?: "Unknown error")
            }
        }
    }


    private fun stopListening() {
        audioPlayer?.stopPlaying()
        audioPlayer = null
        _streamingState.value = StreamingState.Idle
    }

    private suspend fun checkStreamingStatus(childId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val snapshot = FirebaseDatabase.getInstance()
                    .reference
                    .child("audio_streams")
                    .child(childId)
                    .get()
                    .await()

                snapshot.getValue(Boolean::class.java) ?: false
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

    }
}
