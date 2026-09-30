package app.tenet.android.core.database.dao

import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import app.tenet.android.core.database.entity.Attachment
import app.tenet.android.core.database.entity.EntryTag
import app.tenet.android.core.database.entity.Tag
import app.tenet.android.core.database.entity.TagKind
import java.util.UUID
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.tenet.android.core.database.entity.DiaryMeta
import app.tenet.android.core.database.entity.DreamMeta
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Query(
        """
        SELECT * FROM Entry
        WHERE type = :type AND archived = 0
        ORDER BY pinned DESC, entryDate DESC, createdAt DESC
        """,
    )
    fun observeByType(type: EntryType): Flow<List<Entry>>

    @Query(
        """
        SELECT * FROM Entry
        WHERE entryDate = :date AND archived = 0
        ORDER BY createdAt DESC
        """,
    )
    fun observeByDate(date: String): Flow<List<Entry>>

    @Query("SELECT * FROM Entry WHERE id = :id")
    fun observeById(id: String): Flow<Entry?>

    @Query("SELECT * FROM Entry WHERE id = :id")
    suspend fun getById(id: String): Entry?

    @Query("SELECT DISTINCT entryDate FROM Entry WHERE type = 'DIARY' AND archived = 0")
    fun observeDiaryDates(): Flow<List<String>>

    @Upsert
    suspend fun upsert(entry: Entry)

    @Query("DELETE FROM Entry WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM DiaryMeta WHERE entryId = :entryId")
    suspend fun getDiaryMeta(entryId: String): DiaryMeta?

    @Upsert
    suspend fun upsertDiaryMeta(meta: DiaryMeta)

    @Query("DELETE FROM DiaryMeta WHERE entryId = :entryId")
    suspend fun deleteDiaryMeta(entryId: String)

    @Query("SELECT * FROM DreamMeta WHERE entryId = :entryId")
    suspend fun getDreamMeta(entryId: String): DreamMeta?

    @Upsert
    suspend fun upsertDreamMeta(meta: DreamMeta)

    @Query("DELETE FROM DreamMeta WHERE entryId = :entryId")
    suspend fun deleteDreamMeta(entryId: String)

    /** Saves the entry and exactly one matching meta row (others are removed). */
    @Transaction
    suspend fun saveEntry(entry: Entry, diaryMeta: DiaryMeta?, dreamMeta: DreamMeta?) {
        upsert(entry)
        deleteDiaryMeta(entry.id)
        deleteDreamMeta(entry.id)
        diaryMeta?.let { upsertDiaryMeta(it) }
        dreamMeta?.let { upsertDreamMeta(it) }
    }

    @Transaction
    suspend fun deleteEntry(id: String) {
        deleteDiaryMeta(id)
        deleteDreamMeta(id)
        delete(id)
        // EntryTag rows cascade; drop tags nothing points to any more.
        deleteOrphanTags()
    }

    // ---- Pins / colors ---------------------------------------------------

    @Query("UPDATE Entry SET body = :body, updatedAt = :now WHERE id = :id")
    suspend fun updateBody(id: String, body: String, now: Long)

    @Query("UPDATE Entry SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    // ---- Tags ------------------------------------------------------------

    @Query(
        """
        SELECT t.id, t.name, t.kind, COUNT(et.entryId) AS count FROM Tag t
        LEFT JOIN EntryTag et ON et.tagId = t.id
        WHERE t.kind = :kind
        GROUP BY t.id HAVING count > 0 ORDER BY count DESC, t.name
        """,
    )
    fun observeTagCounts(kind: TagKind): Flow<List<TagCount>>

    @Query(
        """
        SELECT et.entryId, t.name, t.kind FROM EntryTag et
        JOIN Tag t ON t.id = et.tagId
        ORDER BY t.name
        """,
    )
    fun observeEntryTags(): Flow<List<EntryTagName>>

    @Query(
        """
        SELECT t.* FROM Tag t JOIN EntryTag et ON et.tagId = t.id
        WHERE et.entryId = :entryId ORDER BY t.name
        """,
    )
    suspend fun tagsForEntry(entryId: String): List<Tag>

    @Query("SELECT * FROM Tag WHERE name = :name COLLATE NOCASE AND kind = :kind LIMIT 1")
    suspend fun findTag(name: String, kind: TagKind): Tag?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTag(link: EntryTag)

    @Query("DELETE FROM EntryTag WHERE entryId = :entryId")
    suspend fun clearEntryTags(entryId: String)

    @Query("DELETE FROM Tag WHERE id NOT IN (SELECT tagId FROM EntryTag)")
    suspend fun deleteOrphanTags()

    /** Replaces the tags of [entryId] with [tags] (name + kind), creating missing ones. */
    @Transaction
    suspend fun setTags(entryId: String, tags: List<Pair<String, TagKind>>) {
        clearEntryTags(entryId)
        tags.forEach { (name, kind) ->
            val tag = findTag(name, kind) ?: Tag(UUID.randomUUID().toString(), name, kind).also { insertTag(it) }
            insertEntryTag(EntryTag(entryId, tag.id))
        }
        deleteOrphanTags()
    }

    // ---- Attachments -----------------------------------------------------

    @Query("SELECT * FROM Attachment WHERE entryId = :entryId ORDER BY createdAt")
    fun observeAttachments(entryId: String): Flow<List<Attachment>>

    @Query("SELECT * FROM Attachment WHERE entryId = :entryId ORDER BY createdAt")
    suspend fun attachmentsFor(entryId: String): List<Attachment>

    @Query("SELECT * FROM Attachment ORDER BY createdAt")
    fun observeAllAttachments(): Flow<List<Attachment>>

    @Upsert
    suspend fun upsertAttachment(attachment: Attachment)

    /** Soft delete for "Rückgängig": hidden everywhere until purged. */
    @Query("UPDATE Entry SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT id FROM Entry WHERE archived = 1")
    suspend fun archivedIds(): List<String>

    @Query("SELECT * FROM Attachment WHERE id = :id")
    suspend fun attachmentById(id: String): Attachment?

    @Query("DELETE FROM Attachment WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    // ---- Search & links ----------------------------------------------------

    /** Title, body or tag match; [pattern] is a LIKE pattern (e.g. "%foo%"). */
    @Query(
        """
        SELECT DISTINCT e.* FROM Entry e
        LEFT JOIN EntryTag et ON et.entryId = e.id
        LEFT JOIN Tag t ON t.id = et.tagId
        WHERE e.archived = 0
          AND (e.title LIKE :pattern OR e.body LIKE :pattern OR t.name LIKE :pattern)
        ORDER BY e.updatedAt DESC
        LIMIT 50
        """,
    )
    suspend fun search(pattern: String): List<Entry>

    /** Entry plus FTS4 `matchinfo(…, 'pcnx')` for relevance ranking. */
    data class FtsHit(
        @Embedded val entry: Entry,
        val info: ByteArray,
    )

    /** Full-text match on title/body; [match] is an FTS4 MATCH expression. */
    @Query(
        """
        SELECT e.*, matchinfo(EntryFts, 'pcnx') AS info FROM EntryFts
        JOIN Entry e ON e.rowid = EntryFts.docid
        WHERE EntryFts MATCH :match AND e.archived = 0
        LIMIT 200
        """,
    )
    suspend fun searchFts(match: String): List<FtsHit>

    /** Entries carrying a tag / dream symbol like [pattern]. */
    @Query(
        """
        SELECT DISTINCT e.* FROM Entry e
        JOIN EntryTag et ON et.entryId = e.id
        JOIN Tag t ON t.id = et.tagId
        WHERE e.archived = 0 AND t.name LIKE :pattern
        ORDER BY e.updatedAt DESC
        LIMIT 50
        """,
    )
    suspend fun searchByTag(pattern: String): List<Entry>

    /** Distinct note folders, alphabetical. */
    @Query("SELECT DISTINCT folder FROM Entry WHERE type = 'NOTE' AND archived = 0 AND folder IS NOT NULL AND folder != '' ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<String>>

    /** Entries whose body contains a `[[title]]` link to [title]. */
    @Query(
        """
        SELECT * FROM Entry
        WHERE archived = 0 AND id != :selfId AND :title != ''
          AND body LIKE '%[[' || :title || ']]%'
        ORDER BY updatedAt DESC
        """,
    )
    fun observeBacklinks(title: String, selfId: String): Flow<List<Entry>>

    @Query("SELECT * FROM Entry WHERE archived = 0 AND title = :title COLLATE NOCASE ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findByTitle(title: String): Entry?

    // ---- Diary views -----------------------------------------------------

    /** Diary entries on the same month/day ([monthDay] = "MM-dd") in earlier years. */
    @Query(
        """
        SELECT * FROM Entry
        WHERE type = 'DIARY' AND archived = 0
          AND substr(entryDate, 6) = :monthDay AND entryDate < :today
        ORDER BY entryDate DESC
        """,
    )
    fun observeOnThisDay(monthDay: String, today: String): Flow<List<Entry>>

    @Query(
        """
        SELECT e.entryDate AS date, AVG(m.mood) AS mood FROM Entry e
        JOIN DiaryMeta m ON m.entryId = e.id
        WHERE e.type = 'DIARY' AND e.archived = 0
        GROUP BY e.entryDate
        """,
    )
    fun observeDayMoods(): Flow<List<DayMood>>

    @Query("SELECT * FROM DiaryMeta")
    fun observeAllDiaryMeta(): Flow<List<DiaryMeta>>

    // ---- Dream views -----------------------------------------------------

    @Query("SELECT * FROM DreamMeta")
    fun observeAllDreamMeta(): Flow<List<DreamMeta>>

    @Query(
        """
        SELECT substr(e.entryDate, 1, 7) AS month, COUNT(*) AS total,
               SUM(m.lucid) AS lucid, SUM(m.nightmare) AS nightmare
        FROM Entry e JOIN DreamMeta m ON m.entryId = e.id
        WHERE e.type = 'DREAM' AND e.archived = 0
        GROUP BY month ORDER BY month
        """,
    )
    fun observeDreamMonths(): Flow<List<DreamMonth>>
}

data class TagCount(val id: String, val name: String, val kind: TagKind, val count: Int)

data class EntryTagName(val entryId: String, val name: String, val kind: TagKind)

data class DayMood(val date: String, val mood: Float)

data class DreamMonth(val month: String, val total: Int, val lucid: Int, val nightmare: Int)
