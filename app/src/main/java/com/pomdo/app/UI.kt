package com.pomdo.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------- Theme: the "Study Python" focus palette on every screen ----------
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

fun Category.icon(): ImageVector = when (this) {
    Category.WORK -> Icons.Rounded.Work
    Category.STUDY -> Icons.Rounded.MenuBook
    Category.PERSONAL -> Icons.Rounded.Spa
    Category.HEALTH -> Icons.Rounded.Favorite
    Category.FITNESS -> Icons.Rounded.FitnessCenter
    Category.SHOPPING -> Icons.Rounded.ShoppingCart
    Category.HOME -> Icons.Rounded.Home
    Category.OTHER -> Icons.Rounded.Star
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
fun clock(ms: Long): String { val s = (ms + 999) / 1000; return "%02d:%02d".format(s / 60, s % 60) }

fun pickDateTime(ctx: Context, initial: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, m, d ->
        TimePickerDialog(ctx, { _, h, mi ->
            onPicked(Calendar.getInstance().apply { set(y, m, d, h, mi, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

/** One-second clock that only runs while enabled (screen visible + timer running). */
@Composable
fun rememberNow(enabled: Boolean): Long {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(enabled) {
        while (enabled) {
            now = System.currentTimeMillis()
            Focus.advance(ctx)
            delay(1000L - now % 1000L + 5L)
        }
        now = System.currentTimeMillis()
    }
    return now
}

@Composable
fun CatTile(c: Category, size: Dp = 44.dp) {
    val col = Color(c.color)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.linearGradient(listOf(col.copy(alpha = 0.38f), col.copy(alpha = 0.14f)))),
        contentAlignment = Alignment.Center
    ) { Icon(c.icon(), null, tint = col, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).clip(RoundedCornerShape(20.dp))
            .background(if (selected) Green else Card2).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, fontSize = 13.sp, color = if (selected) Color.White else Muted, fontWeight = FontWeight.Medium) }
}

@Composable
fun PriorityChip(p: Int) {
    val (label, col) = when (p) { 0 -> "Low" to Accent; 2 -> "High" to Danger; else -> "Med" to Warn }
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(col.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
        Text(label, color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(t: Task, onToggle: () -> Unit, onClick: () -> Unit, onLong: () -> Unit) {
    val late = !t.done && t.due < System.currentTimeMillis()
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp)).background(CardC)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatTile(t.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, fontWeight = FontWeight.SemiBold, textDecoration = if (t.done) TextDecoration.LineThrough else null, color = if (t.done) Muted else Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${t.category.label} · ${fmt("h:mm a", t.start)}", color = if (late) Danger else Muted, fontSize = 12.sp)
                if (t.repeat != Repeat.NONE) Icon(Icons.Rounded.Repeat, null, tint = Muted, modifier = Modifier.padding(start = 6.dp).size(13.dp))
            }
        }
        PriorityChip(t.priority)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(28.dp).clip(CircleShape).border(2.dp, if (t.done) Accent else Muted, CircleShape)
                .background(if (t.done) Accent else Color.Transparent).clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) { if (t.done) Icon(Icons.Rounded.Check, null, tint = BG, modifier = Modifier.size(16.dp)) }
    }
}

@Composable
fun Ring(fraction: Float, size: Dp, stroke: Float, content: @Composable () -> Unit = {}) {
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
fun PomdoApp(request: MutableState<String?>) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var screen by rememberSaveable { mutableStateOf("main") }
    var editId by rememberSaveable { mutableStateOf(-1L) }
    BackHandler(screen != "main" || tab != 0) { if (screen != "main") screen = "main" else tab = 0 }
    val startFocus: (Task?) -> Unit = { t ->
        if (!(Focus.active && Focus.taskId == (t?.id ?: -1L))) Focus.start(ctx, t)
        screen = "focus"
    }
    LaunchedEffect(request.value) {
        val r = request.value ?: return@LaunchedEffect
        request.value = null
        if (r == "focus") screen = "focus"
        else if (r.startsWith("start:")) startFocus(Store.find(r.removePrefix("start:").toLongOrNull() ?: -1L))
    }
    when (screen) {
        "create" -> CreateScreen(editId) { screen = "main" }
        "focus" -> FocusScreen { screen = "main" }
        else -> Scaffold(
            containerColor = BG,
            bottomBar = {
                NavigationBar(containerColor = CardC) {
                    listOf(Icons.Rounded.Home to "Home", Icons.Rounded.TaskAlt to "Tasks", Icons.Rounded.BarChart to "Progress", Icons.Rounded.Settings to "Settings")
                        .forEachIndexed { i, p ->
                            NavigationBarItem(
                                selected = tab == i, onClick = { tab = i },
                                icon = { Icon(p.first, null) }, label = { Text(p.second) },
                                colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Card2)
                            )
                        }
                }
            },
            floatingActionButton = {
                if (tab == 1) FloatingActionButton(onClick = { editId = -1L; screen = "create" }, containerColor = Green, contentColor = Color.White, shape = CircleShape) { Icon(Icons.Rounded.Add, "Add task") }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(startFocus) { tab = 1 }
                    1 -> TasksScreen(startFocus) { editId = it; screen = "create" }
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
    val ctx = LocalContext.current
    val now = rememberNow(Focus.active && Focus.running)
    val today = Store.tasks.filter { sameDay(it.start, now) }
    val done = today.count { it.done }
    val frac = if (today.isEmpty()) 0f else done.toFloat() / today.size
    val focusToday = Store.logs.filter { sameDay(it.time, now) }.sumOf { it.minutes }
    val next = Store.tasks.filter { !it.done && it.start >= startOfDay(now) }.sortedBy { it.start }.take(3)
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"
    val notifOk = ctx.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(fmt("EEEE, MMM d", now).uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
        Text("$greet!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Timer, null, tint = Accent, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(6.dp))
            Text("pomdo", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Accent)
        }
        Spacer(Modifier.height(14.dp))
        if (!notifOk) {
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp)).background(Danger.copy(alpha = 0.18f))
                .clickable { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsOff, null, tint = Danger)
                Spacer(Modifier.width(10.dp))
                Text("Notifications are off. Tap to enable reminders.", fontSize = 13.sp)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CardC).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today's progress", color = Muted, fontSize = 13.sp)
                Text("${(frac * 100).toInt()}%", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text("$done of ${today.size} completed", color = Muted, fontSize = 13.sp)
            }
            Ring(frac, 92.dp, 10f)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardC).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("${hm(focusToday)} / ${hm(Store.goal)} daily focus", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { (focusToday / Store.goal.toFloat()).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Accent, trackColor = Card2)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Up next", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("See all", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { seeAll() })
        }
        if (next.isEmpty()) Text("Nothing scheduled. Add a task from the Tasks tab.", color = Muted, modifier = Modifier.padding(vertical = 14.dp))
        next.forEach { TaskRow(it, { Store.toggle(it) }, { startFocus(it) }, {}) }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Green.copy(alpha = 0.35f)).clickable { startFocus(null) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Warn, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (Focus.active) "Focus session running" else "Ready for a focus session?", fontSize = 13.sp)
                Text(if (Focus.active) clock(Focus.leftNow(now)) else clock(Store.defFocus * 60000L), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = BG) }
        }
    }
}

