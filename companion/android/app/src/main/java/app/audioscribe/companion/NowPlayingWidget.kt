package app.audioscribe.companion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home-screen widget: the book and chapter you're on, with previous / play-pause / next buttons.
 * Tapping the title opens the app. If the app isn't running, play opens it and carries on where you left off.
 * What it shows is saved, so it still shows your book after a restart.
 */
class NowPlayingWidget : AppWidgetProvider() {
    companion object {
        private const val PREFS = "widget"
        private const val ACTION = "app.audioscribe.companion.WIDGET"

        /** Called by MediaPlaybackService whenever what's playing changes. */
        fun save(ctx: Context, title: String, chapter: String, playing: Boolean) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("title", title).putString("chapter", chapter).putBoolean("playing", playing).apply()
            refresh(ctx)
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NowPlayingWidget::class.java))
            if (ids.isNotEmpty()) mgr.updateAppWidget(ids, views(ctx))
        }

        private fun button(ctx: Context, cmd: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(ctx, code,
                Intent(ctx, NowPlayingWidget::class.java).setAction(ACTION).putExtra("cmd", cmd),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        private fun views(ctx: Context): RemoteViews {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val title = p.getString("title", "") ?: ""
            val playing = p.getBoolean("playing", false) && MediaPlaybackService.instance != null
            val v = RemoteViews(ctx.packageName, R.layout.widget_now_playing)
            v.setTextViewText(R.id.w_title, if (title.isEmpty()) "AudioScribe" else title)
            v.setTextViewText(R.id.w_chapter, if (title.isEmpty()) "Open a book to start" else p.getString("chapter", ""))
            v.setImageViewResource(R.id.w_play,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            v.setContentDescription(R.id.w_play, if (playing) "Pause" else "Play")
            val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            v.setOnClickPendingIntent(R.id.w_text, open)
            v.setOnClickPendingIntent(R.id.w_play, button(ctx, if (playing) "pause" else "resume", 1))
            v.setOnClickPendingIntent(R.id.w_prev, button(ctx, "prev", 2))
            v.setOnClickPendingIntent(R.id.w_next, button(ctx, "next", 3))
            return v
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        mgr.updateAppWidget(ids, views(ctx))
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action != ACTION) return
        val cmd = intent.getStringExtra("cmd") ?: return
        val sink = MediaPlaybackService.commandSink
        if (sink != null) {
            sink(cmd, "")
        } else if (cmd == "resume") {                       // app closed: open it and carry on
            ctx.startActivity(Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("widget_cmd", "resume"))
        }
    }
}
