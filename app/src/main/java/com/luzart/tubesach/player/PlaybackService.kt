package com.luzart.tubesach.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.luzart.tubesach.App
import com.luzart.tubesach.auto.ArtworkProvider
import com.luzart.tubesach.auto.CarLibrary
import com.luzart.tubesach.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/**
 * Dịch vụ phát nền: giữ âm thanh chạy khi tắt màn hình hoặc chuyển sang ứng dụng khác,
 * hiện nút điều khiển trên thanh thông báo và màn hình khoá, nhận nút tai nghe Bluetooth.
 *
 * Đồng thời là "thư viện" cho Android Auto: màn hình xe hiện danh sách Trang chủ / Kênh đăng ký / Đã xem…,
 * có ô tìm kiếm và nghe được bằng giọng nói ("Ok Google, mở … trên Tube Sạch").
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var library: CarLibrary

    override fun onCreate() {
        super.onCreate()
        library = CarLibrary(App.of(this).store, artworkUri = { ArtworkProvider.uriFor(this, it) })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val s = MediaLibrarySession.Builder(this, SessionPlayer(PlaybackController.player), LibraryCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(mediaButtons())
            .build()
        session = s
        addSession(s)
    }

    private fun mediaButtons(): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Lùi 10 giây")
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_10)
            .setDisplayName("Tới 10 giây")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName("Video tiếp theo")
            .setSessionCommand(CMD_NEXT)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName("Video trước")
            .setSessionCommand(CMD_PREVIOUS)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(CMD_NEXT)
                        .add(CMD_PREVIOUS)
                        .build(),
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CMD_NEXT.customAction -> PlaybackController.next()
                CMD_PREVIOUS.customAction -> PlaybackController.previous()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(library.root(), LibraryParams.Builder().setExtras(library.rootExtras()).build()),
            )

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = library.item(mediaId)
            return Futures.immediateFuture(
                if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                LibraryResult.ofItemList(paged(library.children(parentId), page, pageSize), params)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            scope.launch {
                val count = runCatching { library.search(query).size }.getOrDefault(0)
                session.notifySearchResultChanged(browser, query, count, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            try {
                LibraryResult.ofItemList(paged(library.search(query), page, pageSize), params)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                LibraryResult.ofError(SessionError.ERROR_IO)
            }
        }

        /** Chọn video trên màn hình xe, hoặc nói "Ok Google, mở … trên Tube Sạch". */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            runCatching { library.resolve(mediaItems) }.getOrDefault(emptyList()).toMutableList()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackController.onTaskRemoved()
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        val CMD_NEXT = SessionCommand("com.luzart.tubesach.NEXT", Bundle.EMPTY)
        val CMD_PREVIOUS = SessionCommand("com.luzart.tubesach.PREVIOUS", Bundle.EMPTY)

        fun <T> paged(list: List<T>, page: Int, pageSize: Int): List<T> {
            if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return list
            val from = (page.toLong() * pageSize).coerceAtMost(list.size.toLong()).toInt()
            val to = (from + pageSize).coerceAtMost(list.size)
            return list.subList(from, to)
        }
    }
}
