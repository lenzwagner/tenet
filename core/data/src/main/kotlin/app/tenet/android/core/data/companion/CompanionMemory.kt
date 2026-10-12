package app.tenet.android.core.data.companion

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the companion remembers across chats and app starts: the exchanges of
 * the last day and the note it last wrote, so "ergänze das" two minutes (or a
 * restart) later still knows which list is meant.
 */
@Singleton
class CompanionMemory @Inject constructor(@ApplicationContext context: Context) {
    data class Turn(val at: Long, val user: String, val reply: String)

    /** The note last created or extended by the companion. */
    data class Note(val id: String, val title: String, val at: Long)

    private val prefs = context.getSharedPreferences("companion_memory", Context.MODE_PRIVATE)

    /** Exchanges of the last 24 hours, oldest first. */
    fun turns(now: Long = System.currentTimeMillis()): List<Turn> {
        val arr = runCatching { JSONArray(prefs.getString(KEY_TURNS, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .map { Turn(it.optLong("at"), it.optString("u"), it.optString("r")) }
            .filter { now - it.at < DAY_MS }
    }

    fun add(user: String, reply: String, now: Long = System.currentTimeMillis()) {
        val kept = (turns(now) + Turn(now, user.take(400), reply.take(400))).takeLast(MAX_TURNS)
        val arr = JSONArray()
        kept.forEach { arr.put(JSONObject().put("at", it.at).put("u", it.user).put("r", it.reply)) }
        prefs.edit { putString(KEY_TURNS, arr.toString()) }
    }

    var lastNote: Note?
        get() = prefs.getString(KEY_NOTE_ID, null)?.let { Note(it, prefs.getString(KEY_NOTE_TITLE, "").orEmpty(), prefs.getLong(KEY_NOTE_AT, 0L)) }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_NOTE_ID)
            } else {
                putString(KEY_NOTE_ID, value.id)
                putString(KEY_NOTE_TITLE, value.title)
                putLong(KEY_NOTE_AT, value.at)
            }
        }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val MAX_TURNS = 30
        const val KEY_TURNS = "turns"
        const val KEY_NOTE_ID = "note_id"
        const val KEY_NOTE_TITLE = "note_title"
        const val KEY_NOTE_AT = "note_at"
    }
}
