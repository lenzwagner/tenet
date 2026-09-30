package app.tenet.android.core.data.sync

import android.content.Context
import android.database.Cursor
import androidx.core.content.edit
import androidx.sqlite.db.SupportSQLiteDatabase
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.database.SyncTriggers
import app.tenet.android.core.database.TenetDatabase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Two-way sync of the whole database with Firestore under users/{uid}
 * (Firebase project shared with the Saffron app, hence the "tenet" prefix).
 *
 * - Every table row is one document in `tenetRows`, id "Table~pk", with the
 *   columns as fields plus `_t` (table), `_u` (server time), `_dev` (writer)
 *   and `_del` (tombstone). GPS tracks are one document per run in `tenetTracks`
 *   instead of thousands of point documents.
 * - Local changes come from the SQLite change log (SyncTriggers); remote ones
 *   from a query on `_u` since the last pull.
 * - Conflicts: a row changed locally and not pushed yet wins; otherwise the
 *   newest write wins. First sign-in on a device: cloud data first, then the
 *   local rows the cloud does not know yet (a fresh phone's seed plan never
 *   overwrites the real one).
 *
 * Attachments (photos, voice memos) stay on the device for now.
 */
@Singleton
class CloudSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TenetDatabase,
    private val accounts: AccountRepository,
    private val saffron: SaffronRecipes,
) {
    data class Status(
        val syncing: Boolean = false,
        val lastSync: Long? = null,
        val error: String? = null,
        /** Rows sent / received in the last run. */
        val pushed: Int = 0,
        val pulled: Int = 0,
    )

    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val _status = MutableStateFlow(Status(lastSync = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 }))
    val status: StateFlow<Status> = _status.asStateFlow()

    private val deviceId: String
        get() = prefs.getString(KEY_DEVICE, null) ?: newUuid().also { prefs.edit { putString(KEY_DEVICE, it) } }

    private val sql: SupportSQLiteDatabase get() = database.openHelper.writableDatabase
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    /** Push local changes, then pull remote ones. No-op when signed out. */
    suspend fun sync(): Result<Unit> = mutex.withLock {
        val uid = accounts.account.value?.uid ?: return Result.success(Unit)
        _status.value = _status.value.copy(syncing = true, error = null)
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val user = firestore.collection("users").document(uid)
                var pulled = 0
                if (prefs.getString(KEY_INITIAL_FOR, null) != uid) {
                    pulled += initial(user)
                    prefs.edit { putString(KEY_INITIAL_FOR, uid) }
                }
                val pushed = push(user)
                pulled += pull(user)
                // Recipes saved in the Saffron app (same Firebase project).
                pulled += runCatching { saffron.importAll() }.getOrDefault(0)
                pushed to pulled
            }
        }
        result.onSuccess { (pushed, pulled) ->
            val now = System.currentTimeMillis()
            prefs.edit { putLong(KEY_LAST_SYNC, now) }
            _status.value = Status(syncing = false, lastSync = now, pushed = pushed, pulled = pulled)
        }.onFailure {
            _status.value = _status.value.copy(syncing = false, error = it.message ?: "Synchronisierung fehlgeschlagen")
        }
        result.map { }
    }

    /** Forget the per-account state (after sign-out); local data stays. */
    fun reset() {
        prefs.edit {
            remove(KEY_INITIAL_FOR)
            remove(KEY_LAST_PULL)
            remove(KEY_LAST_SYNC)
        }
        _status.value = Status()
    }

    /** Deletes everything Tenet stored in the cloud for this account. */
    suspend fun deleteCloudData(): Result<Unit> = mutex.withLock {
        runCatching {
            val uid = accounts.account.value?.uid ?: error("Nicht angemeldet")
            val user = firestore.collection("users").document(uid)
            for (collection in listOf(ROWS, TRACKS)) {
                while (true) {
                    val page = user.collection(collection).limit(400).get().await()
                    if (page.isEmpty) break
                    val batch = firestore.batch()
                    page.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            prefs.edit {
                remove(KEY_INITIAL_FOR)
                remove(KEY_LAST_PULL)
            }
        }
    }

    // ---- First sync on this device --------------------------------------------------

    private suspend fun initial(user: com.google.firebase.firestore.DocumentReference): Int {
        val tables = SyncTriggers.tables(sql).filter { it.name != TRACK_TABLE }
        // 1) Everything the cloud has, applied locally (cloud wins).
        val cloudIds = HashSet<String>()
        var maxU: Timestamp? = null
        var pulled = 0
        var last: DocumentSnapshot? = null
        while (true) {
            var q: Query = user.collection(ROWS).orderBy("_u").limit(PAGE.toLong())
            last?.let { q = q.startAfter(it) }
            val page = q.get().await()
            if (page.isEmpty) break
            applyRows(page.documents, tables, respectPending = false)
            page.documents.forEach { cloudIds += it.id }
            pulled += page.size()
            maxU = page.documents.last().getTimestamp("_u") ?: maxU
            last = page.documents.last()
            if (page.size() < PAGE) break
        }
        pullTracks(user, since = null)
        // 2) Local rows the cloud does not know yet.
        tables.forEach { t ->
            val docs = mutableListOf<Pair<String, Map<String, Any?>>>()
            sql.query("SELECT * FROM `${t.name}`").use { c ->
                while (c.moveToNext()) {
                    val row = readRow(c)
                    val id = docId(t.name, t.primaryKey.joinToString("|") { row[it].toString() })
                    if (id !in cloudIds && !mirrored(t.name, row)) docs += id to row
                }
            }
            docs.chunked(BATCH).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { (id, row) -> batch.set(user.collection(ROWS).document(id), rowDoc(t.name, row)) }
                batch.commit().await()
            }
        }
        pushAllTracks(user)
        // The change log only held what the initial upload already covered.
        sql.execSQL("DELETE FROM SyncChange")
        maxU?.let { prefs.edit { putLong(KEY_LAST_PULL, it.toDate().time) } }
        return pulled
    }

    // ---- Push ------------------------------------------------------------------------

    private data class Change(val table: String, val pk: String, val changedAt: Long)

    private suspend fun push(user: com.google.firebase.firestore.DocumentReference): Int {
        val tables = SyncTriggers.tables(sql).associateBy { it.name }
        var pushed = 0
        while (true) {
            val changes = mutableListOf<Change>()
            sql.query("SELECT tbl, pk, changedAt FROM SyncChange ORDER BY changedAt LIMIT $BATCH").use { c ->
                while (c.moveToNext()) changes += Change(c.getString(0), c.getString(1), c.getLong(2))
            }
            if (changes.isEmpty()) break
            val batch = firestore.batch()
            val trackSessions = mutableSetOf<String>()
            changes.forEach { ch ->
                if (ch.table == TRACK_TABLE) return@forEach // tracks travel with their RunSession
                val t = tables[ch.table] ?: return@forEach
                val ref = user.collection(ROWS).document(docId(ch.table, ch.pk))
                val row = rowByPk(t, ch.pk)
                if (row == null) {
                    batch.set(ref, mapOf("_t" to ch.table, "_del" to true, "_u" to FieldValue.serverTimestamp(), "_dev" to deviceId))
                    if (ch.table == "RunSession") batch.delete(user.collection(TRACKS).document(ch.pk))
                } else if (!mirrored(ch.table, row)) {
                    batch.set(ref, rowDoc(ch.table, row))
                    if (ch.table == "RunSession") trackSessions += ch.pk
                }
            }
            batch.commit().await()
            trackSessions.forEach { pushTrack(user, it) }
            // Drop only entries that did not change again meanwhile.
            changes.forEach { ch ->
                sql.execSQL("DELETE FROM SyncChange WHERE tbl = ? AND pk = ? AND changedAt = ?", arrayOf<Any>(ch.table, ch.pk, ch.changedAt))
            }
            pushed += changes.size
            if (changes.size < BATCH) break
        }
        return pushed
    }

    /** Saffron recipes live in Saffron's own collection; every device imports them from there. */
    private fun mirrored(table: String, row: Map<String, Any?>) =
        table == "Recipe" && row["source"] == SaffronRecipes.SOURCE

    private fun rowDoc(table: String, row: Map<String, Any?>): Map<String, Any?> =
        row.mapValues { (_, v) -> if (v is ByteArray) Blob.fromBytes(v) else v } +
            mapOf("_t" to table, "_del" to false, "_u" to FieldValue.serverTimestamp(), "_dev" to deviceId)

    private fun rowByPk(t: SyncTriggers.Table, pk: String): Map<String, Any?>? {
        val values = pk.split("|")
        if (values.size != t.primaryKey.size) return null
        val where = t.primaryKey.joinToString(" AND ") { "CAST(`$it` AS TEXT) = ?" }
        sql.query("SELECT * FROM `${t.name}` WHERE $where", values.toTypedArray()).use { c ->
            return if (c.moveToFirst()) readRow(c) else null
        }
    }

    private fun readRow(c: Cursor): Map<String, Any?> = (0 until c.columnCount).associate { i ->
        c.getColumnName(i) to when (c.getType(i)) {
            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
            Cursor.FIELD_TYPE_STRING -> c.getString(i)
            Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
            else -> null
        }
    }

    // ---- Pull ------------------------------------------------------------------------

    private suspend fun pull(user: com.google.firebase.firestore.DocumentReference): Int {
        val tables = SyncTriggers.tables(sql).filter { it.name != TRACK_TABLE }
        val since = prefs.getLong(KEY_LAST_PULL, 0L)
        var pulled = 0
        var cursor = Timestamp(java.util.Date(since))
        while (true) {
            val page = user.collection(ROWS).whereGreaterThan("_u", cursor).orderBy("_u").limit(PAGE.toLong()).get().await()
            if (page.isEmpty) break
            val foreign = page.documents.filter { it.getString("_dev") != deviceId }
            applyRows(foreign, tables, respectPending = true)
            pulled += foreign.size
            cursor = page.documents.last().getTimestamp("_u") ?: break
            prefs.edit { putLong(KEY_LAST_PULL, cursor.toDate().time) }
            if (page.size() < PAGE) break
        }
        pulled += pullTracks(user, since = Timestamp(java.util.Date(since)))
        return pulled
    }

    /**
     * Writes remote rows without echoing them into the change log. Foreign keys
     * are off meanwhile: pages arrive in time order, a child may come before
     * its parent; the cloud copy is consistent once the page run is complete.
     */
    private fun applyRows(docs: List<DocumentSnapshot>, tables: List<SyncTriggers.Table>, respectPending: Boolean) {
        if (docs.isEmpty()) return
        val byName = tables.associateBy { it.name }
        val db = sql
        db.execSQL("PRAGMA foreign_keys = OFF")
        db.beginTransaction()
        try {
            db.execSQL("UPDATE SyncControl SET applying = 1")
            docs.forEach { doc ->
                val t = byName[doc.getString("_t")] ?: return@forEach
                val pk = pkFromDocId(doc.id) ?: return@forEach
                if (respectPending && pending(t.name, pk)) return@forEach // unsent local change is newer
                val pkValues = pk.split("|")
                val where = t.primaryKey.joinToString(" AND ") { "CAST(`$it` AS TEXT) = ?" }
                if (doc.getBoolean("_del") == true) {
                    db.execSQL("DELETE FROM `${t.name}` WHERE $where", pkValues.toTypedArray())
                    if (t.name == "RunSession") db.execSQL("DELETE FROM $TRACK_TABLE WHERE sessionId = ?", arrayOf(pk))
                    return@forEach
                }
                val cols = t.columns.filter { doc.contains(it) }
                val values = cols.map { toSql(doc.get(it)) }
                val set = cols.filter { it !in t.primaryKey }
                val updated = if (set.isEmpty()) 0 else db.compileStatement(
                    "UPDATE `${t.name}` SET ${set.joinToString { "`$it` = ?" }} WHERE $where",
                ).use { st ->
                    var i = 1
                    set.forEach { bind(st, i++, toSql(doc.get(it))) }
                    pkValues.forEach { st.bindString(i++, it) }
                    st.executeUpdateDelete()
                }
                if (updated == 0) {
                    db.compileStatement(
                        "INSERT OR IGNORE INTO `${t.name}` (${cols.joinToString { "`$it`" }}) VALUES (${cols.joinToString { "?" }})",
                    ).use { st ->
                        values.forEachIndexed { i, v -> bind(st, i + 1, v) }
                        st.executeInsert()
                    }
                }
            }
            db.execSQL("UPDATE SyncControl SET applying = 0")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys = ON")
        }
        database.invalidationTracker.refreshVersionsAsync()
    }

    private fun pending(table: String, pk: String): Boolean =
        sql.query("SELECT 1 FROM SyncChange WHERE tbl = ? AND pk = ?", arrayOf(table, pk)).use { it.moveToFirst() }

    private fun toSql(v: Any?): Any? = when (v) {
        is Boolean -> if (v) 1L else 0L
        is Int -> v.toLong()
        is Float -> v.toDouble()
        is Blob -> v.toBytes()
        is Timestamp -> v.toDate().time
        else -> v
    }

    private fun bind(st: androidx.sqlite.db.SupportSQLiteStatement, i: Int, v: Any?) = when (v) {
        null -> st.bindNull(i)
        is Long -> st.bindLong(i, v)
        is Double -> st.bindDouble(i, v)
        is ByteArray -> st.bindBlob(i, v)
        else -> st.bindString(i, v.toString())
    }

    // ---- GPS tracks: one document per run --------------------------------------------

    private suspend fun pushAllTracks(user: com.google.firebase.firestore.DocumentReference) {
        val ids = mutableListOf<String>()
        sql.query("SELECT DISTINCT sessionId FROM $TRACK_TABLE").use { c -> while (c.moveToNext()) ids += c.getString(0) }
        ids.forEach { pushTrack(user, it) }
    }

    private suspend fun pushTrack(user: com.google.firebase.firestore.DocumentReference, sessionId: String) {
        val lines = mutableListOf<String>()
        var start = 0L
        sql.query(
            "SELECT timestamp, lat, lon, altitude, hr FROM $TRACK_TABLE WHERE sessionId = ? ORDER BY timestamp",
            arrayOf(sessionId),
        ).use { c ->
            while (c.moveToNext()) {
                val t = c.getLong(0)
                if (lines.isEmpty()) start = t
                lines += String.format(
                    Locale.US, "%d,%.6f,%.6f,%s,%s",
                    t - start, c.getDouble(1), c.getDouble(2),
                    if (c.isNull(3)) "" else String.format(Locale.US, "%.1f", c.getDouble(3)),
                    if (c.isNull(4)) "" else c.getInt(4).toString(),
                )
            }
        }
        if (lines.isEmpty()) return
        // Firestore documents max out at 1 MiB: thin very long runs.
        var kept: List<String> = lines
        while (kept.sumOf { it.length + 1 } > 900_000) kept = kept.filterIndexed { i, _ -> i % 2 == 0 }
        user.collection(TRACKS).document(sessionId).set(
            mapOf("start" to start, "pts" to kept.joinToString("\n"), "_u" to FieldValue.serverTimestamp(), "_dev" to deviceId),
            SetOptions.merge(),
        ).await()
    }

    private suspend fun pullTracks(user: com.google.firebase.firestore.DocumentReference, since: Timestamp?): Int {
        var q: Query = user.collection(TRACKS)
        if (since != null) q = q.whereGreaterThan("_u", since)
        val docs = q.get().await().documents.filter { since == null || it.getString("_dev") != deviceId }
        if (docs.isEmpty()) return 0
        val db = sql
        db.beginTransaction()
        try {
            db.execSQL("UPDATE SyncControl SET applying = 1")
            docs.forEach { doc ->
                val start = doc.getLong("start") ?: return@forEach
                val pts = doc.getString("pts") ?: return@forEach
                db.execSQL("DELETE FROM $TRACK_TABLE WHERE sessionId = ?", arrayOf(doc.id))
                db.compileStatement(
                    "INSERT INTO $TRACK_TABLE (id, sessionId, timestamp, lat, lon, altitude, hr) VALUES (?, ?, ?, ?, ?, ?, ?)",
                ).use { st ->
                    pts.lineSequence().forEachIndexed { i, line ->
                        val f = line.split(",")
                        if (f.size < 3) return@forEachIndexed
                        st.clearBindings()
                        st.bindString(1, "${doc.id}-$i")
                        st.bindString(2, doc.id)
                        st.bindLong(3, start + (f[0].toLongOrNull() ?: 0L))
                        st.bindDouble(4, f[1].toDouble())
                        st.bindDouble(5, f[2].toDouble())
                        f.getOrNull(3)?.toDoubleOrNull()?.let { st.bindDouble(6, it) } ?: st.bindNull(6)
                        f.getOrNull(4)?.toLongOrNull()?.let { st.bindLong(7, it) } ?: st.bindNull(7)
                        st.executeInsert()
                    }
                }
            }
            db.execSQL("UPDATE SyncControl SET applying = 0")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        database.invalidationTracker.refreshVersionsAsync()
        return docs.size
    }

    // ---- Ids -------------------------------------------------------------------------

    private fun docId(table: String, pk: String) = "$table~" + URLEncoder.encode(pk, "UTF-8")

    private fun pkFromDocId(id: String): String? =
        id.substringAfter('~', "").takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, "UTF-8") }

    private companion object {
        const val ROWS = "tenetRows"
        const val TRACKS = "tenetTracks"
        const val TRACK_TABLE = "RunTrackPoint"
        const val PAGE = 500
        const val BATCH = 400
        const val KEY_DEVICE = "device_id"
        const val KEY_INITIAL_FOR = "initial_for_uid"
        const val KEY_LAST_PULL = "last_pull"
        const val KEY_LAST_SYNC = "last_sync"
    }
}
