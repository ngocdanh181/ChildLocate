// AudioViewModel.kt
package com.example.childlocate.ui.parent.home

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.childlocate.data.model.AudioState
import com.example.childlocate.repository.AudioRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioViewModel(application: Application) : AndroidViewModel(application) {

    private val audioRepository: AudioRepository by lazy {
        AudioRepository(application) // Khởi tạo repository
    }

    private val _audioState = MutableStateFlow<AudioState>(AudioState.Idle)
    val audioState = _audioState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null


    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private var audioJob: Job? = null

    fun requestRecording(childId: String){
        viewModelScope.launch {
            _audioState.value=AudioState.RequestingRecording
            try{
                val success = audioRepository.sendAudioRequest(childId)
                if (!success) {
                    _audioState.value = AudioState.Error("Failed to request recording")
                    return@launch
                }
                // Start listening for the recording
                audioRepository.getLatestRecording(childId)
                    .collect { result ->
                        result.onSuccess { recording ->
                            if (recording.status == "completed") {
                                _audioState.value = AudioState.ReadyToPlay(recording)
                            }
                        }.onFailure { error ->
                            _audioState.value = AudioState.Error(error.message ?: "Unknown error")
                        }
                    }
            }catch (e: Exception){
                _audioState.value = AudioState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun playRecording(url: String){
        viewModelScope.launch {
            _audioState.value = AudioState.Loading
            try{
                val result = audioRepository.downloadAndPlayRecording(url)
                result.onSuccess { file ->
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            _audioState.value = AudioState.Completed
                            release()
                            mediaPlayer = null
                            file.delete()
                        }
                    }
                    _audioState.value = AudioState.Playing
                }.onFailure { error ->
                    _audioState.value = AudioState.Error(error.message ?: "Failed to play recording")
                }
            }catch (e:Exception){
                _audioState.value = AudioState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /*
    @RequiresApi(Build.VERSION_CODES.O)
    fun startListening(childId: String) {
        viewModelScope.launch {
            try {

                _buttonState.value = AudioButtonState.Connecting
                val success = repository.sendAudioRequest(childId)

                if (!success) {
                    _buttonState.value = AudioButtonState.Error("Failed to send request")
                    return@launch
                }


                // Bắt đầu lắng nghe stream
                repository.startListeningToChild(childId)
                    .collect { result ->
                        result.onSuccess { chunk ->
                            _buttonState.value = AudioButtonState.Listening
                            audioPlayer?.playChunk(chunk)
                        }.onFailure { error ->
                            _buttonState.value = AudioButtonState.Error(error.message ?: "Unknown error")
                        }
                    }

            } catch (e: Exception) {
                _error.value = e.message
                _buttonState.value = AudioButtonState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopListening() {
        audioJob?.cancel()
        repository.stopListening()
        audioPlayer?.stop()
        _buttonState.value = AudioButtonState.Idle
    }*/
    fun stopPlaying() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _audioState.value = AudioState.Idle
    }
    override fun onCleared() {
        super.onCleared()
        stopPlaying()
    }
}
