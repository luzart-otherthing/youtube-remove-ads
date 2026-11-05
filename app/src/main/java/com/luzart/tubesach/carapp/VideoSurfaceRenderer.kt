package com.luzart.tubesach.carapp

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.luzart.tubesach.player.PlaybackController
import com.luzart.tubesach.ui.components.formatMs

/**
 * Vẽ video lên màn hình xe (bề mặt "bản đồ" mà Android Auto cấp cho ứng dụng dẫn đường).
 *
 * Bề mặt này chỉ là một tấm ảnh trống; app tạo một màn hình ảo (VirtualDisplay) trên đó rồi đặt
 * giao diện Android bình thường lên: khung video (PlayerView – giữ đúng tỉ lệ, có phụ đề), tiêu đề,
 * thanh thời gian và dòng thông báo. Khung video chỉ nằm trong "vùng nhìn thấy" mà xe báo
 * (không bị các nút của Android Auto che).
 */
@OptIn(UnstableApi::class)
class VideoSurfaceRenderer(private val context: Context) : SurfaceCallback {

    /** Trạng thái hiển thị do màn hình xem video quyết định. */
    sealed interface Mode {
        /** Không ở màn hình xem video: hiện tên app. */
        data object Idle : Mode
        /** Đang xem, xe đỗ: hiện hình. */
        data object Video : Mode
        /** Đang xem nhưng không được hiện hình (xe chạy…): chỉ hiện chữ, vẫn phát tiếng. */
        data class AudioOnly(val message: String) : Mode
    }

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: CarPresentation? = null
    private var visibleArea: Rect? = null
    private var mode: Mode = Mode.Idle
    private val handler = Handler(Looper.getMainLooper())

    /** Bấm vào vùng video trên màn hình cảm ứng của xe. */
    var onTap: (() -> Unit)? = null

    fun setMode(m: Mode) {
        if (m == mode) return
        mode = m
        apply()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        release()
        val surface = surfaceContainer.surface ?: return
        val w = surfaceContainer.width
        val h = surfaceContainer.height
        if (w <= 0 || h <= 0) return
        try {
            val dm = context.getSystemService(DisplayManager::class.java)
            val vd = dm.createVirtualDisplay(
                "TubeSach-Car",
                w,
                h,
                surfaceContainer.dpi.coerceAtLeast(120),
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
            )
            virtualDisplay = vd
            val p = CarPresentation(context, vd.display)
            presentation = p
            p.show()
            apply()
        } catch (e: Exception) {
            // Không tạo được màn hình ảo (máy lạ): vẽ thẳng video lên bề mặt (có thể hơi méo hình).
            release()
            fallbackSurface = surface
            apply()
        }
    }

