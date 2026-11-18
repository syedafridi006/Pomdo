@file:OptIn(ExperimentalFoundationApi::class)

package com.pomdo.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Theme (Study Python focus theme everywhere) ----------
val BG = Color(0xFF0F2430)
val CardC = Color(0xFF173545)
val Card2 = Color(0xFF1F4456)
val Green = Color(0xFF2E9B5F)
val Accent = Color(0xFF9BE3A0)
val Muted = Color(0xFFA7BCC6)
val Warn = Color(0xFFF2C14E)
val Danger = Color(0xFFFF7A7A)

@Composable
fun PomdoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = BG, background = BG, surface = CardC,
            onSurface = Color.White, onBackground = Color.White, surfaceVariant = Card2
        ), content = content
    )
}

// ---------- Helpers ----------
fun fmt(p: String, ms: Long): String = SimpleDateFormat(p, Locale.getDefault()).format(Date(ms))
fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
fun sameDay(a: Long, b: Long) = startOfDay(a) == startOfDay(b)
fun whenLabel(ms: Long) = if (sameDay(ms, System.currentTimeMillis())) "Today, " + fmt("h:mm a", ms) else fmt("MMM d, h:mm a", ms)
fun hm(min: Int) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

fun pickDateTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, mi ->
            onPicked(Calendar.getInstance().apply {
                set(y, m, d, h, mi, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Green else Card2).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 13.sp, color = if (selected) Color.White else Muted) }
}

@Composable
fun PriorityChip(p: Int) {
    val (label, col) = when (p) { 0 -> "Low" to Accent; 2 -> "High" to Danger; else -> "Med" to Warn }
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TaskRow(t: Task, onToggle: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(CardC)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(t.category.color).copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Text(t.category.emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, fontWeight = FontWeight.SemiBold, textDecoration = if (t.done) TextDecoration.LineThrough else null)
            Text("${t.category.label} · ${fmt("h:mm a", t.start)}" + if (t.repeat != Repeat.NONE) " · 🔁" else "", color = Muted, fontSize = 12.sp)
        }
        PriorityChip(t.priority)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(26.dp).clip(CircleShape).border(2.dp, if (t.done) Accent else Muted, CircleShape)
                .background(if (t.done) Accent else Color.Transparent).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) { if (t.done) Text("✓", color = BG, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun Ring(fraction: Float, size: androidx.compose.ui.unit.Dp, stroke: Float, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke * density
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(this.size.width - sw, this.size.height - sw)
            drawArc(color = Accent.copy(alpha = 0.2f), startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = tl, size = sz, style = Stroke(sw))
            drawArc(color = Accent, startAngle = -90f, sweepAngle = 360f * fraction.coerceIn(0f, 1f), useCenter = false, topLeft = tl, size = sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

// ---------- App shell ----------
@Composable
fun PomdoApp() {
    var tab by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("main") }
    BackHandler(screen != "main") { screen = "main" }
    val startFocus: (Task?) -> Unit = { t ->
        if (!(Focus.active && Focus.task?.id == t?.id)) Focus.start(t)
        screen = "focus"
    }
    when (screen) {
        "create" -> CreateScreen { screen = "main" }
        "focus" -> FocusScreen { screen = "main" }
        else -> Scaffold(
            containerColor = BG,
            bottomBar = {
                NavigationBar(containerColor = CardC) {
                    listOf("🏠" to "Home", "✅" to "Tasks", "📊" to "Progress", "⚙️" to "Settings").forEachIndexed { i, p ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Text(p.first, fontSize = 20.sp) }, label = { Text(p.second) },
                            colors = NavigationBarItemDefaults.colors(selectedTextColor = Accent, unselectedTextColor = Muted, indicatorColor = Card2)
                        )
                    }
                }
            },
            floatingActionButton = {
                if (tab == 1) FloatingActionButton(onClick = { screen = "create" }, containerColor = Green, contentColor = Color.White) { Text("+", fontSize = 28.sp) }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(startFocus) { tab = 1 }
                    1 -> TasksScreen(startFocus)
                    2 -> ProgressScreen()
                    else -> SettingsScreen()
                }
            }
        }
    }
}

