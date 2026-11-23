package com.kevinconnors.scorestand.playlist

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.kevinconnors.scorestand.MainActivity
import com.kevinconnors.scorestand.ScoreStandApp

/**
 * Keeps a listening playlist playing with the screen off, and provides the notification and
 * lock-screen player (plus Bluetooth/headset buttons). Besides previous / play / next it has
 * Loop and Shuffle buttons whose icons show the mode. Modelled on FoldPlayer's service.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val holder get() = (application as ScoreStandApp).playlistPlayer

    private val modeListener = object : Player.Listener {
        override fun onRepeatModeChanged(repeatMode: Int) = refreshButtons()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = refreshButtons()
    }

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_NOW_PLAYING)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, holder.player)
            .setSessionActivity(open)
            .setCallback(Callback())
            .setCustomLayout(buttons())
            .build()
        holder.player.addListener(modeListener)
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(REPEAT).add(SHUFFLE).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(buttons())
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                REPEAT.customAction -> holder.cycleRepeat()
                SHUFFLE.customAction -> holder.toggleShuffle()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun buttons(): List<CommandButton> {
        val p = holder.player
        val repeat = when (p.repeatMode) {
            Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE to "Loop this track"
            Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL to "Loop the playlist"
            else -> CommandButton.ICON_REPEAT_OFF to "No loop"
        }
        val shuffle = if (p.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON to "Shuffle on" else CommandButton.ICON_SHUFFLE_OFF to "Shuffle off"
        return listOf(
            CommandButton.Builder(repeat.first).setDisplayName(repeat.second).setSessionCommand(REPEAT).build(),
            CommandButton.Builder(shuffle.first).setDisplayName(shuffle.second).setSessionCommand(SHUFFLE).build(),
        )
    }

    private fun refreshButtons() { session?.setCustomLayout(buttons()) }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        // The player belongs to the app; only the session is released here.
        holder.player.removeListener(modeListener)
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        val REPEAT = SessionCommand("com.kevinconnors.scorestand.REPEAT", Bundle.EMPTY)
        val SHUFFLE = SessionCommand("com.kevinconnors.scorestand.SHUFFLE", Bundle.EMPTY)
    }
}
