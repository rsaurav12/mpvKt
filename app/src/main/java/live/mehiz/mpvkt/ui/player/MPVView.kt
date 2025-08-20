package live.mehiz.mpvkt.ui.player

import android.content.Context
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.widget.ImageView
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.KeyMapping
import `is`.xyz.mpv.MPVLib
import live.mehiz.mpvkt.preferences.AdvancedPreferences
import live.mehiz.mpvkt.preferences.AudioPreferences
import live.mehiz.mpvkt.preferences.DecoderPreferences
import live.mehiz.mpvkt.preferences.PlayerPreferences
import live.mehiz.mpvkt.preferences.SubtitlesPreferences
import live.mehiz.mpvkt.ui.player.controls.components.panels.toColorHexString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.reflect.KProperty

class MPVView(context: Context, attributes: AttributeSet) : BaseMPVView(context, attributes), KoinComponent {

    private val audioPreferences: AudioPreferences by inject()
    private val playerPreferences: PlayerPreferences by inject()
    private val decoderPreferences: DecoderPreferences by inject()
    private val advancedPreferences: AdvancedPreferences by inject()
    private val subtitlesPreferences: SubtitlesPreferences by inject()

    var isExiting = false

    // Zoom and Pan state variables
    private var zoomLevel = 1.0f
    private var panX = 0.0f
    private var panY = 0.0f
    private val minZoom = 1.0f
    private val maxZoom = 2.0f

    // Matrix for transformations
    private val transformMatrix = Matrix()

    /**
     * Returns the video aspect ratio. Rotation is taken into account.
     */
    fun getVideoOutAspect(): Double? {
        return MPVLib.getPropertyDouble("video-params/aspect")?.let {
            if (it < 0.001) return 0.0
            if ((MPVLib.getPropertyInt("video-params/rotate") ?: 0) % 180 == 90) 1.0 / it else it
        }
    }

    /** Apply zoom and pan transformations to the video */
    private fun applyTransformations() {
        transformMatrix.reset()

        val centerX = width / 2.0f
        val centerY = height / 2.0f

        // Apply zoom
        transformMatrix.postScale(zoomLevel, zoomLevel, centerX, centerY)

        // Constrain pan to limits based on zoom and view size
        val maxPanX = (width * (zoomLevel - 1)) / 2
        val maxPanY = (height * (zoomLevel - 1)) / 2

        val constrainedPanX = panX.coerceIn(-maxPanX, maxPanX)
        val constrainedPanY = panY.coerceIn(-maxPanY, maxPanY)

        transformMatrix.postTranslate(constrainedPanX, constrainedPanY)

        // Apply transformation matrix and set scale type
        this.imageMatrix = transformMatrix
        this.scaleType = ImageView.ScaleType.MATRIX
    }

