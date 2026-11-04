package com.luzart.tubesach.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import com.luzart.tubesach.ui.MainActivity

/**
 * Dịch vụ phát nền: giữ âm thanh chạy khi tắt màn hình hoặc chuyển sang ứng dụng khác,
 * hiện nút điều khiển trên thanh thông báo và màn hình khoá, nhận nút tai nghe Bluetooth.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val rewind = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Lùi 10 giây")
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val forward = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setDisplayName("Tới 10 giây")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        val s = MediaSession.Builder(this, PlaybackController.player)
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(ImmutableList.of(rewind, forward))
            .build()
        session = s
        addSession(s)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackController.onTaskRemoved()
        stopSelf()
    }

    override fun onDestroy() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
