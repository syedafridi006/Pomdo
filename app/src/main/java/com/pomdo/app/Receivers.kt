package com.pomdo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object Notif {
    fun nm(ctx: Context): NotificationManager = ctx.getSystemService(NotificationManager::class.java)

    fun toneChannel(ctx: Context): String {
        val uri = if (Store.toneUri.isBlank()) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) else Uri.parse(Store.toneUri)
        val id = "pomdo_tone_" + uri.toString().hashCode()
        val ch = NotificationChannel(id, "Reminders with sound", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(uri, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        ch.enableVibration(true)
        nm(ctx).createNotificationChannel(ch)
        return id
    }

    fun silentChannel(ctx: Context): String {
        val ch = NotificationChannel("pomdo_silent", "Silent reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null); ch.enableVibration(true)
        nm(ctx).createNotificationChannel(ch)
        return "pomdo_silent"
    }

    fun chimeChannel(ctx: Context): String {
        val ch = NotificationChannel("pomdo_chime", "Notification reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        nm(ctx).createNotificationChannel(ch)
        return "pomdo_chime"
    }

    fun focusChannel(ctx: Context): String {
        val ch = NotificationChannel("pomdo_focus", "Focus timer", NotificationManager.IMPORTANCE_LOW)
        ch.setSound(null, null)
        nm(ctx).createNotificationChannel(ch)
        return "pomdo_focus"
    }

    fun action(ctx: Context, title: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_stat), title, pi).build()

    fun actionPi(ctx: Context, action: String, id: Long, code: Int): PendingIntent =
        PendingIntent.getBroadcast(ctx, code + id.hashCode(), Intent(ctx, ActionReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun openPi(ctx: Context, nav: String, id: Long, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code + id.hashCode(),
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("nav", nav).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun alert(ctx: Context, title: String, text: String) {
        val n = Notification.Builder(ctx, toneChannel(ctx)).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title).setContentText(text).setAutoCancel(true)
            .setContentIntent(openPi(ctx, "focus", -1L, 11)).build()
        nm(ctx).notify(1002, n)
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { boot(ctx); Focus.advance(ctx) }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        boot(ctx)
        Store.tasks.forEach { Reminders.schedule(ctx, it) }
        Focus.rearm(ctx)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        boot(ctx)
        val id = i.getLongExtra("id", -1L)
        when (i.action) {
            "pause", "resume" -> Focus.toggle(ctx)
            "stop" -> Focus.stop(ctx, false)
            "snooze" -> Store.find(id)?.let { Reminders.snooze(ctx, it, 5) }
            "done" -> Store.find(id)?.let { if (!it.done) Store.toggle(it) }
        }
        if (id != -1L) Notif.nm(ctx).cancel(id.hashCode())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        boot(ctx)
        val t = Store.find(i.getLongExtra("id", -1L)) ?: return
        if (t.done) return
        val pending = goAsync()
        fire(ctx, t) { pending.finish() }
    }

    companion object {
        /** Shows the notification and, for voice modes, speaks it. Also used by the Settings test button. */
        fun fire(ctx: Context, t: Task, onDone: () -> Unit) {
            val mins = ((t.start - System.currentTimeMillis() + 30000L) / 60000L).toInt()
            val text = if (mins <= 0) "${t.name} starts now" else "${t.name} starts in $mins minute" + if (mins == 1) "" else "s"
            val tone = t.sound == SoundMode.RINGTONE || t.sound == SoundMode.VOICE_RINGTONE
            val voice = t.sound == SoundMode.VOICE_NOTIFICATION || t.sound == SoundMode.VOICE_RINGTONE
            val channel = when (t.sound) {
                SoundMode.RINGTONE, SoundMode.VOICE_RINGTONE -> Notif.toneChannel(ctx)
                SoundMode.NOTIFICATION -> Notif.chimeChannel(ctx)
                else -> Notif.silentChannel(ctx)
            }
            val n = Notification.Builder(ctx, channel)
                .setSmallIcon(R.drawable.ic_stat).setContentTitle(t.name).setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true)
                .setContentIntent(Notif.openPi(ctx, "start", t.id, 31))
                .addAction(Notif.action(ctx, "Start focus", Notif.openPi(ctx, "start", t.id, 32)))
                .addAction(Notif.action(ctx, "Snooze 5m", Notif.actionPi(ctx, "snooze", t.id, 33)))
                .addAction(Notif.action(ctx, "Done", Notif.actionPi(ctx, "done", t.id, 34)))
                .build()
            Notif.nm(ctx).notify(t.id.hashCode(), n)
            Store.spawnNext(t.id)
            if (voice) speak(ctx, "Reminder. $text", if (tone) 3000L else 0L, onDone) else onDone()
        }

        private fun speak(ctx: Context, text: String, delayMs: Long, done: () -> Unit) {
            val h = Handler(Looper.getMainLooper())
            var tts: TextToSpeech? = null
            var over = false
            val end = { if (!over) { over = true; tts?.shutdown(); done() } }
            h.postDelayed({
                tts = TextToSpeech(ctx.applicationContext) { st ->
                    if (st == TextToSpeech.SUCCESS) {
                        val r = tts?.setLanguage(Locale.getDefault())
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts?.setLanguage(Locale.US)
                        tts?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(u: String?) {}
                            override fun onDone(u: String?) { h.post(end) }
                            @Deprecated("Deprecated in Java")
                            override fun onError(u: String?) { h.post(end) }
                        })
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pomdo")
                    } else end()
                }
            }, delayMs)
            h.postDelayed(end, delayMs + 7000L)
        }
    }
}
