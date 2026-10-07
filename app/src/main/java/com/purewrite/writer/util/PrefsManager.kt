package com.purewrite.writer.util

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("purewriter_prefs", Context.MODE_PRIVATE)

    var fontSize: Int
        get() = prefs.getInt("font_size", 18)
        set(value) = prefs.edit().putInt("font_size", value).apply()

    var lineSpacing: Float
        get() = prefs.getFloat("line_spacing", 1.6f)
        set(value) = prefs.edit().putFloat("line_spacing", value).apply()

    var autoSave: Boolean
        get() = prefs.getBoolean("auto_save", true)
        set(value) = prefs.edit().putBoolean("auto_save", value).apply()

    var punctuationConvert: Boolean
        get() = prefs.getBoolean("punctuation_convert", true)
        set(value) = prefs.edit().putBoolean("punctuation_convert", value).apply()

    var fullscreenEditor: Boolean
        get() = prefs.getBoolean("fullscreen_editor", false)
        set(value) = prefs.edit().putBoolean("fullscreen_editor", value).apply()

    var dailyWordGoal: Int
        get() = prefs.getInt("daily_word_goal", 2000)
        set(value) = prefs.edit().putInt("daily_word_goal", value).apply()

    var themeColor: String
        get() = prefs.getString("theme_color", "#1A73E8") ?: "#1A73E8"
        set(value) = prefs.edit().putString("theme_color", value).apply()

    var todayWords: Int
        get() {
            val today = TimeUtils.formatDate(System.currentTimeMillis())
            val savedDate = prefs.getString("today_words_date", "") ?: ""
            return if (savedDate == today) {
                prefs.getInt("today_words_count", 0)
            } else {
                0
            }
        }
        set(value) {
            val today = TimeUtils.formatDate(System.currentTimeMillis())
            prefs.edit()
                .putString("today_words_date", today)
                .putInt("today_words_count", value)
                .apply()
        }
}
