package com.pomdo.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Timestamp-based Pomodoro engine. No ticking thread: the end time is stored, an exact alarm wakes
 * the app at each phase change, and the notification countdown is drawn by the system itself.
 * State is persisted, so the session survives the app being closed or the process being killed.
 */
object Focus {
    private const val LONG_EVERY = 4
    private const val NOTIF_ID = 1001
    private const val ALERT_ID = 1002
    private var loaded = false

    var active by mutableStateOf(false)
    var running by mutableStateOf(false)
    var finished by mutableStateOf(false)
    var phase by mutableStateOf(0)          // 0 focus, 1 short break, 2 long break
    var session by mutableStateOf(1)
    var total by mutableStateOf(4)
    var taskId by mutableStateOf(-1L)
    var endAt by mutableStateOf(0L)
    var leftMs by mutableStateOf(0L)
    var doneSec by mutableStateOf(0)
    var focusMin = 25
    var breakMin = 5
    var longMin = 15

    fun task(): Task? = Store.find(taskId)
    private fun name() = task()?.name ?: "Free focus"
    private fun cat() = task()?.category ?: Category.OTHER
    fun phaseMs(): Long = when (phase) { 0 -> focusMin; 1 -> breakMin; else -> longMin } * 60000L
    fun phaseLabel() = when (phase) { 0 -> "Focus"; 1 -> "Short break"; else -> "Long break" }
    fun leftNow(now: Long): Long = if (running) (endAt - now).coerceAtLeast(0L) else leftMs
    fun focusedSec(now: Long): Int = doneSec + if (active && phase == 0) ((phaseMs() - leftNow(now)) / 1000L).toInt().coerceAtLeast(0) else 0

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("pomdo_focus", Context.MODE_PRIVATE)

    private fun persist(ctx: Context) {
        prefs(ctx).edit().putBoolean("active", active).putBoolean("running", running).putBoolean("finished", finished)
            .putInt("phase", phase).putInt("session", session).putInt("total", total).putLong("taskId", taskId)
            .putLong("endAt", endAt).putLong("leftMs", leftMs).putInt("doneSec", doneSec)
            .putInt("fm", focusMin).putInt("bm", breakMin).putInt("lm", longMin).apply()
    }

    fun restore(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        active = p.getBoolean("active", false); running = p.getBoolean("running", false)
        finished = p.getBoolean("finished", false); phase = p.getInt("phase", 0)
        session = p.getInt("session", 1); total = p.getInt("total", 4); taskId = p.getLong("taskId", -1L)
        endAt = p.getLong("endAt", 0L); leftMs = p.getLong("leftMs", 0L); doneSec = p.getInt("doneSec", 0)
        focusMin = p.getInt("fm", 25); breakMin = p.getInt("bm", 5); longMin = p.getInt("lm", 15)
        if (active && running) advance(ctx)
    }

    /** Re-creates the alarm and notification (after reboot or app update). */
    fun rearm(ctx: Context) {
        if (active && running) { advance(ctx); if (active && running) { arm(ctx); ongoing(ctx) } }
        else if (active) ongoing(ctx)
    }

    private fun alarmPi(ctx: Context) = PendingIntent.getBroadcast(ctx, 9001,
        Intent(ctx, FocusReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun arm(ctx: Context) = Alarms.set(ctx, endAt, alarmPi(ctx))
    private fun disarm(ctx: Context) = Alarms.cancel(ctx, alarmPi(ctx))

    fun start(ctx: Context, t: Task?) {
        val c = ctx.applicationContext
        taskId = t?.id ?: -1L
        focusMin = t?.focusMin ?: Store.defFocus; breakMin = t?.breakMin ?: Store.defBreak
        longMin = Store.defLong; total = t?.sessions ?: 4
        session = 1; phase = 0; doneSec = 0; finished = false; active = true; running = true
        leftMs = phaseMs(); endAt = System.currentTimeMillis() + leftMs
        arm(c); ongoing(c); persist(c)
    }

    fun toggle(ctx: Context) {
        if (!active) return
        val c = ctx.applicationContext
        val now = System.currentTimeMillis()
        if (running) { leftMs = (endAt - now).coerceAtLeast(0L); running = false; disarm(c) }
        else { endAt = now + leftMs; running = true; arm(c) }
        ongoing(c); persist(c)
    }

    /** Idempotent: moves through every phase whose end time has passed. */
    fun advance(ctx: Context) {
        if (!active || !running) return
        val c = ctx.applicationContext
        val now = System.currentTimeMillis()
        var alert: String? = null
        while (active && running && now >= endAt) {
            val base = endAt
            if (phase == 0) {
                Store.addLog(FocusLog(base, focusMin, cat())); doneSec += focusMin * 60
                if (session >= total) { active = false; running = false; finished = true; alert = "All sessions complete. Great work!"; break }
                phase = if (session % LONG_EVERY == 0) 2 else 1
                alert = "Focus complete. Take a ${phaseMs() / 60000} min break"
            } else { session++; phase = 0; alert = "Break over. Time to focus" }
            endAt = base + phaseMs()
            if (now - endAt > 30 * 60000L) { active = false; running = false; finished = true; break }
        }
        if (alert != null) Notif.alert(c, name(), alert)
        if (active && running) { arm(c); ongoing(c) } else { disarm(c); Notif.nm(c).cancel(NOTIF_ID) }
        persist(c)
    }

    fun stop(ctx: Context, complete: Boolean) {
        if (!active) return
        val c = ctx.applicationContext
        val now = System.currentTimeMillis()
        if (phase == 0) {
            val min = ((phaseMs() - leftNow(now)) / 60000L).toInt()
            if (min >= 1) Store.addLog(FocusLog(now, min, cat()))
        }
        if (complete) task()?.let { if (!it.done) Store.toggle(it) }
        active = false; running = false; finished = true
        disarm(c); Notif.nm(c).cancel(NOTIF_ID); persist(c)
    }

    private fun ongoing(ctx: Context) {
        val b = Notification.Builder(ctx, Notif.focusChannel(ctx))
            .setSmallIcon(R.drawable.ic_stat).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(Notif.openPi(ctx, "focus", -1L, 11))
            .setContentTitle("${phaseLabel()} · ${name()}")
        if (running) {
            b.setWhen(endAt).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setContentText("Session ${session.coerceAtMost(total)} of $total")
                .addAction(Notif.action(ctx, "Pause", Notif.actionPi(ctx, "pause", -1L, 21)))
        } else {
            val l = leftMs / 1000
            b.setContentText("Paused · %d:%02d left".format(l / 60, l % 60))
                .addAction(Notif.action(ctx, "Resume", Notif.actionPi(ctx, "resume", -1L, 22)))
        }
        b.addAction(Notif.action(ctx, "Stop", Notif.actionPi(ctx, "stop", -1L, 23)))
        Notif.nm(ctx).notify(NOTIF_ID, b.build())
    }
}
