package com.keyboardai.app.ai.memory

import android.content.Context
import com.keyboardai.app.ai.ChatTurn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Three-tier persistent memory for the assistant:
 *
 *  - **session**: the live conversation (in-memory, reset when the keyboard
 *    leaves a field for a while or the app switches).
 *  - **daily**: short bullet facts gathered today, kept per calendar day.
 *  - **global**: durable facts/preferences that persist indefinitely (capped).
 *
 * Daily and global tiers are small JSON files in private storage. Summarization
 * (session → daily → global) is intentionally simple here; a model-based
 * consolidation pass can be layered on later without changing the storage.
 */
class MemoryStore(context: Context) {

    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "memory").apply { mkdirs() }
    private val globalFile = File(dir, "global.json")

    // ---- session ----

    private val session = ArrayDeque<ChatTurn>()

    fun sessionTurns(): List<ChatTurn> = session.toList()

    fun recordTurn(turn: ChatTurn) {
        session.addLast(turn)
        while (session.size > MAX_SESSION_TURNS) session.removeFirst()
    }

    fun clearSession() = session.clear()

    // ---- daily ----

    fun todayFacts(): List<String> = readFacts(dailyFile(today()))

    fun addDailyFact(fact: String) {
        if (fact.isBlank()) return
        val file = dailyFile(today())
        val facts = readFacts(file).toMutableList()
        facts.add(fact.trim())
        writeFacts(file, facts.takeLast(MAX_DAILY_FACTS))
    }

    // ---- global ----

    fun globalFacts(): List<String> = readFacts(globalFile)

    fun addGlobalFact(fact: String) {
        if (fact.isBlank()) return
        val facts = readFacts(globalFile).toMutableList()
        if (facts.none { it.equals(fact.trim(), ignoreCase = true) }) {
            facts.add(fact.trim())
            writeFacts(globalFile, facts.takeLast(MAX_GLOBAL_FACTS))
        }
    }

    fun setGlobalFacts(facts: List<String>) =
        writeFacts(globalFile, facts.map { it.trim() }.filter { it.isNotEmpty() }.takeLast(MAX_GLOBAL_FACTS))

    // ---- storage helpers ----

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun dailyFile(date: String) = File(dir, "daily-$date.json")

    private fun readFacts(file: File): List<String> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONObject(file.readText()).optJSONArray("facts") ?: return emptyList()
        buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    }.getOrDefault(emptyList())

    private fun writeFacts(file: File, facts: List<String>) {
        runCatching {
            val array = JSONArray().apply { facts.forEach { put(it) } }
            file.writeText(JSONObject().put("facts", array).toString())
        }
    }

    private companion object {
        const val MAX_SESSION_TURNS = 12
        const val MAX_DAILY_FACTS = 40
        const val MAX_GLOBAL_FACTS = 50
    }
}
