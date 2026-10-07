package app.audioscribe.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import org.json.JSONObject

/**
 * Shows what's playing on the lock screen, in the notification and in Android Auto, and passes the
 * buttons (play, pause, next / previous chapter, skip, seek, chapter list, sleep timer) to the web page,
 * which does the actual playing.
 */
class MediaPlaybackService : MediaBrowserServiceCompat() {
    companion object {
        var instance: MediaPlaybackService? = null
        var commandSink: ((String, String) -> Unit)? = null
        private const val CHANNEL = "playback"
        private const val NOTE_ID = 1
    }

    private lateinit var session: MediaSessionCompat
    private var bookId = ""
    private var title = ""
    private var author = ""
    private var chapter = ""
    private var chapters: List<String> = emptyList()
    private var index = 0
    private var playing = false
    private var position = 0L
    private var duration = 0L
    private var foreground = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        session = MediaSessionCompat(this, "AudioScribe")
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = send("play", "")
            override fun onPause() = send("pause", "")
            override fun onStop() = send("pause", "")
            override fun onSkipToNext() = send("next", "")
            override fun onSkipToPrevious() = send("prev", "")
            override fun onFastForward() = send("fwd", "")
            override fun onRewind() = send("back", "")
            override fun onSeekTo(pos: Long) = send("seek", pos.toString())
            override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                if (mediaId != null && mediaId.startsWith("ch:")) send("chapter", mediaId.removePrefix("ch:"))
                else send("play", "")
            }
            override fun onCustomAction(action: String?, extras: Bundle?) {
                when (action) {
                    "sleep15" -> send("sleep", "15")
                    "sleepend" -> send("sleep", "end")
                }
            }
        })
        session.isActive = true
        sessionToken = session.sessionToken
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Playing", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        publish()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(session, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        session.release()
        instance = null
        NowPlayingWidget.refresh(this)                 // shows "play" again
        super.onDestroy()
    }

    private fun send(cmd: String, arg: String) {
        commandSink?.invoke(cmd, arg)
    }

    /** Called by the web page: {bookId, title, author, chapter, chapters[], index, playing, position, duration} (ms). */
    fun update(j: JSONObject) {
        val newBook = j.optString("bookId", bookId)
        title = j.optString("title", title)
        author = j.optString("author", author)
        chapter = j.optString("chapter", chapter)
        index = j.optInt("index", index)
        playing = j.optBoolean("playing", playing)
        position = j.optLong("position", position)
        duration = j.optLong("duration", duration)
        val arr = j.optJSONArray("chapters")
        val chaptersChanged = arr != null || newBook != bookId
        if (arr != null) chapters = List(arr.length()) { arr.optString(it) }
        bookId = newBook
        publish()
        if (chaptersChanged) notifyChildrenChanged("root")
    }

    private fun publish() {
        NowPlayingWidget.save(this, title, chapter, playing)
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, if (chapter.isNotEmpty()) chapter else title)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, author)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .build()
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_FAST_FORWARD or PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    position, if (playing) 1f else 0f)
                .addCustomAction("sleep15", "Sleep in 15 min", R.mipmap.ic_launcher)
                .addCustomAction("sleepend", "Sleep at chapter end", R.mipmap.ic_launcher)
                .build()
        )
        showNotification()
    }

    private fun showNotification() {
        if (title.isEmpty()) return
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun act(icon: Int, label: String, action: Long) = NotificationCompat.Action(icon, label,
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, action))
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (chapter.isNotEmpty()) chapter else title)
            .setContentText(title)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(act(android.R.drawable.ic_media_previous, "Previous chapter", PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS))
            .addAction(if (playing) act(android.R.drawable.ic_media_pause, "Pause", PlaybackStateCompat.ACTION_PAUSE)
                       else act(android.R.drawable.ic_media_play, "Play", PlaybackStateCompat.ACTION_PLAY))
            .addAction(act(android.R.drawable.ic_media_next, "Next chapter", PlaybackStateCompat.ACTION_SKIP_TO_NEXT))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2))
            .build()
        if (playing) {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
            ServiceCompat.startForeground(this, NOTE_ID, n, type)
            foreground = true
        } else {
            if (foreground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                foreground = false
            }
            getSystemService(NotificationManager::class.java).notify(NOTE_ID, n)
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot("root", null)

    override fun onLoadChildren(parentId: String, result: Result<List<MediaBrowserCompat.MediaItem>>) {
        val items = mutableListOf<MediaBrowserCompat.MediaItem>()
        if (parentId == "root" && title.isNotEmpty()) {
            items.add(MediaBrowserCompat.MediaItem(
                MediaDescriptionCompat.Builder().setMediaId("now").setTitle("Continue: $title")
                    .setSubtitle(chapter).build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            chapters.forEachIndexed { i, t ->
                items.add(MediaBrowserCompat.MediaItem(
                    MediaDescriptionCompat.Builder().setMediaId("ch:$i").setTitle(t)
                        .setSubtitle(if (i == index) "Now playing" else title).build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            }
        }
        result.sendResult(items)
    }
}