// ---------- Home ----------
@Composable
fun HomeScreen(startFocus: (Task?) -> Unit, seeAll: () -> Unit) {
    val now = System.currentTimeMillis()
    val today = Store.tasks.filter { sameDay(it.start, now) }
    val done = today.count { it.done }
    val frac = if (today.isEmpty()) 0f else done.toFloat() / today.size
    val focusToday = Store.logs.filter { sameDay(it.time, now) }.sumOf { it.minutes }
    val next = Store.tasks.filter { !it.done && it.start >= startOfDay(now) }.sortedBy { it.start }.take(3)
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = if (hour < 12) "Good morning!" else if (hour < 17) "Good afternoon!" else "Good evening!"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(fmt("EEEE, MMM d", now).uppercase(), color = Muted, fontSize = 12.sp)
        Text(greet, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("🍅 pomdo", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Accent, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text("Plan · Focus · Achieve", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardC).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today's progress", color = Muted, fontSize = 13.sp)
                Text("${(frac * 100).toInt()}%", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("$done of ${today.size} completed", color = Muted, fontSize = 13.sp)
            }
            Ring(frac, 90.dp, 10f)
        }
        Spacer(Modifier.height(12.dp))
        Text("⏱ ${hm(focusToday)} focus", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Up next", fontWeight = FontWeight.Bold)
            Text("See all →", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { seeAll() })
        }
        if (next.isEmpty()) Text("Nothing scheduled. Add a task from the Tasks tab.", color = Muted, modifier = Modifier.padding(vertical = 14.dp))
        next.forEach { TaskRow(it, { Store.toggle(it) }, { startFocus(it) }, {}) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Green.copy(alpha = 0.35f)).clickable { startFocus(null) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✨", fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (Focus.active) "Focus session running" else "Ready for a focus session?", fontSize = 13.sp)
                Text(if (Focus.active) "%02d:%02d".format(Focus.remaining / 60, Focus.remaining % 60) else "%02d:00".format(Store.defFocus), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(44.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) { Text("▶", color = BG) }
        }
    }
}

// ---------- Tasks ----------
@Composable
fun TasksScreen(startFocus: (Task?) -> Unit) {
    var filter by remember { mutableStateOf(0) }
    var toDelete by remember { mutableStateOf<Task?>(null) }
    val now = System.currentTimeMillis()
    val endToday = startOfDay(now) + 86400000L
    val list = Store.tasks.filter {
        when (filter) {
            0 -> !it.done && it.start < endToday && it.due >= startOfDay(now) || (it.done && sameDay(it.start, now))
            1 -> !it.done && it.start >= endToday
            2 -> !it.done && it.due < startOfDay(now)
            else -> it.done
        }
    }.sortedBy { it.start }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("My tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Today", "Upcoming", "Overdue", "Completed").forEachIndexed { i, s -> Pill(s, filter == i) { filter = i } }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("No tasks here yet. Tap + to create one.", color = Muted, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(list, key = { it.id }) { t -> TaskRow(t, { Store.toggle(t) }, { startFocus(t) }, { toDelete = t }) }
        }
    }
    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete task?") }, text = { Text(t.name) },
            confirmButton = { TextButton({ Store.delete(t); toDelete = null }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } }
        )
    }
}