    /** Dự phòng khi không tạo được màn hình ảo. */
    private var fallbackSurface: android.view.Surface? = null

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = Rect(visibleArea)
        presentation?.setVisibleArea(visibleArea)
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        if (visibleArea == null) presentation?.setVisibleArea(stableArea)
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        release()
    }

    override fun onClick(x: Float, y: Float) {
        onTap?.invoke()
    }

    private fun apply() {
        val showVideo = mode == Mode.Video
        // Báo cho trình phát: màn hình xe đang cần hình (để không chỉ tải tiếng).
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR_SURFACE, showVideo && hasSurface())
        presentation?.render(mode)
        fallbackSurface?.let { s ->
            val player = PlaybackController.player
            if (showVideo) player.setVideoSurface(s) else player.clearVideoSurface(s)
        }
    }

    private fun hasSurface() = presentation != null || fallbackSurface != null

    fun release() {
        fallbackSurface?.let { runCatching { PlaybackController.player.clearVideoSurface(it) } }
        fallbackSurface = null
        presentation?.let { runCatching { it.dismiss() } }
        presentation = null
        virtualDisplay?.let { runCatching { it.release() } }
        virtualDisplay = null
        PlaybackController.setVideoVisible(PlaybackController.VIEWER_CAR_SURFACE, false)
    }

    // ---------------------------------------------------------------- Giao diện trên màn hình ảo

    private inner class CarPresentation(ctx: Context, display: android.view.Display) : Presentation(ctx, display) {

        private lateinit var root: FrameLayout
        private lateinit var content: FrameLayout
        private lateinit var playerView: PlayerView
        private lateinit var message: TextView
        private lateinit var info: LinearLayout
        private lateinit var title: TextView
        private lateinit var time: TextView
        private lateinit var progress: ProgressBar
        private lateinit var spinner: ProgressBar
        private var current: Mode = Mode.Idle

        private val ticker = object : Runnable {
            override fun run() {
                updateInfo()
                handler.postDelayed(this, 500)
            }
        }

        private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val ctx = context
            root = FrameLayout(ctx).apply { setBackgroundColor(Color.BLACK) }
            content = FrameLayout(ctx)
            root.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            playerView = PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setShutterBackgroundColor(Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                subtitleView?.setStyle(
                    CaptionStyleCompat(Color.WHITE, 0xBF000000.toInt(), Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_NONE, Color.WHITE, null),
                )
                subtitleView?.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.3f)
            }
            content.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

            spinner = ProgressBar(ctx).apply { isIndeterminate = true; visibility = View.GONE }
            content.addView(spinner, FrameLayout.LayoutParams(dp(56f), dp(56f), Gravity.CENTER))

            message = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                gravity = Gravity.CENTER
                setPadding(dp(24f), dp(16f), dp(24f), dp(16f))
                background = GradientDrawable().apply { setColor(0xCC000000.toInt()); cornerRadius = dp(16f).toFloat() }
            }
            content.addView(message, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

            info = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), dp(24f), dp(16f), dp(12f))
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xCC000000.toInt(), 0x00000000))
            }
            title = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            time = TextView(ctx).apply {
                setTextColor(0xFFDDDDDD.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progressTintList = android.content.res.ColorStateList.valueOf(0xFFFF0033.toInt())
            }
            info.addView(title)
            info.addView(time)
            info.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6f)))
            content.addView(info, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))

            content.setOnClickListener { onTap?.invoke() }
            setContentView(root)
            visibleArea?.let(::setVisibleArea)
            render(mode)
            handler.post(ticker)
        }

        fun setVisibleArea(area: Rect) {
            if (!::content.isInitialized) return
            val lp = content.layoutParams as FrameLayout.LayoutParams
            lp.leftMargin = area.left
            lp.topMargin = area.top
            lp.width = area.width()
            lp.height = area.height()
            content.layoutParams = lp
        }

        fun render(m: Mode) {
            if (!::content.isInitialized) return
            current = m
            val player = PlaybackController.player
            when (m) {
                Mode.Video -> {
                    if (playerView.player !== player) playerView.player = player
                    playerView.visibility = View.VISIBLE
                    message.visibility = View.GONE
                    info.visibility = View.VISIBLE
                }
                is Mode.AudioOnly -> {
                    playerView.player = null
                    playerView.visibility = View.INVISIBLE
                    message.text = m.message
                    message.visibility = View.VISIBLE
                    info.visibility = View.VISIBLE
                }
                Mode.Idle -> {
                    playerView.player = null
                    playerView.visibility = View.INVISIBLE
                    message.text = "Tube Sạch\nChọn một video để xem"
                    message.visibility = View.VISIBLE
                    info.visibility = View.GONE
                }
            }
            updateInfo()
        }

        private fun updateInfo() {
            if (current == Mode.Idle) {
                spinner.visibility = View.GONE
                return
            }
            val now = PlaybackController.nowPlaying.value
            val p = PlaybackController.player
            title.text = now?.video?.title.orEmpty()
            val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            val pos = p.currentPosition
            val live = now?.video?.isLive == true
            time.text = when {
                live -> "● TRỰC TIẾP"
                dur > 0 -> "${formatMs(pos)} / ${formatMs(dur)}" + if (!p.isPlaying && p.playbackState == Player.STATE_READY) "   ❚❚ Đang tạm dừng" else ""
                else -> ""
            }
            progress.visibility = if (dur > 0 && !live) View.VISIBLE else View.GONE
            if (dur > 0) progress.progress = (pos * 1000 / dur).toInt().coerceIn(0, 1000)
            val loading = PlaybackController.loading.value || p.playbackState == Player.STATE_BUFFERING
            spinner.visibility = if (current == Mode.Video && loading) View.VISIBLE else View.GONE
            PlaybackController.error.value?.let { err ->
                if (current == Mode.Video) {
                    message.text = err
                    message.visibility = View.VISIBLE
                }
            } ?: run { if (current == Mode.Video) message.visibility = View.GONE }
        }

        override fun onStop() {
            handler.removeCallbacks(ticker)
            playerView.player = null
            super.onStop()
        }
    }
}
