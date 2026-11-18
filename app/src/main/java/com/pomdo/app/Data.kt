package com.pomdo.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

enum class Category(val label: String, val color: Long) {
    WORK("Work", 0xFFE0A15B), STUDY("Study", 0xFF6AA2FF), PERSONAL("Personal", 0xFF7BD26B),
    HEALTH("Health", 0xFFFF6B6B), FITNESS("Fitness", 0xFFA38CFF), SHOPPING("Shopping", 0xFF4FC3F7),
    HOME("Home", 0xFFFF9150), OTHER("Other", 0xFFFFD24D)
}
enum class Repeat(val label: String) { NONE("Never"), DAILY("Daily"), WEEKDAYS("Weekdays"), WEEKLY("Weekly"), MONTHLY("Monthly") }
enum class SoundMode(val label: String) {
    VOICE_NOTIFICATION("Voice + notification"), VOICE_RINGTONE("Voice + ringtone"),
    RINGTONE("Ringtone"), NOTIFICATION("Notification"), SILENT("Silent")
}
fun migrateSound(s: String?) = if (s == "VOICE") "VOICE_NOTIFICATION" else s

data class Task(
    val id: Long, val name: String, val desc: String, val category: Category,
    val start: Long, val due: Long, val reminderMin: Int, val sound: SoundMode,
    val focusMin: Int, val breakMin: Int, val sessions: Int, val priority: Int,
    val repeat: Repeat, val done: Boolean = false, val completedAt: Long = 0L, val spawned: Boolean = false
)
data class FocusLog(val time: Long, val minutes: Int, val category: Category)

inline fun <reified T : Enum<T>> parseEnum(s: String?, d: T): T =
    enumValues<T>().firstOrNull { it.name == s } ?: d

fun nextOccurrence(ms: Long, r: Repeat): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    when (r) {
        Repeat.DAILY -> c.add(Calendar.DAY_OF_YEAR, 1)
        Repeat.WEEKLY -> c.add(Calendar.DAY_OF_YEAR, 7)
        Repeat.MONTHLY -> c.add(Calendar.MONTH, 1)
        Repeat.WEEKDAYS -> do { c.add(Calendar.DAY_OF_YEAR, 1) } while (c.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)
        Repeat.NONE -> {}
    }
    return c.timeInMillis
}

fun boot(ctx: Context) { Store.init(ctx); Focus.restore(ctx) }

object Store {
    lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private var inited = false
    private var lastId = 0L
    val tasks = mutableStateListOf<Task>()
    val logs = mutableStateListOf<FocusLog>()
    var defFocus by mutableStateOf(25)
    var defBreak by mutableStateOf(5)
    var defLong by mutableStateOf(15)
    var goal by mutableStateOf(120)
    var defSound by mutableStateOf(SoundMode.VOICE_NOTIFICATION)
    var toneUri by mutableStateOf("")
    var keepAwake by mutableStateOf(false)

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        sp = app.getSharedPreferences("pomdo", Context.MODE_PRIVATE)
        defFocus = sp.getInt("defFocus", 25); defBreak = sp.getInt("defBreak", 5)
        defLong = sp.getInt("defLong", 15); goal = sp.getInt("goal", 120)
        defSound = parseEnum(migrateSound(sp.getString("defSound", "")), SoundMode.VOICE_NOTIFICATION)
        toneUri = sp.getString("toneUri", "") ?: ""; keepAwake = sp.getBoolean("keepAwake", false)
        try {
            val a = JSONArray(sp.getString("tasks", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                tasks.add(Task(
                    o.getLong("id"), o.getString("name"), o.optString("desc"),
                    parseEnum(o.optString("cat"), Category.OTHER), o.getLong("start"), o.getLong("due"),
                    o.optInt("rem", 5), parseEnum(migrateSound(o.optString("snd")), SoundMode.VOICE_NOTIFICATION),
                    o.optInt("fm", 25), o.optInt("bm", 5), o.optInt("ss", 4), o.optInt("pr", 1),
                    parseEnum(o.optString("rep"), Repeat.NONE), o.optBoolean("done"), o.optLong("doneAt"), o.optBoolean("sp")
                ))
            }
            val cutoff = System.currentTimeMillis() - 400L * 86400000L
            val l = JSONArray(sp.getString("logs", "[]"))
            for (i in 0 until l.length()) {
                val o = l.getJSONObject(i)
                if (o.getLong("t") >= cutoff) logs.add(FocusLog(o.getLong("t"), o.getInt("m"), parseEnum(o.optString("c"), Category.OTHER)))
            }
        } catch (e: Exception) { }
        lastId = tasks.maxOfOrNull { it.id } ?: 0L
        rollRecurring()
    }

