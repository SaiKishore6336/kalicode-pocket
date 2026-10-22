package com.saikishore.kalicodepocket.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var fontSize: Int
        get() = prefs.getInt("font_size", 14).coerceIn(10, 28)
        set(value) = prefs.edit().putInt("font_size", value.coerceIn(10, 28)).apply()

    var wordWrap: Boolean
        get() = prefs.getBoolean("word_wrap", false)
        set(value) = prefs.edit().putBoolean("word_wrap", value).apply()

    var lineNumbers: Boolean
        get() = prefs.getBoolean("line_numbers", true)
        set(value) = prefs.edit().putBoolean("line_numbers", value).apply()

    var autoSave: Boolean
        get() = prefs.getBoolean("auto_save", true)
        set(value) = prefs.edit().putBoolean("auto_save", value).apply()

    var tabSize: Int
        get() = prefs.getInt("tab_size", 4).coerceIn(2, 8)
        set(value) = prefs.edit().putInt("tab_size", value.coerceIn(2, 8)).apply()

    /** "kali" or "paper". */
    var editorTheme: String
        get() = prefs.getString("editor_theme", "kali") ?: "kali"
        set(value) = prefs.edit().putString("editor_theme", value).apply()
}
