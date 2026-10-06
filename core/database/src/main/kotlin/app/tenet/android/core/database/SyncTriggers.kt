package app.tenet.android.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Change log for the cloud sync, kept outside Room's entities so the schema
 * version is untouched: every insert/update/delete on a synced table records
 * (table, primary key) in `SyncChange` via SQLite triggers. While the sync
 * applies remote rows it sets `SyncControl.applying = 1`, so those writes are
 * not echoed back. Triggers are (re)created on every open, which also covers
 * tables added by later migrations.
 */
object SyncTriggers {

    /**
     * Room internals, FTS shadow tables, the sync tables themselves and
     * device-local data (form videos point to files on this phone only).
     */
    private val SKIP = setOf("android_metadata", "room_master_table", "sqlite_sequence", "SyncChange", "SyncControl", "FormVideo")

    /**
     * Synced as one document per run together with its RunSession, so no
     * per-point change log (a run has thousands of points).
     */
    const val TRACK_TABLE = "RunTrackPoint"

    data class Table(val name: String, val columns: List<String>, val primaryKey: List<String>)

    fun install(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS SyncChange (tbl TEXT NOT NULL, pk TEXT NOT NULL, changedAt INTEGER NOT NULL, PRIMARY KEY(tbl, pk))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS SyncControl (id INTEGER PRIMARY KEY CHECK (id = 1), applying INTEGER NOT NULL)")
        db.execSQL("INSERT OR IGNORE INTO SyncControl (id, applying) VALUES (1, 0)")
        // A crash mid-apply must not leave change tracking switched off.
        db.execSQL("UPDATE SyncControl SET applying = 0")
        tables(db).filter { it.name != TRACK_TABLE }.forEach { t ->
            val guard = "WHEN (SELECT applying FROM SyncControl WHERE id = 1) = 0"
            // Milliseconds: the push only clears entries whose timestamp is unchanged.
            val now = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t.name}_ins AFTER INSERT ON `${t.name}` $guard BEGIN " +
                    "INSERT OR REPLACE INTO SyncChange VALUES ('${t.name}', ${pkExpr(t, "NEW")}, $now); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t.name}_upd AFTER UPDATE ON `${t.name}` $guard BEGIN " +
                    "INSERT OR REPLACE INTO SyncChange VALUES ('${t.name}', ${pkExpr(t, "NEW")}, $now); END",
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS sync_${t.name}_del AFTER DELETE ON `${t.name}` $guard BEGIN " +
                    "INSERT OR REPLACE INTO SyncChange VALUES ('${t.name}', ${pkExpr(t, "OLD")}, $now); END",
            )
        }
    }

    /** Synced tables with their columns and primary key (FTS tables excluded). */
    fun tables(db: SupportSQLiteDatabase): List<Table> {
        val names = mutableListOf<String>()
        db.query("SELECT name, sql FROM sqlite_master WHERE type = 'table'").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                val sql = c.getString(1).orEmpty()
                val fts = sql.contains("VIRTUAL TABLE", ignoreCase = true) || name.contains("Fts") || name.contains("_segments") ||
                    name.contains("_segdir") || name.contains("_docsize") || name.contains("_stat") || name.endsWith("_content")
                if (name !in SKIP && !name.startsWith("sqlite_") && !fts) names += name
            }
        }
        return names.sorted().mapNotNull { name ->
            val cols = mutableListOf<Pair<String, Int>>()
            db.query("PRAGMA table_info(`$name`)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                val pkIdx = c.getColumnIndexOrThrow("pk")
                while (c.moveToNext()) cols += c.getString(nameIdx) to c.getInt(pkIdx)
            }
            val pk = cols.filter { it.second > 0 }.sortedBy { it.second }.map { it.first }
            if (pk.isEmpty()) null else Table(name, cols.map { it.first }, pk)
        }
    }

    private fun pkExpr(t: Table, row: String) =
        t.primaryKey.joinToString(" || '|' || ") { "CAST($row.`$it` AS TEXT)" }
}
