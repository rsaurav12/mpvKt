package live.mehiz.mpvkt.ui.player

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import live.mehiz.mpvkt.database.entities.CustomButtonEntity
import live.mehiz.mpvkt.database.entities.PlaybackStateEntity
import live.mehiz.mpvkt.domain.playbackstate.repository.PlaybackStateRepository
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences

class PlayerViewModel(
    private val playbackStateRepository: PlaybackStateRepository,
    private val playerPreferences: PlayerPreferences,
    private val audioPreferences: AudioPreferences,
    private val subtitlesPreferences: SubtitlesPreferences,
    private val advancedPreferences: AdvancedPreferences,
) : ViewModel() {

    val paused = MutableStateFlow(false)
    val pos = MutableStateFlow<Int?>(null)
    val duration = MutableStateFlow<Int?>(null)
    val playerUpdate = MutableStateFlow<PlayerUpdates>(PlayerUpdates.None)
    val controlsShown = MutableStateFlow(true)
    val areControlsLocked = MutableStateFlow(false)
    val panelShown = MutableStateFlow(Panels.None)
    val sheetShown = MutableStateFlow(Sheets.None)
    val currentVolume = MutableStateFlow(50)
    val currentBrightness = MutableStateFlow(0.5f)
    val gestureSeekAmount = MutableStateFlow<Pair<Int, Int>?>(null)
    val doubleTapSeekAmount = MutableStateFlow(0)
    val isSeekingForwards = MutableStateFlow(false)
    val isVolumeSliderShown = MutableStateFlow(false)
    val isBrightnessSliderShown = MutableStateFlow(false)
    val customButtons = MutableStateFlow<List<CustomButtonEntity>>(emptyList())
    val primaryButton = MutableStateFlow<CustomButtonEntity?>(null)
    val primaryButtonTitle = MutableStateFlow<String?>(null)
    val chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val subtitleTracks = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val audioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val remainingTime = MutableStateFlow(0L)

    val maxVolume = 150

    // Zoom and pan state management
    private val _videoZoom = MutableStateFlow(1f)
    val videoZoom: StateFlow<Float> = _videoZoom.asStateFlow()

    private val _videoPan = MutableStateFlow(Offset.Zero)
    val videoPan: StateFlow<Offset> = _videoPan.asStateFlow()

    fun updateVideoTransformation(scale: Float, offset: Offset) {
        _videoZoom.update { scale }
        _videoPan.update { offset }

        MPVLib.setPropertyDouble("video-zoom", (scale - 1.0).toDouble())
        MPVLib.setPropertyDouble("video-pan-x", (offset.x / 1000.0).toDouble())
        MPVLib.setPropertyDouble("video-pan-y", (offset.y / 1000.0).toDouble())
    }

    fun resetVideoTransformation() {
        updateVideoTransformation(1f, Offset.Zero)
    }

    fun toggleZoomPan() {
        if (_videoZoom.value != 1f || _videoPan.value != Offset.Zero) {
            resetVideoTransformation()
        }
    }

    // ...Insert your other existing ViewModel methods here (pauseUnpause, seekTo, etc)...
}
