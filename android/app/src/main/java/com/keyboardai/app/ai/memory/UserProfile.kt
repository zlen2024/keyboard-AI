package com.keyboardai.app.ai.memory

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * What the assistant knows about its owner — the "harness" the user registers
 * once in the setup app. Plain data; persisted as JSON by [ProfileStore].
 */
data class UserProfile(
    val name: String = "",
    /** Contact + identity fields used to autofill forms (Google Forms, sign-ups…). */
    val email: String = "",
    val phone: String = "",
    val address: String = "",
    val organization: String = "",
    val occupation: String = "",
    val tone: String = "",
    val languages: String = "",
    /** Free-text facts the user wants the AI to always keep in mind. */
    val about: String = "",
) {
    val isEmpty: Boolean
        get() = name.isBlank() && email.isBlank() && phone.isBlank() &&
            address.isBlank() && organization.isBlank() && occupation.isBlank() &&
            tone.isBlank() && languages.isBlank() && about.isBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("email", email)
        .put("phone", phone)
        .put("address", address)
        .put("organization", organization)
        .put("occupation", occupation)
        .put("tone", tone)
        .put("languages", languages)
        .put("about", about)

    companion object {
        fun fromJson(json: JSONObject) = UserProfile(
            name = json.optString("name"),
            email = json.optString("email"),
            phone = json.optString("phone"),
            address = json.optString("address"),
            organization = json.optString("organization"),
            occupation = json.optString("occupation"),
            tone = json.optString("tone"),
            languages = json.optString("languages"),
            about = json.optString("about"),
        )
    }
}

/** Reads/writes the single [UserProfile] in the app's private storage. */
class ProfileStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "profile.json")

    fun load(): UserProfile = runCatching {
        if (!file.exists()) UserProfile()
        else UserProfile.fromJson(JSONObject(file.readText()))
    }.getOrDefault(UserProfile())

    fun save(profile: UserProfile) {
        runCatching { file.writeText(profile.toJson().toString()) }
    }
}