// ---------- Tasks ----------
@Composable
fun TasksScreen(startFocus: (Task?) -> Unit, onEdit: (Long) -> Unit) {
    var filter by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<Task?>(null) }
    var confirmDelete by remember { mutableStateOf<Task?>(null) }
    val now = System.currentTimeMillis()
    val endToday = startOfDay(now) + 86400000L
    val list = Store.tasks.filter {
        when (filter) {
            0 -> if (it.done) sameDay(it.start, now) else it.start < endToday && it.due >= startOfDay(now)
            1 -> !it.done && it.start >= endToday
            2 -> !it.done && it.due < startOfDay(now)
            else -> it.done
        } && (query.isBlank() || it.name.contains(query, true))
    }.sortedBy { it.start }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My tasks", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Icon(if (searching) Icons.Rounded.Close else Icons.Rounded.Search, "Search", Modifier.clickable { searching = !searching; if (!searching) query = "" }.padding(6.dp))
        }
        if (searching) OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(bottom = 8.dp), singleLine = true, placeholder = { Text("Search tasks") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Card2, focusedContainerColor = CardC, unfocusedContainerColor = CardC))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf("Today", "Upcoming", "Overdue", "Completed").forEachIndexed { i, s -> Pill(s, filter == i) { filter = i } }
        }
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) Text("Nothing here yet. Tap + to create a task.", color = Muted, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
            items(list, key = { it.id }) { t -> TaskRow(t, { Store.toggle(t) }, { startFocus(t) }, { menu = t }) }
        }
    }
    menu?.let { t ->
        AlertDialog(
            onDismissRequest = { menu = null }, title = { Text(t.name) },
            text = {
                Column {
                    TextButton({ menu = null; onEdit(t.id) }) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit task") }
                    TextButton({ menu = null; startFocus(t) }) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start focus") }
                    TextButton({ menu = null; confirmDelete = t }) { Icon(Icons.Rounded.Delete, null, tint = Danger); Spacer(Modifier.width(8.dp)); Text("Delete", color = Danger) }
                }
            },
            confirmButton = {}, dismissButton = { TextButton({ menu = null }) { Text("Close") } }
        )
    }
    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, title = { Text("Delete task?") }, text = { Text(t.name) },
            confirmButton = { TextButton({ Store.delete(t); confirmDelete = null }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

// ---------- Create / Edit ----------
@Composable
fun SRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, Modifier.weight(1f), color = Muted)
        Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Icon(Icons.Rounded.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CreateScreen(editId: Long, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val ex = remember { Store.find(editId) }
    var name by rememberSaveable { mutableStateOf(ex?.name ?: "") }
    var desc by rememberSaveable { mutableStateOf(ex?.desc ?: "") }
    var cat by rememberSaveable { mutableStateOf(ex?.category ?: Category.WORK) }
    val base = remember { System.currentTimeMillis() + 3600000L }
    var start by rememberSaveable { mutableStateOf(ex?.start ?: base) }
    var due by rememberSaveable { mutableStateOf(ex?.due ?: (base + 3600000L)) }
    var rem by rememberSaveable { mutableStateOf(ex?.reminderMin ?: 5) }
    var sound by rememberSaveable { mutableStateOf(ex?.sound ?: Store.defSound) }
    var rep by rememberSaveable { mutableStateOf(ex?.repeat ?: Repeat.NONE) }
    var fm by rememberSaveable { mutableStateOf(ex?.focusMin ?: Store.defFocus) }
    var bm by rememberSaveable { mutableStateOf(ex?.breakMin ?: Store.defBreak) }
    var sessions by rememberSaveable { mutableStateOf(ex?.sessions ?: 4) }
    var prio by rememberSaveable { mutableStateOf(ex?.priority ?: 1) }
    val presets = listOf(25 to 5, 50 to 10, 15 to 3, 45 to 15)
    val remOpts = listOf(0, 5, 10, 15, 30)
    val tf = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Card2, focusedContainerColor = CardC, unfocusedContainerColor = CardC)
    val save = {
        if (name.isNotBlank()) {
            val t = Task(ex?.id ?: Store.newId(), name.trim(), desc.trim(), cat, start, maxOf(due, start), rem, sound, fm, bm, sessions, prio, rep,
                ex?.done ?: false, ex?.completedAt ?: 0L, ex?.spawned ?: false)
            if (ex != null) Store.update(t) else Store.addTask(t)
            onClose()
        }
    }
    Column(Modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBack, "Back", Modifier.clickable { onClose() }.padding(8.dp))
            Text(if (ex == null) "Create a task" else "Edit task", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.Check, "Save", Modifier.clickable { save() }.padding(8.dp), tint = Accent)
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
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(CardC)
                                .border(2.dp, if (cat == c) Accent else Color.Transparent, RoundedCornerShape(16.dp))
                                .clickable { cat = c }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CatTile(c, 40.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(c.label, fontSize = 11.sp, color = if (cat == c) Accent else Muted)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Schedule", fontWeight = FontWeight.Bold)
            SRow(Icons.Rounded.Event, "Start", whenLabel(start)) { pickDateTime(ctx, start) { start = it; if (due < it) due = it + 3600000L } }
            SRow(Icons.Rounded.EventAvailable, "Due", whenLabel(due)) { pickDateTime(ctx, due) { due = it } }
            SRow(Icons.Rounded.Notifications, "Reminder", if (rem == 0) "At start" else "$rem min before") { rem = remOpts[(remOpts.indexOf(rem) + 1) % remOpts.size] }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RecordVoiceOver, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Alert style", color = Muted)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                SoundMode.values().forEach { m -> Pill(m.label, sound == m) { sound = m } }
            }
            SRow(Icons.Rounded.Repeat, "Repeat", rep.label) { rep = Repeat.values()[(rep.ordinal + 1) % Repeat.values().size] }
            SRow(Icons.Rounded.Timer, "Pomodoro", "$fm focus · $bm break") {
                val i = presets.indexOf(fm to bm); val n = presets[(i + 1) % presets.size]; fm = n.first; bm = n.second
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Layers, null, tint = Accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sessions", Modifier.weight(1f), color = Muted)
                Icon(Icons.Rounded.Remove, null, Modifier.clickable { if (sessions > 1) sessions-- }.padding(8.dp))
                Text("$sessions", fontWeight = FontWeight.Bold)
                Icon(Icons.Rounded.Add, null, Modifier.clickable { if (sessions < 12) sessions++ }.padding(8.dp))
            }
            Text("Total focus time: ${hm(fm * sessions)}", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Priority", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
                listOf("Low", "Medium", "High").forEachIndexed { i, s -> Pill(s, prio == i) { prio = i } }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { save() }, enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)
        ) { Text("Save task", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    }
}

// ---------- Focus ----------
@Composable
fun FocusScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val now = rememberNow(Focus.active && Focus.running)
    var confirm by remember { mutableStateOf(false) }
    DisposableEffect(Store.keepAwake) { view.keepScreenOn = Store.keepAwake; onDispose { view.keepScreenOn = false } }
    val t = Focus.task()
    val left = Focus.leftNow(now)
    val frac = if (Focus.phaseMs() == 0L) 0f else left / Focus.phaseMs().toFloat()
    Column(Modifier.fillMaxSize().background(BG).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { Icon(Icons.Rounded.ArrowBack, "Back", Modifier.clickable { onClose() }.padding(8.dp)) }
        Text(
            if (Focus.finished || !Focus.active) "SESSION COMPLETE" else if (Focus.phase == 0) "CURRENT TASK · FOCUSING" else "TAKE A BREAK",
            color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (t != null) { CatTile(t.category, 36.dp); Spacer(Modifier.width(10.dp)) }
            Text(t?.name ?: "Free focus", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        Ring(if (Focus.active) frac else 1f, 270.dp, 16f) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (Focus.active) Text(clock(left), fontSize = 58.sp, fontWeight = FontWeight.Bold)
                else Icon(Icons.Rounded.EmojiEvents, null, tint = Warn, modifier = Modifier.size(64.dp))
                Text("Session ${Focus.session.coerceAtMost(Focus.total)} of ${Focus.total}", color = Muted)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TrackChanges, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("${Focus.focusedSec(now) / 60} / ${Focus.focusMin * Focus.total} min focused", color = Muted)
        }
        Spacer(Modifier.weight(1f))
        if (Focus.active) {
            Button(
                onClick = { Focus.toggle(ctx) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BG)
            ) {
                Icon(if (Focus.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (Focus.running) "Pause" else "Resume", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.5.dp, Color.White)
            ) { Icon(Icons.Rounded.Check, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("Finish", color = Color.White, fontSize = 17.sp) }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Card2).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (Focus.phase == 0) Icons.Rounded.FreeBreakfast else Icons.Rounded.Timer, null, tint = Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (Focus.phase == 0) "Next break · ${if (Focus.session % 4 == 0) Focus.longMin else Focus.breakMin} minutes" else "Next focus · ${Focus.focusMin} minutes", fontSize = 13.sp)
            }
        } else {
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = BG)) { Text("Done", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(10.dp))
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Finish session?") },
        text = { Text("Your focused time is saved to history." + if (t != null && !t.done) " Also mark \"${t.name}\" as complete?" else "") },
        confirmButton = {
            if (t != null && !t.done) TextButton({ confirm = false; Focus.stop(ctx, true) }) { Text("Complete task", color = Accent) }
            else TextButton({ confirm = false; Focus.stop(ctx, false) }) { Text("Finish", color = Accent) }
        },
        dismissButton = {
            Row {
                if (t != null && !t.done) TextButton({ confirm = false; Focus.stop(ctx, false) }) { Text("Just stop") }
                TextButton({ confirm = false }) { Text("Cancel") }
            }
        }
    )
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
fun StatCard(modifier: Modifier, icon: ImageVector, big: String, small: String) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(CardC).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Column { Text(big, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(small, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
fun ProgressScreen() {
    var range by rememberSaveable { mutableStateOf(0) }
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
        Text("Your progress", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row { listOf("Week", "Month", "Year").forEachIndexed { i, s -> Pill(s, range == i) { range = i } } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(Modifier.weight(1f), Icons.Rounded.CheckCircle, "$tasksDone", "tasks done")
            StatCard(Modifier.weight(1f), Icons.Rounded.Schedule, hm(mins), "focused")
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardC).padding(14.dp)) {
            Text("Tasks completed", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            BarChart(perTasks, labels)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardC).padding(14.dp)) {
            Text("Focus time (minutes)", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LineChart(perMins, labels)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardC).padding(14.dp)) {
            Text("Category breakdown", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (byCat.isEmpty()) Text("No focus sessions in this period yet.", color = Muted)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    byCat.forEach { (c, m) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(c.icon(), null, tint = Color(c.color), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(c.label, Modifier.weight(1f), fontSize = 13.sp)
                            Text("${m * 100 / mins}%", fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(6.dp))
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
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Green.copy(alpha = 0.35f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Warn, modifier = Modifier.size(32.dp))
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
fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted)
        Icon(Icons.Rounded.Remove, null, Modifier.clickable { onChange((value - step).coerceAtLeast(min)) }.padding(8.dp))
        Text("$value", fontWeight = FontWeight.Bold)
        Icon(Icons.Rounded.Add, null, Modifier.clickable { onChange((value + step).coerceAtMost(max)) }.padding(8.dp))
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Store.toneUri = u?.toString() ?: ""; Store.saveSettings()
        }
    }
    val toneName = remember(Store.toneUri) {
        try {
            if (Store.toneUri.isBlank()) "Default alarm"
            else RingtoneManager.getRingtone(ctx, Uri.parse(Store.toneUri)).getTitle(ctx)
        } catch (e: Exception) { "Custom" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text("Pomodoro", fontWeight = FontWeight.Bold)
        Stepper("Focus (min)", Store.defFocus, 5, 120, 5) { Store.defFocus = it; Store.saveSettings() }
        Stepper("Short break (min)", Store.defBreak, 1, 30, 1) { Store.defBreak = it; Store.saveSettings() }
        Stepper("Long break (min)", Store.defLong, 5, 60, 5) { Store.defLong = it; Store.saveSettings() }
        Stepper("Daily focus goal (min)", Store.goal, 15, 600, 15) { Store.goal = it; Store.saveSettings() }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Keep screen on while focusing", Modifier.weight(1f), color = Muted)
            Switch(Store.keepAwake, { Store.keepAwake = it; Store.saveSettings() })
        }
        Spacer(Modifier.height(12.dp))
        Text("Reminders", fontWeight = FontWeight.Bold)
        SRow(Icons.Rounded.MusicNote, "Default sound", Store.defSound.label) {
            Store.defSound = SoundMode.values()[(Store.defSound.ordinal + 1) % SoundMode.values().size]; Store.saveSettings()
        }
        SRow(Icons.Rounded.RecordVoiceOver, "Apply default to all tasks", "Apply") {
            Store.applySoundToAll()
            android.widget.Toast.makeText(ctx, "Applied to all tasks", android.widget.Toast.LENGTH_SHORT).show()
        }
        SRow(Icons.Rounded.PlayArrow, "Test voice + notification", "Play") {
            ReminderReceiver.fire(ctx, Task(-7L, "Test reminder", "", Category.OTHER, System.currentTimeMillis() + 300000L,
                System.currentTimeMillis() + 600000L, 5, Store.defSound, 25, 5, 1, 1, Repeat.NONE)) { }
        }
        SRow(Icons.Rounded.Notifications, "Ringtone", toneName) {
            picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true))
        }
        Spacer(Modifier.height(12.dp))
        Text("Reliability", fontWeight = FontWeight.Bold)
        SRow(Icons.Rounded.BatteryChargingFull, "Battery optimisation", "Open") {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        SRow(Icons.Rounded.Notifications, "Notification settings", "Open") {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
        if (Build.VERSION.SDK_INT in 31..32) SRow(Icons.Rounded.Alarm, "Exact alarms", "Open") {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:" + ctx.packageName)))
        }
        Text("Tip: on Xiaomi, Oppo, Vivo, Realme and Samsung phones, also allow Autostart and set Battery to Unrestricted so timers and reminders are never delayed.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(20.dp))
        Text("Pomdo 1.1 · Plan · Focus · Achieve", color = Muted, fontSize = 12.sp)
    }
}
