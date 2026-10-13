package com.minimal.launcher

import android.content.Context

/** All launcher settings live here (SharedPreferences, nothing leaves the phone). */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("minimal_launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString("fav", "")!!.split(",").filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("fav", v.joinToString(",")).apply()

    var swipeLeft: String
        get() = sp.getString("swipeLeft", "") ?: ""
        set(v) = sp.edit().putString("swipeLeft", v).apply()

    var swipeRight: String
        get() = sp.getString("swipeRight", "") ?: ""
        set(v) = sp.edit().putString("swipeRight", v).apply()

    /** Apps hidden/blocked while focus mode is on. */
    var distracting: Set<String>
        get() = sp.getStringSet("distracting", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("distracting", v).apply()

    var focusManual: Boolean
        get() = sp.getBoolean("focusManual", false)
        set(v) = sp.edit().putBoolean("focusManual", v).apply()

    var focusAuto: Boolean
        get() = sp.getBoolean("focusAuto", false)
        set(v) = sp.edit().putBoolean("focusAuto", v).apply()

    /** Minutes after midnight. Defaults: 09:00 to 17:00. */
    var focusStart: Int
        get() = sp.getInt("focusStart", 9 * 60)
        set(v) = sp.edit().putInt("focusStart", v).apply()

    var focusEnd: Int
        get() = sp.getInt("focusEnd", 17 * 60)
        set(v) = sp.edit().putInt("focusEnd", v).apply()

    /** package -> daily limit in minutes. Stored as "pkg=min;pkg=min". */
    var limits: Map<String, Int>
        get() = (sp.getString("limits", "") ?: "").split(";")
            .filter { it.contains("=") }
            .associate { it.substringBefore("=") to (it.substringAfter("=").toIntOrNull() ?: 0) }
            .filterValues { it > 0 }
        set(v) = sp.edit().putString("limits", v.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()

    /** Quick to-dos, one per line (newlines inside a task are stripped). */
    var tasks: List<String>
        get() = (sp.getString("tasks", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("tasks", v.joinToString("\n")).apply()
}
