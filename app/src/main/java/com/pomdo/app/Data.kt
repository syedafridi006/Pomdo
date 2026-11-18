package com.pomdo.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class Category(val label: String, val emoji: String, val color: Long) {
    WORK("Work", "💼", 0xFFB5793B), STUDY("Study", "📘", 0xFF4A86E8),
    PERSONAL("Personal", "🌿", 0xFF6BBF59), HEALTH("Health", "❤️", 0xFFE5484D),
    FITNESS("Fitness", "🏋️", 0xFF7C6FE0), SHOPPING("Shopping", "🛒", 0xFF3AA0E0),
    HOME("Home", "🏠", 0xFFE8793A), OTHER("Other", "⭐", 0xFFE8B83A)
}
enum class Repeat(val label: String) { NONE("Never"), DAILY("Daily"), WEEKLY("Weekly") }
enum class SoundMode(val label: String) {
    VOICE_RINGTONE("Voice + ringtone"), RINGTONE("Ringtone"), VOICE("Voice"), SILENT("Silent")
}

data class Task(
    val id: Long, val name: String, val desc: String, val category: Category,
    val start: Long, val due: Long, val reminderMin: Int, val sound: SoundMode,
    val focusMin: Int, val breakMin: Int, val sessions: Int, val priority: Int,
    val repeat: Repeat, val done: Boolean = false, val completedAt: Long = 0L
)
data class FocusLog(val time: Long, val minutes: Int, val category: Category)

inline fun <reified T : Enum<T>> parseEnum(s: String?, d: T): T =
    enumValues<T>().firstOrNull { it.name == s } ?: d

object Store {
    lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private var inited = false
    private var lastId = 0L
    val tasks = mutableStateListOf<Task>()
    val logs = mutableStateListOf<FocusLog>()
    var defFocus by mutableStateOf(25)
    var defBreak by mutableStateOf(5)
    var defSound by mutableStateOf(SoundMode.VOICE_RINGTONE)

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        sp = app.getSharedPreferences("pomdo", Context.MODE_PRIVATE)
        defFocus = sp.getInt("defFocus", 25)
        defBreak = sp.getInt("defBreak", 5)
        defSound = parseEnum(sp.getString("defSound", ""), SoundMode.VOICE_RINGTONE)
        try {
            val a = JSONArray(sp.getString("tasks", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                tasks.add(Task(
                    o.getLong("id"), o.getString("name"), o.optString("desc"),
                    parseEnum(o.optString("cat"), Category.OTHER), o.getLong("start"), o.getLong("due"),
                    o.optInt("rem", 5), parseEnum(o.optString("snd"), SoundMode.SILENT),
                    o.optInt("fm", 25), o.optInt("bm", 5), o.optInt("ss", 4), o.optInt("pr", 1),
                    parseEnum(o.optString("rep"), Repeat.NONE), o.optBoolean("done"), o.optLong("doneAt")
                ))
            }
            val l = JSONArray(sp.getString("logs", "[]"))
            for (i in 0 until l.length()) {
                val o = l.getJSONObject(i)
                logs.add(FocusLog(o.getLong("t"), o.getInt("m"), parseEnum(o.optString("c"), Category.OTHER)))
            }
        } catch (e: Exception) { }
        lastId = tasks.maxOfOrNull { it.id } ?: 0L
    }

    fun newId(): Long { lastId = maxOf(System.currentTimeMillis(), lastId + 1); return lastId }

    fun save() {
        val a = JSONArray()
        tasks.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("desc", it.desc)
                .put("cat", it.category.name).put("start", it.start).put("due", it.due)
                .put("rem", it.reminderMin).put("snd", it.sound.name).put("fm", it.focusMin)
                .put("bm", it.breakMin).put("ss", it.sessions).put("pr", it.priority)
                .put("rep", it.repeat.name).put("done", it.done).put("doneAt", it.completedAt))
        }
        val l = JSONArray()
        logs.forEach { l.put(JSONObject().put("t", it.time).put("m", it.minutes).put("c", it.category.name)) }
        sp.edit().putString("tasks", a.toString()).putString("logs", l.toString())
            .putInt("defFocus", defFocus).putInt("defBreak", defBreak)
            .putString("defSound", defSound.name).apply()
    }

    fun addTask(t: Task) { tasks.add(t); Reminders.schedule(app, t); save() }

    fun delete(t: Task) { Reminders.cancel(app, t); tasks.removeAll { it.id == t.id }; save() }

    fun toggle(t: Task) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i < 0) return
        val cur = tasks[i]
        val nowDone = !cur.done
        val upd = cur.copy(done = nowDone, completedAt = if (nowDone) System.currentTimeMillis() else 0L)
        tasks[i] = upd
        if (nowDone) {
            Reminders.cancel(app, upd)
            if (upd.repeat != Repeat.NONE) {
                val step = if (upd.repeat == Repeat.DAILY) 86400000L else 7 * 86400000L
                addTask(upd.copy(id = newId(), start = upd.start + step, due = upd.due + step,
                    done = false, completedAt = 0L))
            }
        } else Reminders.schedule(app, upd)
        save()
    }

    fun addLog(l: FocusLog) { logs.add(l); save() }
    fun saveSettings() = save()
}

