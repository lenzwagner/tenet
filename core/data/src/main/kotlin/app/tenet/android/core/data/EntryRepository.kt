package app.tenet.android.core.data

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import app.tenet.android.core.database.dao.DreamMonth
import app.tenet.android.core.database.dao.TagCount
import app.tenet.android.core.database.entity.Attachment
import app.tenet.android.core.database.entity.Tag
import app.tenet.android.core.database.entity.TagKind
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.map
import app.tenet.android.core.database.dao.EntryDao
import app.tenet.android.core.database.entity.DiaryMeta
import app.tenet.android.core.database.entity.DreamMeta
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class EntryBundle(
    val entry: Entry,
    val diaryMeta: DiaryMeta?,
    val dreamMeta: DreamMeta?,
)

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val weather: WeatherRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeByType(type: EntryType): Flow<List<Entry>> = entryDao.observeByType(type)

    fun observeByDate(date: String): Flow<List<Entry>> = entryDao.observeByDate(date)

    fun observeDiaryDates(): Flow<List<String>> = entryDao.observeDiaryDates()

    fun observeEntry(id: String): Flow<Entry?> = entryDao.observeById(id)

    suspend fun get(id: String): EntryBundle? {
        val entry = entryDao.getById(id) ?: return null
        return EntryBundle(
            entry = entry,
            diaryMeta = entryDao.getDiaryMeta(id),
            dreamMeta = entryDao.getDreamMeta(id),
        )
    }

    suspend fun save(entry: Entry, diaryMeta: DiaryMeta?, dreamMeta: DreamMeta?) {
        // Diary: the day's weather once (like a paper diary), when known.
        val stored = if (diaryMeta != null && diaryMeta.weatherCode == null) entryDao.getDiaryMeta(entry.id) else null
        val meta = if (stored?.weatherCode != null) {
            diaryMeta!!.copy(weatherCode = stored.weatherCode, tempMaxC = stored.tempMaxC, tempMinC = stored.tempMinC)
        } else if (diaryMeta != null && diaryMeta.weatherCode == null) {
            val day = runCatching { java.time.LocalDate.parse(entry.entryDate) }.getOrNull()
            day?.let { runCatching { weather.forDate(it) }.getOrNull() }
                ?.let { w -> diaryMeta.copy(weatherCode = w.code, tempMaxC = w.maxC, tempMinC = w.minC) }
                ?: diaryMeta
        } else {
            diaryMeta
        }
        entryDao.saveEntry(entry, meta, dreamMeta)
    }

    /**
     * Undoable delete: [hide] now, then [commitDelete] or [restore] from the
     * snackbar. Runs in the repository's own scope, so it also completes
     * after the calling screen is closed (editor → back).
     */
    fun hide(id: String) {
        scope.launch { entryDao.setArchived(id, true) }
    }

    fun restore(id: String) {
        scope.launch { entryDao.setArchived(id, false) }
    }

    fun commitDelete(id: String) {
        scope.launch { delete(id) }
    }

    /** Deletes entries still hidden from an interrupted undo (app killed). */
    suspend fun purgeHidden() {
        entryDao.archivedIds().forEach { delete(it) }
    }

    suspend fun delete(id: String) {
        // Voice memos live in app storage; the rows go with the entry (cascade).
        entryDao.attachmentsFor(id).forEach { deleteLocalFile(it.uri) }
        entryDao.deleteEntry(id)
    }

    /** Updates only the text (e.g. a checkbox ticked on a card), keeping meta data. */
    suspend fun updateBody(id: String, body: String) =
        entryDao.updateBody(id, body, System.currentTimeMillis())

    suspend fun setPinned(id: String, pinned: Boolean) =
        entryDao.setPinned(id, pinned, System.currentTimeMillis())

    // Tags / dream symbols
    fun observeTagCounts(kind: TagKind): Flow<List<TagCount>> = entryDao.observeTagCounts(kind)

    /** entryId -> tag names of [kind]. */
    fun observeTagsByEntry(kind: TagKind): Flow<Map<String, List<String>>> =
        entryDao.observeEntryTags().map { rows ->
            rows.filter { it.kind == kind }.groupBy({ it.entryId }, { it.name })
        }

    suspend fun tagsFor(entryId: String): List<Tag> = entryDao.tagsForEntry(entryId)

    suspend fun setTags(entryId: String, general: List<String>, symbols: List<String>) =
        entryDao.setTags(
            entryId,
            general.map { it to TagKind.GENERAL } + symbols.map { it to TagKind.DREAM_SYMBOL },
        )

    // Attachments
    fun observeAttachments(entryId: String): Flow<List<Attachment>> = entryDao.observeAttachments(entryId)

    suspend fun observeAttachmentsOnce(entryId: String): List<Attachment> = entryDao.attachmentsFor(entryId)

    /** entryId -> attachments, for thumbnails in lists. */
    fun observeAttachmentsByEntry(): Flow<Map<String, List<Attachment>>> =
        entryDao.observeAllAttachments().map { list -> list.groupBy { it.entryId } }

    suspend fun addAttachment(entryId: String, uri: String, mimeType: String) =
        entryDao.upsertAttachment(
            Attachment(
                id = UUID.randomUUID().toString(),
                entryId = entryId,
                uri = uri,
                mimeType = mimeType,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun deleteAttachment(id: String) {
        entryDao.attachmentById(id)?.let { deleteLocalFile(it.uri) }
        entryDao.deleteAttachment(id)
    }

    /** Note folders in use, alphabetical. */
    fun observeFolders(): Flow<List<String>> = entryDao.observeFolders()

    // Search & links
    suspend fun search(query: String): List<Entry> =
        if (query.isBlank()) emptyList() else entryDao.search("%${query.trim()}%")

    fun observeBacklinks(title: String, selfId: String): Flow<List<Entry>> =
        entryDao.observeBacklinks(title.trim(), selfId)

    suspend fun findByTitle(title: String): Entry? = entryDao.findByTitle(title.trim())

    // Diary views
    fun observeOnThisDay(today: LocalDate): Flow<List<Entry>> =
        entryDao.observeOnThisDay(today.toString().substring(5), today.toString())

    fun observeDayMoods(): Flow<Map<String, Float>> =
        entryDao.observeDayMoods().map { rows -> rows.associate { it.date to it.mood } }

    fun observeDiaryMeta(): Flow<Map<String, DiaryMeta>> =
        entryDao.observeAllDiaryMeta().map { rows -> rows.associateBy { it.entryId } }

    // Dream views
    fun observeDreamMeta(): Flow<Map<String, DreamMeta>> =
        entryDao.observeAllDreamMeta().map { rows -> rows.associateBy { it.entryId } }

    fun observeDreamMonths(): Flow<List<DreamMonth>> = entryDao.observeDreamMonths()

    companion object {
        /** Deletes app-private recordings (file://…/files/voice/…); content:// images stay untouched. */
        fun deleteLocalFile(uri: String) {
            if (!uri.startsWith("file://") || "/files/voice/" !in uri) return
            runCatching { java.io.File(java.net.URI(uri)).delete() }
        }
    }
}
