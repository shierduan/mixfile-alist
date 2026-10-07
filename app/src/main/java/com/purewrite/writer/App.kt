package com.purewrite.writer

import android.app.Application
import com.purewrite.writer.data.db.AppDatabase
import com.purewrite.writer.data.repository.WritingRepository

class App : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        WritingRepository(database.bookDao(), database.chapterDao())
    }

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
