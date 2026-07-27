package com.claudecode.mobile.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ServerRepository(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "claude_code.db"
        private const val DB_VERSION = 1
        private const val TABLE = "servers"
        private const val COL_ID = "id"
        private const val COL_NAME = "name"
        private const val COL_URL = "url"
        private const val COL_DEFAULT = "is_default"
        private const val COL_TRUST = "trust_all_certs"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_URL TEXT NOT NULL,
                $COL_DEFAULT INTEGER DEFAULT 0,
                $COL_TRUST INTEGER DEFAULT 0
            )
        """.trimIndent())
        // Insert default CloudCLI Cloud server
        db.execSQL("""
            INSERT INTO $TABLE ($COL_NAME, $COL_URL, $COL_DEFAULT, $COL_TRUST)
            VALUES ('CloudCLI Cloud', 'https://cloudcli.ai', 1, 0)
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE")
        onCreate(db)
    }

    fun getAll(): List<ServerConfig> {
        val list = mutableListOf<ServerConfig>()
        val cursor = readableDatabase.query(
            TABLE, null, null, null, null, null, "$COL_DEFAULT DESC, $COL_NAME ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(ServerConfig(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COL_NAME)),
                    url = it.getString(it.getColumnIndexOrThrow(COL_URL)),
                    isDefault = it.getInt(it.getColumnIndexOrThrow(COL_DEFAULT)) == 1,
                    trustAllCerts = it.getInt(it.getColumnIndexOrThrow(COL_TRUST)) == 1
                ))
            }
        }
        return list
    }

    fun getDefault(): ServerConfig? {
        val cursor = readableDatabase.query(
            TABLE, null, "$COL_DEFAULT = 1", null, null, null, null, "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                return ServerConfig(
                    id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(COL_NAME)),
                    url = it.getString(it.getColumnIndexOrThrow(COL_URL)),
                    isDefault = true,
                    trustAllCerts = it.getInt(it.getColumnIndexOrThrow(COL_TRUST)) == 1
                )
            }
        }
        return getAll().firstOrNull()
    }

    fun add(name: String, url: String, trustAllCerts: Boolean): Long {
        val db = writableDatabase
        // Clear previous default if setting new default
        val values = ContentValues().apply {
            put(COL_NAME, name)
            put(COL_URL, url)
            put(COL_DEFAULT, 0)
            put(COL_TRUST, if (trustAllCerts) 1 else 0)
        }
        return db.insert(TABLE, null, values)
    }

    fun update(id: Long, name: String, url: String, trustAllCerts: Boolean) {
        val values = ContentValues().apply {
            put(COL_NAME, name)
            put(COL_URL, url)
            put(COL_TRUST, if (trustAllCerts) 1 else 0)
        }
        writableDatabase.update(TABLE, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun setDefault(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val clearValues = ContentValues().apply { put(COL_DEFAULT, 0) }
            db.update(TABLE, clearValues, null, null)
            val setValues = ContentValues().apply { put(COL_DEFAULT, 1) }
            db.update(TABLE, setValues, "$COL_ID = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