object Reminders {
    private fun pi(ctx: Context, t: Task, create: Boolean): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("id", t.id).putExtra("name", t.name).putExtra("min", t.reminderMin)
            .putExtra("sound", t.sound.name).putExtra("emoji", t.category.emoji)
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(ctx, (t.id % Int.MAX_VALUE).toInt(), i, flags)
    }

    fun schedule(ctx: Context, t: Task) {
        val at = t.start - t.reminderMin * 60000L
        if (t.done || at <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, t, true) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        }
    }

    fun cancel(ctx: Context, t: Task) {
        val p = pi(ctx, t, false) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(p)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Store.init(ctx)
        Store.tasks.forEach { Reminders.schedule(ctx, it) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        val name = i.getStringExtra("name") ?: "Task"
        val min = i.getIntExtra("min", 5)
        val mode = parseEnum(i.getStringExtra("sound"), SoundMode.SILENT)
        val emoji = i.getStringExtra("emoji") ?: "⏰"
        val text = if (min == 0) "$name starts now" else "$name starts in $min minutes"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel("pomdo_rem", "Task reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        nm.createNotificationChannel(ch)
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, "pomdo_rem")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("$emoji $name").setContentText(text)
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(i.getLongExtra("id", 0L).toInt(), n)

        val wantTone = mode == SoundMode.RINGTONE || mode == SoundMode.VOICE_RINGTONE
        val wantVoice = mode == SoundMode.VOICE || mode == SoundMode.VOICE_RINGTONE
        val pending = goAsync()
        val h = Handler(Looper.getMainLooper())
        var rt: Ringtone? = null
        if (wantTone) {
            rt = RingtoneManager.getRingtone(ctx, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
            rt?.play()
        }
        h.postDelayed({
            rt?.stop()
            if (wantVoice) {
                var tts: TextToSpeech? = null
                tts = TextToSpeech(ctx.applicationContext) { st ->
                    if (st == TextToSpeech.SUCCESS) tts?.speak("Reminder. $text", TextToSpeech.QUEUE_FLUSH, null, "pomdo")
                }
                h.postDelayed({ tts?.shutdown(); pending.finish() }, 6000)
            } else pending.finish()
        }, if (wantTone) 3000L else 0L)
    }
}

object Focus {
    var task by mutableStateOf<Task?>(null)
    var focusing by mutableStateOf(true)
    var running by mutableStateOf(false)
    var active by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var remaining by mutableStateOf(25 * 60)
    var session by mutableStateOf(1)
    var totalSessions by mutableStateOf(4)
    var focusedSec by mutableStateOf(0)
    var focusMin = 25
    var breakMin = 5
    private var unlogged = 0
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            remaining--
            if (focusing) { focusedSec++; unlogged++ }
            if (remaining <= 0) phaseDone() else handler.postDelayed(this, 1000)
        }
    }

    private fun cat() = task?.category ?: Category.OTHER

    private fun beep() {
        try { RingtoneManager.getRingtone(Store.app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)).play() } catch (e: Exception) { }
    }

    private fun flush() {
        if (unlogged >= 60) Store.addLog(FocusLog(System.currentTimeMillis(), unlogged / 60, cat()))
        unlogged = 0
    }

    private fun phaseDone() {
        beep()
        if (focusing) {
            flush()
            if (session >= totalSessions) { running = false; finished = true; active = false; remaining = 0; return }
            focusing = false; remaining = breakMin * 60
        } else {
            session++; focusing = true; remaining = focusMin * 60
        }
        handler.postDelayed(tick, 1000)
    }

    fun start(t: Task?) {
        handler.removeCallbacks(tick)
        task = t
        focusMin = t?.focusMin ?: Store.defFocus
        breakMin = t?.breakMin ?: Store.defBreak
        totalSessions = t?.sessions ?: 4
        session = 1; focusing = true; remaining = focusMin * 60
        unlogged = 0; focusedSec = 0; finished = false; active = true; running = true
        handler.postDelayed(tick, 1000)
    }

    fun toggle() {
        handler.removeCallbacks(tick)
        running = !running
        if (running) handler.postDelayed(tick, 1000)
    }

    fun finish() {
        handler.removeCallbacks(tick)
        flush()
        running = false; active = false; finished = true
        task?.let { t -> Store.tasks.firstOrNull { it.id == t.id }?.let { if (!it.done) Store.toggle(it) } }
    }
}