// ---------- Create ----------
@Composable
fun SRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted)
        Text("$value  ›", fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CreateScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(Category.WORK) }
    val base = remember { System.currentTimeMillis() + 3600000L }
    var start by remember { mutableStateOf(base) }
    var due by remember { mutableStateOf(base + 3600000L) }
    var rem by remember { mutableStateOf(5) }
    var sound by remember { mutableStateOf(Store.defSound) }
    var rep by remember { mutableStateOf(Repeat.NONE) }
    var pom by remember { mutableStateOf(Store.defFocus to Store.defBreak) }
    var sessions by remember { mutableStateOf(4) }
    var prio by remember { mutableStateOf(1) }
    val presets = listOf(25 to 5, 50 to 10, 15 to 3, 45 to 15)
    val remOpts = listOf(0, 5, 10, 15, 30)
    val tf = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Card2, focusedContainerColor = CardC, unfocusedContainerColor = CardC)
    val save = {
        if (name.isNotBlank()) {
            Store.addTask(Task(Store.newId(), name.trim(), desc.trim(), cat, start, maxOf(due, start), rem, sound, pom.first, pom.second, sessions, prio, rep))
            onClose()
        }
    }
    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", fontSize = 24.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp))
            Text("Create a task", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("✓", fontSize = 24.sp, color = Accent, modifier = Modifier.clickable { save() }.padding(8.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Task name *", color = Muted, fontSize = 13.sp)
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, colors = tf, placeholder = { Text("e.g. Study Python") })
            Spacer(Modifier.height(10.dp))
            Text("Description (optional)", color = Muted, fontSize = 13.sp)
            OutlinedTextField(desc, { desc = it }, Modifier.fillMaxWidth(), minLines = 2, colors = tf)
            Spacer(Modifier.height(14.dp))
            Text("Category", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Category.values().toList().chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CardC)
                                .border(2.dp, if (cat == c) Accent else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { cat = c }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(c.emoji, fontSize = 26.sp)
                            Text(c.label, fontSize = 11.sp, color = if (cat == c) Accent else Muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Schedule", fontWeight = FontWeight.Bold)
            SRow("🗓 Start", whenLabel(start)) { pickDateTime(ctx, start) { start = it; if (due < it) due = it + 3600000L } }
            SRow("🗓 Due", whenLabel(due)) { pickDateTime(ctx, due) { due = it } }
            SRow("🔔 Reminder", if (rem == 0) "At start time" else "$rem minutes before") { rem = remOpts[(remOpts.indexOf(rem) + 1) % remOpts.size] }
            SRow("🎵 Sound", sound.label) { sound = SoundMode.values()[(sound.ordinal + 1) % SoundMode.values().size] }
            SRow("🔁 Repeat", rep.label) { rep = Repeat.values()[(rep.ordinal + 1) % Repeat.values().size] }
            SRow("🍅 Pomodoro", "${pom.first} focus • ${pom.second} min break") { pom = presets[(presets.indexOf(pom) + 1) % presets.size] }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Sessions", Modifier.weight(1f), color = Muted)
                Text("−", fontSize = 24.sp, modifier = Modifier.clickable { if (sessions > 1) sessions-- }.padding(horizontal = 14.dp))
                Text("$sessions", fontWeight = FontWeight.Bold)
                Text("+", fontSize = 24.sp, modifier = Modifier.clickable { if (sessions < 12) sessions++ }.padding(horizontal = 14.dp))
            }
            Text("Total focus time: ${hm(pom.first * sessions)}", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Priority", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
                listOf("Low", "Medium", "High").forEachIndexed { i, s -> Pill(s, prio == i) { prio = i } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { save() }, enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)
        ) { Text("Save task", fontSize = 16.sp) }
    }
}

// ---------- Focus ----------
@Composable
fun FocusScreen(onClose: () -> Unit) {
    val t = Focus.task
    val total = (if (Focus.focusing) Focus.focusMin else Focus.breakMin) * 60
    val frac = if (total == 0) 0f else Focus.remaining / total.toFloat()
    Column(Modifier.fillMaxSize().background(BG).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { Text("←", fontSize = 24.sp, modifier = Modifier.clickable { onClose() }.padding(8.dp)) }
        Text(if (Focus.finished) "SESSION COMPLETE" else if (Focus.focusing) "CURRENT TASK · FOCUSING" else "TAKE A BREAK", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("${t?.category?.emoji ?: "🍅"} ${t?.name ?: "Free focus"}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Ring(if (Focus.finished) 1f else frac, 270.dp, 16f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (Focus.finished) "🎉" else "%02d:%02d".format(Focus.remaining / 60, Focus.remaining % 60), fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Text("Session ${Focus.session.coerceAtMost(Focus.totalSessions)} of ${Focus.totalSessions}", color = Muted)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("🎯 ${Focus.focusedSec / 60} / ${Focus.focusMin * Focus.totalSessions} min focused", color = Muted)
        Spacer(Modifier.weight(1f))
        if (!Focus.finished) {
            Button(
                onClick = { Focus.toggle() }, modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BG)
            ) { Text(if (Focus.running) "❚❚  Pause" else "▶  Resume", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { Focus.finish() }, modifier = Modifier.fillMaxWidth().height(54.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White)
            ) { Text("✓  Finish", color = Color.White, fontSize = 17.sp) }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.clip(RoundedCornerShape(20.dp)).background(Card2).padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(if (Focus.focusing) "☕ Next break · ${Focus.breakMin} minutes" else "🍅 Next focus · ${Focus.focusMin} minutes", fontSize = 13.sp)
            }
        } else {
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BG)) { Text("Done", fontSize = 17.sp) }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ---------- Progress ----------
data class Bucket(val label: String, val from: Long, val to: Long)

fun buckets(range: Int): List<Bucket> {
    val day = 86400000L
    val today = startOfDay(System.currentTimeMillis())
    return when (range) {
        0 -> (6 downTo 0).map { val s = today - it * day; Bucket(fmt("EEE", s), s, s + day) }
        1 -> (5 downTo 0).map { val s = today - (it * 5L + 4) * day; Bucket(fmt("d/M", s), s, s + 5 * day) }
        else -> (11 downTo 0).map {
            val c = Calendar.getInstance().apply { timeInMillis = today; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -it) }
            val s = c.timeInMillis
            c.add(Calendar.MONTH, 1)
            Bucket(fmt("MMM", s).take(1), s, c.timeInMillis)
        }
    }
}

fun streak(): Int {
    val days = (Store.logs.map { startOfDay(it.time) } + Store.tasks.filter { it.done }.map { startOfDay(it.completedAt) }).toSet()
    var d = startOfDay(System.currentTimeMillis())
    if (d !in days) d -= 86400000L
    var n = 0
    while (d in days) { n++; d -= 86400000L }
    return n
}

@Composable
fun StatCard(modifier: Modifier, icon: String, big: String, small: String) {
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(CardC).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 26.sp)
        Spacer(Modifier.width(10.dp))
        Column { Text(big, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(small, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
fun ProgressScreen() {
    var range by remember { mutableStateOf(0) }
    val bs = buckets(range)
    val from = bs.first().from
    val to = bs.last().to
    val tasksDone = Store.tasks.count { it.done && it.completedAt in from until to }
    val logs = Store.logs.filter { it.time in from until to }
    val mins = logs.sumOf { it.minutes }
    val perTasks = bs.map { b -> Store.tasks.count { it.done && it.completedAt in b.from until b.to }.toFloat() }
    val perMins = bs.map { b -> Store.logs.filter { it.time in b.from until b.to }.sumOf { it.minutes }.toFloat() }
    val labels = bs.map { it.label }
    val byCat = Category.values().map { c -> c to logs.filter { it.category == c }.sumOf { it.minutes } }.filter { it.second > 0 }.sortedByDescending { it.second }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Your progress", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row { listOf("Week", "Month", "Year").forEachIndexed { i, s -> Pill(s, range == i) { range = i } } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), "✅", "$tasksDone", "tasks done")
            StatCard(Modifier.weight(1f), "🕒", hm(mins), "focused")
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC).padding(14.dp)) {
            Text("Tasks completed", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            BarChart(perTasks, labels)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC).padding(14.dp)) {
            Text("Focus time (minutes)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LineChart(perMins, labels)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardC).padding(14.dp)) {
            Text("Category breakdown", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (byCat.isEmpty()) Text("No focus sessions in this period yet.", color = Muted)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    byCat.forEach { (c, m) ->
                        Row { Text("${c.emoji} ${c.label}", Modifier.weight(1f), fontSize = 13.sp); Text("${m * 100 / mins}%", fontSize = 13.sp) }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                Canvas(Modifier.size(110.dp)) {
                    var a = -90f
                    val sw = 26f
                    byCat.forEach { (c, m) ->
                        val sweep = 360f * m / mins
                        drawArc(color = Color(c.color), startAngle = a, sweepAngle = (sweep - 2f).coerceAtLeast(1f), useCenter = false,
                            topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                        a += sweep
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Green.copy(alpha = 0.35f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column { Text("${streak()} day streak", fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("Keep going!", color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
fun BarChart(values: List<Float>, labels: List<String>) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val max = maxOf(values.maxOrNull() ?: 1f, 1f)
            val w = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = maxOf(size.height * v / max, 4f)
                drawRoundRect(color = if (v > 0) Accent else Card2, topLeft = Offset(i * w + w * 0.25f, size.height - h), size = Size(w * 0.5f, h), cornerRadius = CornerRadius(8f, 8f))
            }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = Muted) } }
    }
}

@Composable
fun LineChart(values: List<Float>, labels: List<String>) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 6.dp)) {
            val max = maxOf(values.maxOrNull() ?: 1f, 1f)
            val w = size.width / values.size
            val pts = values.mapIndexed { i, v -> Offset(i * w + w / 2, size.height - size.height * v / max) }
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, Accent, style = Stroke(5f, cap = StrokeCap.Round))
            pts.forEach { drawCircle(Accent, 8f, it) }
        }
        Row(Modifier.fillMaxWidth()) { labels.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 10.sp, color = Muted) } }
    }
}

// ---------- Settings ----------
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Pomodoro defaults", fontWeight = FontWeight.Bold)
        Stepper("Focus length (min)", Store.defFocus, 5, 120) { Store.defFocus = it; Store.saveSettings() }
        Stepper("Break length (min)", Store.defBreak, 1, 60) { Store.defBreak = it; Store.saveSettings() }
        Spacer(Modifier.height(10.dp))
        Text("Reminders", fontWeight = FontWeight.Bold)
        SRow("🎵 Default sound", Store.defSound.label) {
            Store.defSound = SoundMode.values()[(Store.defSound.ordinal + 1) % SoundMode.values().size]; Store.saveSettings()
        }
        if (Build.VERSION.SDK_INT >= 31) SRow("⏰ Allow exact alarms", "Open") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(android.net.Uri.parse("package:" + ctx.packageName)))
        }
        Spacer(Modifier.height(20.dp))
        Text("Pomdo 1.0 · Plan · Focus · Achieve", color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted)
        Text("−", fontSize = 26.sp, modifier = Modifier.clickable { if (value > min) onChange(value - 5.coerceAtMost(value - min)) }.padding(horizontal = 16.dp))
        Text("$value", fontWeight = FontWeight.Bold)
        Text("+", fontSize = 26.sp, modifier = Modifier.clickable { if (value < max) onChange(minOf(max, value + 5)) }.padding(horizontal = 16.dp))
    }
}
