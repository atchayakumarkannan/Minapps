package com.minimal.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.math.abs

class AppInfo(val label: String, val pkg: String)

class MainActivity : Activity() {

    private val bg = Color.BLACK
    private val fg = Color.parseColor("#EDEDED")
    private val dim = Color.parseColor("#777777")

    private lateinit var prefs: Prefs
    private var apps: List<AppInfo> = emptyList()

    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var tasksBox: LinearLayout
    private lateinit var favBox: LinearLayout
    private lateinit var focusLabel: TextView
    private lateinit var drawer: LinearLayout
    private lateinit var searchBox: EditText
    private lateinit var drawerList: LinearLayout
    private lateinit var settings: ScrollView
    private lateinit var settingsBody: LinearLayout
    private lateinit var gestures: GestureDetector

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        buildUi()
        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null || overlayOpen()) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                val far = dp(110)
                if (abs(dy) > abs(dx) && abs(dy) > far) {
                    if (dy < 0) openDrawer() else expandShade()
                    return true
                }
                if (abs(dx) > abs(dy) && abs(dx) > far) {
                    launch(if (dx < 0) prefs.swipeLeft else prefs.swipeRight)
                    return true
                }
                return false
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home button pressed: go back to the clock.
        closeOverlays()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlayOpen()) closeOverlays()
        // On a launcher home screen, back does nothing.
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---------------------------------------------------------------- UI building

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(s: String, size: Float, color: Int = fg, light: Boolean = false) =
        TextView(this).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (light) typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(bg) }

        // ----- home
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(72), dp(32), dp(48))
            setOnLongClickListener { openSettings(); true }
        }
        val clock = TextClock(this).apply {
            format24Hour = "HH:mm"
            format12Hour = "HH:mm"
            textSize = 76f
            setTextColor(fg)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val date = TextClock(this).apply {
            format24Hour = "EEEE, d MMMM"
            format12Hour = "EEEE, d MMMM"
            textSize = 16f
            setTextColor(dim)
        }
        focusLabel = label("", 14f, dim).apply {
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { toggleFocus() }
        }
        tasksBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        favBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val spacer = View(this)
        home.addView(clock)
        home.addView(date)
        home.addView(focusLabel)
        home.addView(tasksBox)
        home.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        home.addView(favBox)
        root.addView(home, matchParent())

        // ----- drawer
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(32), dp(56), dp(32), dp(16))
            visibility = View.GONE
        }
        searchBox = EditText(this).apply {
            hint = "search"
            setHintTextColor(dim)
            setTextColor(fg)
            textSize = 22f
            background = null
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) = renderDrawer(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            setOnEditorActionListener { _, _, _ ->
                matches(text.toString()).firstOrNull()?.let { closeOverlays(); launch(it.pkg) }
                true
            }
        }
        drawerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val drawerScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(drawerList)
        }
        drawer.addView(searchBox)
        drawer.addView(drawerScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(drawer, matchParent())

        // ----- settings
        settingsBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(56), dp(32), dp(48))
        }
        settings = ScrollView(this).apply {
            setBackgroundColor(bg)
            visibility = View.GONE
            addView(settingsBody)
        }
        root.addView(settings, matchParent())

        setContentView(root)
    }

    private fun matchParent() = FrameLayout.LayoutParams(-1, -1)

    // ---------------------------------------------------------------- refresh / render

    private fun refresh() {
        loadApps()
        renderFocus()
        renderTasks()
        renderFavorites()
        if (drawer.visibility == View.VISIBLE) renderDrawer(searchBox.text.toString())
        if (settings.visibility == View.VISIBLE) renderSettings()
    }

    private fun loadApps() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = packageManager.queryIntentActivities(i, 0)
            .map { AppInfo(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun nameOf(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg

    private fun renderFocus() {
        focusLabel.text = if (focusActive()) "●  focus on" else "○  focus off"
        focusLabel.setTextColor(if (focusActive()) fg else dim)
    }

    private fun renderTasks() {
        tasksBox.removeAllViews()
        val tasks = prefs.tasks
        tasks.take(4).forEachIndexed { idx, t ->
            tasksBox.addView(label("–  $t", 16f, fg).apply {
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener {
                    // Tap = done.
                    prefs.tasks = prefs.tasks.toMutableList().also { l -> l.removeAt(idx) }
                    renderTasks()
                }
            })
        }
        if (tasks.size > 4) tasksBox.addView(label("+${tasks.size - 4} more", 13f, dim))
        tasksBox.addView(label("+ note", 14f, dim).apply {
            setPadding(0, dp(10), 0, dp(10))
            setOnClickListener { addTaskDialog() }
        })
    }

    private fun renderFavorites() {
        favBox.removeAllViews()
        val favs = prefs.favorites.filter { p -> apps.any { it.pkg == p } }
        if (favs.isEmpty()) {
            favBox.addView(label("long-press anywhere to set up", 14f, dim))
            return
        }
        favs.forEach { p ->
            favBox.addView(label(nameOf(p), 26f, if (blockedByFocus(p)) dim else fg, light = true).apply {
                setPadding(0, dp(9), 0, dp(9))
                setOnClickListener { launch(p) }
                setOnLongClickListener { apps.firstOrNull { it.pkg == p }?.let { a -> appMenu(a) }; true }
            })
        }
    }

    private fun matches(q: String): List<AppInfo> {
        val s = q.trim().lowercase()
        return if (s.isEmpty()) apps else apps.filter { it.label.lowercase().contains(s) }
            .sortedBy { if (it.label.lowercase().startsWith(s)) 0 else 1 }
    }

    private fun renderDrawer(q: String) {
        drawerList.removeAllViews()
        val limits = prefs.limits
        val used = if (limits.isNotEmpty() && hasUsageAccess()) usageMinutes() else emptyMap()
        matches(q).forEach { a ->
            val blocked = blockedByFocus(a.pkg)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
                setOnClickListener { closeOverlays(); launch(a.pkg) }
                setOnLongClickListener { appMenu(a); true }
            }
            row.addView(label(a.label, 22f, if (blocked) dim else fg, light = true))
            val lim = limits[a.pkg]
            val tag = when {
                blocked -> "focus"
                lim != null && hasUsageAccess() -> "${used[a.pkg] ?: 0}/${lim}m"
                lim != null -> "${lim}m limit"
                else -> ""
            }
            if (tag.isNotEmpty()) row.addView(label("   $tag", 12f, dim))
            drawerList.addView(row)
        }
    }

    // ---------------------------------------------------------------- overlays

    private fun overlayOpen() = drawer.visibility == View.VISIBLE || settings.visibility == View.VISIBLE

    private fun openDrawer() {
        drawer.visibility = View.VISIBLE
        searchBox.setText("")
        renderDrawer("")
        searchBox.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(searchBox, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun openSettings() {
        renderSettings()
        settings.scrollTo(0, 0)
        settings.visibility = View.VISIBLE
    }

    private fun closeOverlays() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(searchBox.windowToken, 0)
        drawer.visibility = View.GONE
        settings.visibility = View.GONE
        refreshHomeOnly()
    }

    private fun refreshHomeOnly() {
        renderFocus(); renderTasks(); renderFavorites()
    }

    private fun expandShade() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Throwable) {
            // Not allowed on this ROM; swipe down from the very top edge instead.
        }
    }

    // ---------------------------------------------------------------- settings screen

    private fun row(title: String, value: String, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            addView(label(title, 18f, fg, light = true))
            if (value.isNotEmpty()) addView(label(value, 13f, dim))
            setOnClickListener { onClick() }
        }

    private fun heading(s: String) = label(s.uppercase(), 12f, dim).apply { setPadding(0, dp(28), 0, dp(4)) }

    private fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    private fun renderSettings() {
        val b = settingsBody
        b.removeAllViews()
        b.addView(label("Settings", 30f, fg, light = true))

        b.addView(heading("Home"))
        b.addView(row("Favorites", "${prefs.favorites.size} of 6 apps on home") {
            pickMany("Favorites (max 6)", prefs.favorites.toSet()) { sel ->
                val kept = prefs.favorites.filter { it in sel }
                val added = apps.map { it.pkg }.filter { it in sel && it !in kept }
                prefs.favorites = (kept + added).take(6)
                refresh()
            }
        })
        b.addView(row("Swipe left opens", prefs.swipeLeft.ifEmpty { "nothing" }.let { if (it == "nothing") it else nameOf(it) }) {
            pickOne("Swipe left opens") { prefs.swipeLeft = it; renderSettings() }
        })
        b.addView(row("Swipe right opens", prefs.swipeRight.ifEmpty { "nothing" }.let { if (it == "nothing") it else nameOf(it) }) {
            pickOne("Swipe right opens") { prefs.swipeRight = it; renderSettings() }
        })

        b.addView(heading("Focus mode"))
        b.addView(row("Distracting apps", "${prefs.distracting.size} apps paused during focus") {
            pickMany("Distracting apps", prefs.distracting) { prefs.distracting = it; refresh() }
        })
        b.addView(Switch(this).apply {
            text = "Turn on automatically on a schedule"
            textSize = 16f
            setTextColor(fg)
            isChecked = prefs.focusAuto
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, c -> prefs.focusAuto = c; renderFocus() }
        })
        b.addView(row("Starts at", fmt(prefs.focusStart)) {
            TimePickerDialog(this, { _, h, m -> prefs.focusStart = h * 60 + m; renderSettings(); renderFocus() },
                prefs.focusStart / 60, prefs.focusStart % 60, true).show()
        })
        b.addView(row("Ends at", fmt(prefs.focusEnd)) {
            TimePickerDialog(this, { _, h, m -> prefs.focusEnd = h * 60 + m; renderSettings(); renderFocus() },
                prefs.focusEnd / 60, prefs.focusEnd % 60, true).show()
        })

        b.addView(heading("Daily app limits"))
        b.addView(row(
            "Usage access",
            if (hasUsageAccess()) "granted" else "needed so limits can count your time. Tap to allow."
        ) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        val limits = prefs.limits
        b.addView(row("Add a limit", "${limits.size} apps limited. Long-press any app in the drawer too.") {
            pickOne("Limit which app?") { p -> apps.firstOrNull { it.pkg == p }?.let { limitDialog(it) } }
        })
        limits.forEach { (p, m) ->
            b.addView(row("   ${nameOf(p)}", "   $m min / day. Tap to change") {
                apps.firstOrNull { it.pkg == p }?.let { limitDialog(it) }
            })
        }

        b.addView(heading("System"))
        b.addView(row("Make Minimal my home app", "opens Android's default home app setting") {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        })
        b.addView(row("Close", "") { closeOverlays() })
    }

    // ---------------------------------------------------------------- dialogs

    private fun pickMany(title: String, selected: Set<String>, onDone: (Set<String>) -> Unit) {
        val names = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].pkg in selected }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(names, checked) { _, i, c -> checked[i] = c }
            .setPositiveButton("Done") { _, _ ->
                onDone(apps.filterIndexed { i, _ -> checked[i] }.map { it.pkg }.toSet())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pickOne(title: String, onPick: (String) -> Unit) {
        val names = (listOf("None") + apps.map { it.label }).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names) { _, i -> onPick(if (i == 0) "" else apps[i - 1].pkg) }
            .show()
    }

    private fun appMenu(a: AppInfo) {
        val fav = a.pkg in prefs.favorites
        val dis = a.pkg in prefs.distracting
        val lim = prefs.limits[a.pkg]
        val items = arrayOf(
            if (fav) "Remove from favorites" else "Add to favorites",
            if (dis) "Not distracting" else "Mark as distracting",
            if (lim != null) "Daily limit: $lim min (change)" else "Set daily limit",
            "App info",
            "Uninstall"
        )
        AlertDialog.Builder(this).setTitle(a.label).setItems(items) { _, w ->
            when (w) {
                0 -> {
                    if (fav) prefs.favorites = prefs.favorites - a.pkg
                    else if (prefs.favorites.size >= 6) toast("Home is full (6). Remove one first.")
                    else prefs.favorites = prefs.favorites + a.pkg
                    refresh()
                }
                1 -> {
                    prefs.distracting = if (dis) prefs.distracting - a.pkg else prefs.distracting + a.pkg
                    refresh()
                }
                2 -> limitDialog(a)
                3 -> startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))
                )
                4 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                else -> {}
            }
        }.show()
    }

    private fun limitDialog(a: AppInfo) {
        val mins = intArrayOf(15, 30, 45, 60, 90, 120)
        val items = (mins.map { "$it minutes a day" } + "No limit").toTypedArray()
        AlertDialog.Builder(this).setTitle("${a.label}: daily limit").setItems(items) { _, i ->
            val l = prefs.limits.toMutableMap()
            if (i < mins.size) l[a.pkg] = mins[i] else l.remove(a.pkg)
            prefs.limits = l
            if (i < mins.size && !hasUsageAccess()) {
                toast("Allow usage access so Minimal can count your time")
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            refresh()
        }.show()
    }

    private fun addTaskDialog() {
        val input = EditText(this).apply {
            hint = "quick note"
            setSingleLine()
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        AlertDialog.Builder(this)
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val t = input.text.toString().replace("\n", " ").trim()
                if (t.isNotEmpty()) {
                    prefs.tasks = prefs.tasks + t
                    renderTasks()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
        input.requestFocus()
    }

    private fun confirm(title: String, msg: String, onOpen: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(msg)
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open anyway") { _, _ -> onOpen() }
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- focus + limits

    private fun inScheduledWindow(): Boolean {
        val c = Calendar.getInstance()
        val now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val s = prefs.focusStart
        val e = prefs.focusEnd
        return when {
            s == e -> false
            s < e -> now in s until e
            else -> now >= s || now < e // overnight window
        }
    }

    private fun focusActive() = prefs.focusManual || (prefs.focusAuto && inScheduledWindow())

    private fun blockedByFocus(pkg: String) = focusActive() && pkg in prefs.distracting

    private fun toggleFocus() {
        if (!prefs.focusManual && prefs.focusAuto && inScheduledWindow()) {
            toast("Scheduled focus is on until ${fmt(prefs.focusEnd)}")
            return
        }
        prefs.focusManual = !prefs.focusManual
        refreshHomeOnly()
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Minutes each app has been in the foreground since midnight. */
    private fun usageMinutes(): Map<String, Int> {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return usm.queryAndAggregateUsageStats(cal.timeInMillis, System.currentTimeMillis())
            .mapValues { (it.value.totalTimeInForeground / 60000).toInt() }
    }

    // ---------------------------------------------------------------- launching

    private fun launch(pkg: String, bypass: Boolean = false) {
        if (pkg.isEmpty()) return
        if (!bypass) {
            if (blockedByFocus(pkg)) {
                confirm("Focus mode is on", "Open ${nameOf(pkg)} anyway?") { launch(pkg, true) }
                return
            }
            val lim = prefs.limits[pkg]
            if (lim != null && hasUsageAccess()) {
                val used = usageMinutes()[pkg] ?: 0
                if (used >= lim) {
                    confirm("Daily limit reached", "You've spent $used of $lim min on ${nameOf(pkg)} today.") {
                        launch(pkg, true)
                    }
                    return
                }
            }
        }
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
    }
}
