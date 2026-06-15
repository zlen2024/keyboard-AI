package com.keyboardai.app.ai.memory

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A single thing the user wants the AI to know, e.g. "Email" → "me@example.com". */
data class InfoEntry(val key: String, val value: String)

/**
 * The user's free-form personal knowledge base: an ordered list of key/value
 * pairs the AI can draw on (to autofill forms, write as them, etc.). There is
 * no fixed schema — the user adds whatever they want ("Name", "Phone",
 * "Student ID", "Allergies", "Favourite quote"…). Stored on-device as JSON.
 */
class InfoStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "user_info.json")

    fun load(): List<InfoEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText())
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val key = o.optString("key").trim()
                val value = o.optString("value").trim()
                if (key.isNotEmpty() || value.isNotEmpty()) add(InfoEntry(key, value))
            }
        }
    }.getOrDefault(emptyList())

    fun save(entries: List<InfoEntry>) {
        runCatching {
            val array = JSONArray()
            entries.forEach { entry ->
                if (entry.key.isNotBlank() || entry.value.isNotBlank()) {
                    array.put(
                        JSONObject()
                            .put("key", entry.key.trim())
                            .put("value", entry.value.trim())
                    )
                }
            }
            file.writeText(array.toString())
        }
    }
}
