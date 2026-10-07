package com.purewrite.writer.data.db

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移集合
 *
 * 设计原则：
 * - 所有迁移均为"增量式"，绝不 DROP 已有表，防止用户数据丢失
 * - 对列操作采用"存在性检查"，兼容未知的历史版本结构
 * - 新增表使用 CREATE TABLE IF NOT EXISTS
 */
object DatabaseMigrations {

    /**
     * v1 → v2 迁移
     * 增量补充 v2 新增的列（如软删除 deletedAt 字段等）
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureColumn(db, "books", "deletedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "volumes", "deletedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "chapters", "deletedAt", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * v2 → v3 迁移
     * 增量补充 v3 新增的列（如 coverColor、lastOpenedAt 等）
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureColumn(db, "books", "coverColor", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "books", "lastOpenedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "volumes", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "chapters", "wordCount", "INTEGER NOT NULL DEFAULT 0")
        }
    }

    /**
     * 检查表中是否存在某列，不存在则添加
     * 兼容 SQLite 不支持 ADD COLUMN IF NOT EXISTS 的限制
     */
    private fun ensureColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        definition: String
    ) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    /**
     * 通过 PRAGMA table_info 检查列是否存在
     */
    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        var cursor: Cursor? = null
        return try {
            cursor = db.query("PRAGMA table_info($table)")
            val nameIndex = cursor.getColumnIndex("name")
            if (nameIndex < 0) return false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex).equals(column, ignoreCase = true)) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            cursor?.close()
        }
    }
}
