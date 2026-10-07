package com.purewrite.writer

import android.app.Application
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.purewrite.writer.data.db.AppDatabase
import com.purewrite.writer.data.repository.WritingRepository
import com.purewrite.writer.util.PrefsManager

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        WritingRepository(database.bookDao(), database.chapterDao())
    }
    val prefs by lazy { PrefsManager(this) }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        applyNightMode()
    }

    /**
     * 应用夜间模式设置
     * 0=跟随系统，1=始终日间，2=始终夜间
     */
    fun applyNightMode() {
        val mode = when (prefs.nightMode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
