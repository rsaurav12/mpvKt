package live.mehiz.mpvkt.ui.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import live.mehiz.mpvkt.R
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.ui.player.MPVView
import live.mehiz.mpvkt.ui.player.Panels
import live.mehiz.mpvkt.ui.player.PlayerActivity
import live.mehiz.mpvkt.ui.player.PlayerUpdates
import live.mehiz.mpvkt.ui.player.PlayerViewModel
import live.mehiz.mpvkt.ui.player.controls.components.DoubleTapSeekTriangles
import live.mehiz.mpvkt.ui.theme.playerRippleConfiguration
import org.koin.compose.koinInject

@Suppress("CyclomaticComplexMethod", "MultipleEmitters")
@Composable
fun GestureHandler(
    viewModel: PlayerViewModel,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val playerPreferences = koinInject<PlayerPreferences>()
    val audioPreferences = koinInject<AudioPreferences>()
    val panelShown by viewModel.panelShown.collectAsState()
    val allowGesturesInPanels by playerPreferences.allowGesturesInPanels.collectAsState()
    val paused by MPVLib.propBoolean["pause"].collectAsState()
    val duration by MPVLib.propInt["duration"].collectAsState()
    val position by MPVLib.propInt["time-pos"].collectAsState()
    val playbackSpeed by MPVLib.propFloat["speed"].collectAsState()
    val controlsShown by viewModel.controlsShown.collectAsState()
    val areControlsLocked by viewModel.areControlsLocked.collectAsState()
    val seekAmount by viewModel.doubleTapSeekAmount.collectAsState()
    val isSeekingForwards by viewModel.isSeekingForwards.collectAsState()
    var isDoubleTapSeeking by remember { mutableStateOf(false) }

    LaunchedEffect(seekAmount) {
        delay(800)
        isDoubleTapSeeking = false
        viewModel.updateSeekAmount(0)
        viewModel.updateSeekText(null)
        delay(100)
        viewModel.hideSeekBar()
    }

    val multipleSpeedGesture by playerPreferences.holdForMultipleSpeed.collectAsState()
    val brightnessGesture = playerPreferences.brightnessGesture.get()
    val volumeGesture by playerPreferences.volumeGesture.collectAsState()
    val swapVolumeAndBrightness by playerPreferences.swapVolumeAndBrightness.collectAsState()
    val seekGesture by playerPreferences.horizontalSeekGesture.collectAsState()
    val preciseSeeking by playerPreferences.preciseSeeking.collectAsState()
    val showSeekbarWhenSeeking by playerPreferences.showSeekBarWhenSeeking.collectAsState()
    var isLongPressing by remember { mutableStateOf(false) }
    val currentVolume by viewModel.currentVolume.collectAsState()
    val currentMPVVolume by MPVLib.propInt["volume"].collectAsState()
    val currentBrightness by viewModel.currentBrightness.collectAsState()
    val volumeBoostingCap = audioPreferences.volumeBoostCap.get()
    val haptics = LocalHapticFeedback.current

    val context = LocalContext.current
    val activity = context as PlayerActivity
    val mpvView = activity.player

    var isZoomPanGesture by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeGestures)
            // Detect pinch to zoom and pan gestures
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (areControlsLocked) return@detectTransformGestures

                    if (zoom != 1f || (pan.x != 0f || pan.y != 0f)) {
                        isZoomPanGesture = true

                        if (zoom != 1f) {
                            mpvView.onZoom(zoom, centroid.x, centroid.y)
                        }

                        if (mpvView.getZoomLevel() > 1f && (pan.x != 0f || pan.y != 0f)) {
                            mpvView.onPan(pan.x, pan.y)
                        }
                    }
                }
            }
            // Original tap gestures - check zoom/pan gesture priority
            .pointerInput(isZoomPanGesture) {
                var originalSpeed = MPVLib.getPropertyFloat("speed") ?: 1f
                detectTapGestures(
                    onTap = {
                        if (isZoomPanGesture) {
                            isZoomPanGesture = false
                            return@detectTapGestures
                        }

                        viewModel.pauseUnpause()
                    },
                    // Double tap resets zoom if currently zoomed
                    onDoubleTap = {
                        if (areControlsLocked || isDoubleTapSeeking || isZoomPanGesture) return@detectTapGestures

                        if (mpvView.getZoomLevel() > 1f) {
                            mpvView.resetZoomPan()
                            return@detectTapGestures
                        }

                        if (it.x > size.width * 3 / 5) {
                            if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                            viewModel.handleRightDoubleTap()
                            isDoubleTapSeeking = true
                        } else if (it.x < size.width * 2 / 5) {
                            if (isSeekingForwards) viewModel.updateSeekAmount(0)
                            viewModel.handleLeftDoubleTap()
                            isDoubleTapSeeking = true
                        } else {
                            viewModel.handleCenterDoubleTap()
                        }
                    },
                    onPress = {
                        if (panelShown != Panels.None && !allowGesturesInPanels) {
                            viewModel.panelShown.update { Panels.None }
                        }

                        if (!areControlsLocked && isDoubleTapSeeking && seekAmount != 0) {
                            if (it.x > size.width * 3 / 5) {
                                if (!isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleRightDoubleTap()
                            } else if (it.x < size.width * 2 / 5) {
                                if (isSeekingForwards) viewModel.updateSeekAmount(0)
                                viewModel.handleLeftDoubleTap()
                            } else {
                                viewModel.handleCenterDoubleTap()
                            }
                        } else {
                            isDoubleTapSeeking = false
                        }

                        val press = PressInteraction.Press(
                            it.copy(x = if (it.x > size.width * 3 / 5) it.x - size.width * 0.6f else it.x),
                        )
                        interactionSource.emit(press)
                        tryAwaitRelease()
                        if (isLongPressing) {
                            isLongPressing = false
                            MPVLib.setPropertyFloat("speed", originalSpeed)
                            viewModel.playerUpdate.update { PlayerUpdates.None }
                        }
                        interactionSource.emit(PressInteraction.Release(press))
                    },
                    onLongPress = {
                        if (multipleSpeedGesture == 0f || areControlsLocked) return@detectTapGestures
                        if (!isLongPressing && paused == false) {
                            originalSpeed = playbackSpeed ?: return@detectTapGestures
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            isLongPressing = true
                            MPVLib.setPropertyFloat("speed", multipleSpeedGesture)
                            viewModel.playerUpdate.update { PlayerUpdates.MultipleSpeed }
                        }
                    },
                )
            }
            // Horizontal seek gestures disabled if zoomed or zoom/pan active
            .pointerInput(areControlsLocked) {
                if (!seekGesture || areControlsLocked) return@pointerInput
                if (isZoomPanGesture || mpvView.getZoomLevel() > 1f) return@pointerInput
                var startingPosition = position ?: 0
                var startingX = 0f
                var wasPlayerAlreadyPause = false
                detectHorizontalDragGestures(
                    onDragStart = {
                        startingPosition = position ?: 0
                        startingX = it.x
                        wasPlayerAlreadyPause = paused ?: false
                        viewModel.pause()
                    },
                    onDragEnd = {
                        viewModel.gestureSeekAmount.update { null }
                        viewModel.hideSeekBar()
                        if (!wasPlayerAlreadyPause) viewModel.unpause()
                    },
                ) { change, dragAmount ->
                    if ((position ?: 0) <= 0f && dragAmount < 0) return@detectHorizontalDragGestures
                    if ((position ?: 0) >= (duration ?: 0) && dragAmount > 0) return@detectHorizontalDragGestures
                    calculateNewHorizontalGestureValue(
                        startingPosition,
                        startingX,
                        change.position.x,
                        0.15f
                    ).let {
                        viewModel.gestureSeekAmount.update { _ ->
                            Pair(
                                startingPosition,
                                (it - startingPosition)
                                    .coerceIn(0 - startingPosition, ((duration ?: 0) - startingPosition)),
                            )
                        }
                        viewModel.seekTo(it, preciseSeeking)
                    }

                    if (showSeekbarWhenSeeking) viewModel.showSeekBar()
                }
            }
            // Vertical volume/brightness gestures disabled if zoomed or zoom/pan active
            .pointerInput(areControlsLocked) {
                if ((!brightnessGesture && !volumeGesture) || areControlsLocked) return@pointerInput
                if (isZoomPanGesture || mpvView.getZoomLevel() > 1f) return@pointerInput
                var startingY = 0f
                var mpvVolumeStartingY = 0f
                var originalVolume = currentVolume
                var originalMPVVolume = currentMPVVolume
                var originalBrightness = currentBrightness
                val brightnessGestureSens = 0.001f
                val volumeGestureSens = 0.03f
                val mpvVolumeGestureSens = 0.02f
                val isIncreasingVolumeBoost: (Float) -> Boolean = {
                    volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
                            (currentMPVVolume ?: 100) - 100 < volumeBoostingCap && it < 0
                }
                val isDecreasingVolumeBoost: (Float) -> Boolean = {
                    volumeBoostingCap > 0 && currentVolume == viewModel.maxVolume &&
                            (currentMPVVolume ?: 100) - 100 in 1..volumeBoostingCap && it > 0
                }
                detectVerticalDragGestures(
                    onDragEnd = { startingY = 0f },
                    onDragStart = {
                        startingY = 0f
                        mpvVolumeStartingY = 0f
                        originalVolume = currentVolume
                        originalMPVVolume = currentMPVVolume
                        originalBrightness = currentBrightness
                    },
                ) { change, amount ->
                    val changeVolume: () -> Unit = {
                        if (isIncreasingVolumeBoost(amount) || isDecreasingVolumeBoost(amount)) {
                            if (mpvVolumeStartingY == 0f) {
                                startingY = 0f
                                originalVolume = currentVolume
                                mpvVolumeStartingY = change.position.y
                            }
                            viewModel.changeMPVVolumeTo(
                                calculateNewVerticalGestureValue(
                                    originalMPVVolume ?: 100,
                                    mpvVolumeStartingY,
                                    change.position.y,
                                    mpvVolumeGestureSens,
                                )
                                    .coerceIn(100..volumeBoostingCap + 100),
                            )
                        } else {
                            if (startingY == 0f) {
                                mpvVolumeStartingY = 0f
                                originalMPVVolume = currentMPVVolume
                                startingY = change.position.y
                            }
                            viewModel.changeVolumeTo(
                                calculateNewVerticalGestureValue(originalVolume, startingY, change.position.y, volumeGestureSens),
                            )
                        }
                        viewModel.displayVolumeSlider()
                    }
                    val changeBrightness: () -> Unit = {
                        if (startingY == 0f) startingY = change.position.y
                        viewModel.changeBrightnessTo(
                            calculateNewVerticalGestureValue(originalBrightness, startingY, change.position.y, brightnessGestureSens),
                        )
                        viewModel.displayBrightnessSlider()
                    }
                    when {
                        volumeGesture && brightnessGesture -> {
                            if (swapVolumeAndBrightness) {
                                if (change.position.x > size.width / 2) changeBrightness() else changeVolume()
                            } else {
                                if (change.position.x < size.width / 2) changeBrightness() else changeVolume()
                            }
                        }

                        brightnessGesture -> changeBrightness()
                        volumeGesture -> changeVolume()
                        else -> {}
                    }
                }
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTapToSeekOvals(
    amount: Int,
    text: String?,
    showOvals: Boolean,
    showSeekIcon: Boolean,
    showSeekTime: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (amount == 0) 0f else 0.2f, label = "double_tap_animation_alpha")
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (amount > 0) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        CompositionLocalProvider(
            LocalRippleConfiguration provides playerRippleConfiguration,
        ) {
            if (amount != 0) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (showOvals) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(if (amount > 0) RightSideOvalShape else LeftSideOvalShape)
                                .background(Color.White.copy(alpha))
                                .indication(interactionSource, ripple()),
                        )
                    }
                    if (showSeekIcon || showSeekTime) {
                        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DoubleTapSeekTriangles(isForward = amount > 0)
                            Text(
                                text = text ?: pluralStringResource(R.plurals.seconds, amount, amount),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

fun calculateNewVerticalGestureValue(originalValue: Int, startingY: Float, newY: Float, sensitivity: Float): Int {
    return originalValue + ((startingY - newY) * sensitivity).toInt()
}

fun calculateNewVerticalGestureValue(originalValue: Float, startingY: Float, newY: Float, sensitivity: Float): Float {
    return originalValue + ((startingY - newY) * sensitivity)
}

fun calculateNewHorizontalGestureValue(originalValue: Int, startingX: Float, newX: Float, sensitivity: Float): Int {
    return originalValue + ((newX - startingX) * sensitivity).toInt()
}

fun calculateNewHorizontalGestureValue(originalValue: Float, startingX: Float, newX: Float, sensitivity: Float): Float {
    return originalValue + ((newX - startingX) * sensitivity)
}
