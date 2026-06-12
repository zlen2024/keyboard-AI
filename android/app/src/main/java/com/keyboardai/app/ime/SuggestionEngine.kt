package com.keyboardai.app.ime

import android.content.Context

/**
 * Word completion from a frequency-ordered dictionary plus words the user has
 * typed. Lightweight on purpose: the dictionary is ~10k words, so linear
 * prefix scans are well under a millisecond.
 */
class SuggestionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("user_dictionary", Context.MODE_PRIVATE)

    @Volatile
    private var dictionary: List<String> = emptyList()

    /** word -> use count, most recently learned last. */
    private val userWords = LinkedHashMap<String, Int>()

    init {
        synchronized(userWords) {
            for (entry in prefs.getStringSet(PREF_WORDS, emptySet()).orEmpty()) {
                val sep = entry.lastIndexOf(':')
                if (sep > 0) {
                    val count = entry.substring(sep + 1).toIntOrNull() ?: continue
                    userWords[entry.substring(0, sep)] = count
                }
            }
        }
        Thread {
            dictionary = appContext.assets.open("dictionary.txt")
                .bufferedReader()
                .readLines()
                .filter { it.length >= 2 }
        }.start()
    }

    fun suggest(prefix: String, max: Int): List<String> {
        if (prefix.length < MIN_PREFIX || !prefix.all { it.isLetter() || it == '\'' }) {
            return emptyList()
        }
        val lower = prefix.lowercase()
        val results = LinkedHashSet<String>()

        synchronized(userWords) {
            userWords.entries
                .filter { it.key.startsWith(lower) && it.key != lower }
                .sortedByDescending { it.value }
                .take(max)
                .forEach { results.add(it.key) }
        }

        for (word in dictionary) {
            if (results.size >= max) break
            if (word.startsWith(lower) && word != lower) results.add(word)
        }

        // Match the capitalization the user is typing with.
        val capitalized = prefix.first().isUpperCase()
        return results.take(max).map { if (capitalized) it.replaceFirstChar(Char::uppercase) else it }
    }

    /** Remember words the user finishes typing so they rank first next time. */
    fun learn(word: String) {
        val lower = word.lowercase()
        if (lower.length < 3 || !lower.all { it.isLetter() || it == '\'' }) return

        synchronized(userWords) {
            userWords[lower] = (userWords[lower] ?: 0) + 1
            while (userWords.size > MAX_USER_WORDS) {
                userWords.remove(userWords.keys.first())
            }
            prefs.edit()
                .putStringSet(PREF_WORDS, userWords.map { "${it.key}:${it.value}" }.toSet())
                .apply()
        }
    }

    private companion object {
        const val PREF_WORDS = "words"
        const val MIN_PREFIX = 2
        const val MAX_USER_WORDS = 500
    }
}
