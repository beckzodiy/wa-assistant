package com.beeone.waassistant

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", MODELS.first()) ?: MODELS.first()
        set(v) = sp.edit().putString("model", v).apply()

    var myLanguage: String
        get() = sp.getString("my_language", "English") ?: "English"
        set(v) = sp.edit().putString("my_language", v.trim().ifEmpty { "English" }).apply()

    var businessNotes: String
        get() = sp.getString("notes", DEFAULT_NOTES) ?: DEFAULT_NOTES
        set(v) = sp.edit().putString("notes", v).apply()

    var includePersonalWhatsApp: Boolean
        get() = sp.getBoolean("include_personal", false)
        set(v) = sp.edit().putBoolean("include_personal", v).apply()

    var includeGroups: Boolean
        get() = sp.getBoolean("include_groups", false)
        set(v) = sp.edit().putBoolean("include_groups", v).apply()

    companion object {
        /** First entry is the default. Opus = best quality; Haiku = fastest/cheapest. */
        val MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")

        const val DEFAULT_NOTES = """Company: Bee One Global (UAE).
Brand: Mommy Knows.
Products: baby diapers (all sizes), baby wipes, sanitary napkins.
We deliver across the UAE.
If a customer asks for a price, stock, or delivery time that is not written here, say we will check and reply shortly."""
    }
}