    /** Handle zoom gesture */
    fun onZoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        val newZoom = (zoomLevel * scaleFactor).coerceIn(minZoom, maxZoom)
        if (newZoom != zoomLevel) {
            val scaleChange = newZoom / zoomLevel
            panX = (panX - focusX) * scaleChange + focusX
            panY = (panY - focusY) * scaleChange + focusY

            zoomLevel = newZoom
            applyTransformations()
        }
    }

    /** Handle pan gesture */
    fun onPan(deltaX: Float, deltaY: Float) {
        if (zoomLevel > minZoom) {
            panX += deltaX
            panY += deltaY
            applyTransformations()
        }
    }

    /** Reset zoom and pan to default */
    fun resetZoomPan() {
        zoomLevel = minZoom
        panX = 0f
        panY = 0f
        applyTransformations()
    }

    /** Get current zoom level */
    fun getZoomLevel(): Float = zoomLevel

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = MPVLib.getPropertyString(name)
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1) MPVLib.setPropertyString(name, "no") else MPVLib.setPropertyInt(name, value)
        }
    }

    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    override fun initOptions() {
        setVo(if (decoderPreferences.gpuNext.get()) "gpu-next" else "gpu")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("hwdec", if (decoderPreferences.tryHWDecoding.get()) "auto" else "no")

        if (decoderPreferences.useYUV420P.get()) {
            MPVLib.setOptionString("vf", "format=yuv420p")
        }
        MPVLib.setOptionString("msg-level", "all=" + if (advancedPreferences.verboseLogging.get()) "v" else "warn")

        MPVLib.setPropertyBoolean("keep-open", true)
        MPVLib.setPropertyBoolean("input-default-bindings", true)

        MPVLib.setOptionString("tls-verify", "yes")
        MPVLib.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")

        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        MPVLib.setOptionString("screenshot-directory", screenshotDir.path)

        VideoFilters.entries.forEach {
            MPVLib.setOptionString(it.mpvProperty, it.preference(decoderPreferences).get().toString())
        }

        MPVLib.setOptionString("speed", playerPreferences.defaultSpeed.get().toString())
        MPVLib.setOptionString("vd-lavc-film-grain", "cpu")

        setupSubtitlesOptions()
        setupAudioOptions()
    }

    override fun observeProperties() {
        for ((name, format) in observedProps) MPVLib.observeProperty(name, format)
    }

    override fun postInitOptions() {
        when (decoderPreferences.debanding.get()) {
            Debanding.None -> {}
            Debanding.CPU -> MPVLib.command("vf", "add", "@deband:gradfun=radius=12")
            Debanding.GPU -> MPVLib.setOptionString("deband", "yes")
        }

        advancedPreferences.enabledStatisticsPage.get().let {
            if (it != 0) {
                MPVLib.command("script-binding", "stats/display-stats-toggle")
                MPVLib.command("script-binding", "stats/display-page-$it")
            }
        }
    }

    @Suppress("ReturnCount")
    fun onKey(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE || KeyEvent.isModifierKey(event.keyCode)) {
            return false
        }

        var mapped = KeyMapping[event.keyCode]
        if (mapped == null) {
            if (!event.isPrintingKey) {
                if (event.repeatCount == 0) {
                    Log.d(TAG, "Unmapped non-printable key ${event.keyCode}")
                }
                return false
            }

            val ch = event.unicodeChar
            if (ch.and(KeyCharacterMap.COMBINING_ACCENT) != 0) return false  // dead key

            mapped = ch.toChar().toString()
        }

        if (event.repeatCount > 0) return true

        val mod: MutableList<String> = mutableListOf()
        if (event.isShiftPressed) mod.add("shift")
        if (event.isCtrlPressed) mod.add("ctrl")
        if (event.isAltPressed) mod.add("alt")
        if (event.isMetaPressed) mod.add("meta")

        val action = if (event.action == KeyEvent.ACTION_DOWN) "keydown" else "keyup"
        mod.add(mapped)
        MPVLib.command(action, mod.joinToString("+"))

        return true
    }

    private val observedProps = mapOf(
        "pause" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,
        "video-params/aspect" to MPVLib.mpvFormat.MPV_FORMAT_DOUBLE,
        "eof-reached" to MPVLib.mpvFormat.MPV_FORMAT_FLAG,

        "user-data/mpvkt/show_text" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/toggle_ui" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/show_panel" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/set_button_title" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/reset_button_title" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/toggle_button" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/seek_by" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/seek_to" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/seek_by_with_text" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/seek_to_with_text" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
        "user-data/mpvkt/software_keyboard" to MPVLib.mpvFormat.MPV_FORMAT_STRING,
    )

    private fun setupAudioOptions() {
        MPVLib.setOptionString("alang", audioPreferences.preferredLanguages.get())
        MPVLib.setOptionString("audio-delay", (audioPreferences.defaultAudioDelay.get() / 1000.0).toString())
        MPVLib.setOptionString("audio-pitch-correction", audioPreferences.audioPitchCorrection.get().toString())
        MPVLib.setOptionString("volume-max", (audioPreferences.volumeBoostCap.get() + 100).toString())
    }

    private fun setupSubtitlesOptions() {
        MPVLib.setOptionString("slang", subtitlesPreferences.preferredLanguages.get())

        MPVLib.setOptionString("sub-fonts-dir", context.cacheDir.path + "/fonts/")
        MPVLib.setOptionString("sub-delay", (subtitlesPreferences.defaultSubDelay.get() / 1000.0).toString())
        MPVLib.setOptionString("sub-speed", subtitlesPreferences.defaultSubSpeed.get().toString())
        MPVLib.setOptionString("secondary-sub-delay", (subtitlesPreferences.defaultSecondarySubDelay.get() / 1000.0).toString())

        MPVLib.setOptionString("sub-font", subtitlesPreferences.font.get())
        if (subtitlesPreferences.overrideAssSubs.get()) {
            MPVLib.setOptionString("sub-ass-override", "force")
            MPVLib.setOptionString("sub-ass-justify", "yes")
        }
        MPVLib.setOptionString("sub-font-size", subtitlesPreferences.fontSize.get().toString())
        MPVLib.setOptionString("sub-bold", if (subtitlesPreferences.bold.get()) "yes" else "no")
        MPVLib.setOptionString("sub-italic", if (subtitlesPreferences.italic.get()) "yes" else "no")
        MPVLib.setOptionString("sub-justify", subtitlesPreferences.justification.get().value)
        MPVLib.setOptionString("sub-color", subtitlesPreferences.textColor.get().toColorHexString())
        MPVLib.setOptionString("sub-back-color", subtitlesPreferences.backgroundColor.get().toColorHexString())
        MPVLib.setOptionString("sub-border-color", subtitlesPreferences.borderColor.get().toColorHexString())
        MPVLib.setOptionString("sub-border-size", subtitlesPreferences.borderSize.get().toString())
        MPVLib.setOptionString("sub-border-style", subtitlesPreferences.borderStyle.get().value)
        MPVLib.setOptionString("sub-shadow-offset", subtitlesPreferences.shadowOffset.get().toString())
        MPVLib.setOptionString("sub-pos", subtitlesPreferences.subPos.get().toString())
        MPVLib.setOptionString("sub-scale", subtitlesPreferences.subScale.get().toString())
    }

    companion object {
        private const val TAG = "MPVView"
    }
}
