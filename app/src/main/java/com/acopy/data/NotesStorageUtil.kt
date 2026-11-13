package com.acopy.data

import android.content.Context
import com.acopy.FloatingControlService.NoteItem
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object NotesStorageUtil {

    const val PREFS_NAME = "pintasan_floating_prefs"
    const val KEY_NOTES_JSON = "saved_notes_json"

    fun loadNotes(context: Context): List<NoteItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_NOTES_JSON, "") ?: ""
        if (raw.isBlank()) return emptyList()

        val list = mutableListOf<NoteItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", UUID.randomUUID().toString())
                val title = obj.optString("title", "")
                val content = obj.optString("content", "")
                val isDeleted = obj.optBoolean("isDeleted", false)
                val deletedAt = obj.optLong("deletedAt", 0L)
                if (content.isNotBlank() || title.isNotBlank()) {
                    list.add(NoteItem(id, title, content, isDeleted, deletedAt))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveNotes(context: Context, notes: List<NoteItem>) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (note in notes) {
            val obj = JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("content", note.content)
                put("isDeleted", note.isDeleted)
                put("deletedAt", note.deletedAt)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_NOTES_JSON, arr.toString()).apply()
    }

    fun restoreNote(context: Context, noteId: String): List<NoteItem> {
        val current = loadNotes(context).toMutableList()
        val idx = current.indexOfFirst { it.id == noteId }
        if (idx >= 0) {
            val n = current[idx]
            current[idx] = n.copy(isDeleted = false, deletedAt = 0L)
            saveNotes(context, current)
        }
        return current
    }

    fun permanentlyDeleteNote(context: Context, noteId: String): List<NoteItem> {
        val current = loadNotes(context).toMutableList()
        current.removeAll { it.id == noteId }
        saveNotes(context, current)
        return current
    }
}