    fun newId(): Long { lastId = maxOf(System.currentTimeMillis(), lastId + 1); return lastId }

    fun save() {
        val a = JSONArray()
        tasks.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("desc", it.desc)
                .put("cat", it.category.name).put("start", it.start).put("due", it.due)
                .put("rem", it.reminderMin).put("snd", it.sound.name).put("fm", it.focusMin)
                .put("bm", it.breakMin).put("ss", it.sessions).put("pr", it.priority)
                .put("rep", it.repeat.name).put("done", it.done).put("doneAt", it.completedAt).put("sp", it.spawned))
        }
        val l = JSONArray()
        logs.forEach { l.put(JSONObject().put("t", it.time).put("m", it.minutes).put("c", it.category.name)) }
        sp.edit().putString("tasks", a.toString()).putString("logs", l.toString()).apply()
    }

    fun saveSettings() {
        sp.edit().putInt("defFocus", defFocus).putInt("defBreak", defBreak).putInt("defLong", defLong)
            .putInt("goal", goal).putString("defSound", defSound.name).putString("toneUri", toneUri)
            .putBoolean("keepAwake", keepAwake).apply()
    }

    fun find(id: Long) = tasks.firstOrNull { it.id == id }

    fun addTask(t: Task) {
        tasks.add(t); Reminders.schedule(app, t); save()
        if (t.repeat != Repeat.NONE && t.start - t.reminderMin * 60000L <= System.currentTimeMillis()) spawnNext(t.id)
    }

    fun update(t: Task) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i < 0) return
        Reminders.cancel(app, tasks[i]); tasks[i] = t; Reminders.schedule(app, t); save()
    }

    fun delete(t: Task) { Reminders.cancel(app, t); tasks.removeAll { it.id == t.id }; save() }

    fun toggle(t: Task) {
        val i = tasks.indexOfFirst { it.id == t.id }
        if (i < 0) return
        val cur = tasks[i]
        val nowDone = !cur.done
        val upd = cur.copy(done = nowDone, completedAt = if (nowDone) System.currentTimeMillis() else 0L)
        tasks[i] = upd
        if (nowDone) { Reminders.cancel(app, upd); save(); spawnNext(upd.id) }
        else { Reminders.schedule(app, upd); save() }
    }

    /** Creates the next occurrence of a recurring task exactly once, so the chain never breaks. */
    fun spawnNext(id: Long) {
        val i = tasks.indexOfFirst { it.id == id }
        if (i < 0) return
        val t = tasks[i]
        if (t.repeat == Repeat.NONE || t.spawned) return
        tasks[i] = t.copy(spawned = true)
        val len = t.due - t.start
        var s = nextOccurrence(t.start, t.repeat)
        while (s + len < System.currentTimeMillis()) s = nextOccurrence(s, t.repeat)
        addTask(t.copy(id = newId(), start = s, due = s + len, done = false, completedAt = 0L, spawned = false))
    }

    fun rollRecurring() {
        val now = System.currentTimeMillis()
        tasks.filter { it.repeat != Repeat.NONE && !it.spawned && it.start <= now }.map { it.id }.forEach { spawnNext(it) }
    }

    fun applySoundToAll() {
        for (i in tasks.indices) if (tasks[i].sound != defSound) tasks[i] = tasks[i].copy(sound = defSound)
        save()
    }

    fun addLog(l: FocusLog) { logs.add(l); save() }
}

object Alarms {
    fun set(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
    fun cancel(ctx: Context, pi: PendingIntent) = ctx.getSystemService(AlarmManager::class.java).cancel(pi)
}

object Reminders {
    private fun pi(ctx: Context, id: Long, kind: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(ctx, id.hashCode() * 2 + kind,
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", id), flags or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(ctx: Context, t: Task) {
        val at = t.start - t.reminderMin * 60000L
        if (t.done || at <= System.currentTimeMillis()) return
        Alarms.set(ctx, at, pi(ctx, t.id, 0, PendingIntent.FLAG_UPDATE_CURRENT) ?: return)
    }

    fun snooze(ctx: Context, t: Task, min: Int) {
        Alarms.set(ctx, System.currentTimeMillis() + min * 60000L, pi(ctx, t.id, 1, PendingIntent.FLAG_UPDATE_CURRENT) ?: return)
    }

    fun cancel(ctx: Context, t: Task) {
        pi(ctx, t.id, 0, PendingIntent.FLAG_NO_CREATE)?.let { Alarms.cancel(ctx, it) }
        pi(ctx, t.id, 1, PendingIntent.FLAG_NO_CREATE)?.let { Alarms.cancel(ctx, it) }
    }
}
