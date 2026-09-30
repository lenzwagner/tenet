package app.tenet.android.feature.journal

import app.tenet.android.feature.journal.markdown.toggleCheck
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.EntryRepository
import app.tenet.android.core.database.dao.DreamMonth
import app.tenet.android.core.database.dao.TagCount
import app.tenet.android.core.database.entity.Attachment
import app.tenet.android.core.database.entity.DiaryMeta
import app.tenet.android.core.database.entity.DreamMeta
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.database.entity.TagKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the three journal pages need, loaded once and shared. */
data class JournalUiState(
    val loading: Boolean = true,
    val notes: List<Entry> = emptyList(),
    val diary: List<Entry> = emptyList(),
    val dreams: List<Entry> = emptyList(),
    val tagsByEntry: Map<String, List<String>> = emptyMap(),
    val symbolsByEntry: Map<String, List<String>> = emptyMap(),
    val attachments: Map<String, List<Attachment>> = emptyMap(),
    val diaryMeta: Map<String, DiaryMeta> = emptyMap(),
    val dreamMeta: Map<String, DreamMeta> = emptyMap(),
    val dayMoods: Map<String, Float> = emptyMap(),
    val onThisDay: List<Entry> = emptyList(),
    val tagCounts: List<TagCount> = emptyList(),
    val symbolCounts: List<TagCount> = emptyList(),
    val dreamMonths: List<DreamMonth> = emptyList(),
    /** Note folders in use. */
    val folders: List<String> = emptyList(),
    /** ISO wake-up date → the night's sleep (Health Connect). */
    val sleepByDate: Map<String, app.tenet.android.core.common.SleepNight> = emptyMap(),
) {
    fun entriesOf(type: EntryType): List<Entry> = when (type) {
        EntryType.NOTE -> notes
        EntryType.DIARY -> diary
        EntryType.DREAM -> dreams
    }
}

/** Weekly goal: one entry per day. */
const val WEEKLY_ENTRY_GOAL = 7

fun segmentToEntryType(segment: Int): EntryType = when (segment) {
    0 -> EntryType.NOTE
    1 -> EntryType.DIARY
    else -> EntryType.DREAM
}

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val healthConnect: app.tenet.android.core.data.health.HealthConnectRepository,
) : ViewModel() {

    /** Sleep per wake-up day (Health Connect), shown next to the dreams. */
    private val sleep = kotlinx.coroutines.flow.MutableStateFlow<Map<String, app.tenet.android.core.common.SleepNight>>(emptyMap())

    /** Reads the last 90 nights (called when the journal is shown). */
    fun refreshSleep() {
        viewModelScope.launch {
            val from = java.time.Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS)
            sleep.value = runCatching { healthConnect.sleepNights(from) }.getOrDefault(emptyMap())
                .mapKeys { it.key.toString() }
        }
    }

    private data class Lists(
        val notes: List<Entry>,
        val diary: List<Entry>,
        val dreams: List<Entry>,
        val folders: List<String>,
    )
    private data class Meta(
        val tags: Map<String, List<String>>,
        val symbols: Map<String, List<String>>,
        val attachments: Map<String, List<Attachment>>,
        val diary: Map<String, DiaryMeta>,
        val dream: Map<String, DreamMeta>,
    )
    private data class Stats(
        val moods: Map<String, Float>,
        val onThisDay: List<Entry>,
        val tagCounts: List<TagCount>,
        val symbolCounts: List<TagCount>,
        val months: List<DreamMonth>,
    )

    private val lists = combine(
        entryRepository.observeByType(EntryType.NOTE),
        entryRepository.observeByType(EntryType.DIARY),
        entryRepository.observeByType(EntryType.DREAM),
        entryRepository.observeFolders(),
    ) { n, d, dr, f -> Lists(n, d, dr, f) }

    private val meta = combine(
        entryRepository.observeTagsByEntry(TagKind.GENERAL),
        entryRepository.observeTagsByEntry(TagKind.DREAM_SYMBOL),
        entryRepository.observeAttachmentsByEntry(),
        entryRepository.observeDiaryMeta(),
        entryRepository.observeDreamMeta(),
    ) { t, s, a, d, dr -> Meta(t, s, a, d, dr) }

    private val stats = combine(
        entryRepository.observeDayMoods(),
        entryRepository.observeOnThisDay(LocalDate.now()),
        entryRepository.observeTagCounts(TagKind.GENERAL),
        entryRepository.observeTagCounts(TagKind.DREAM_SYMBOL),
        entryRepository.observeDreamMonths(),
    ) { m, o, t, s, mo -> Stats(m, o, t, s, mo) }

    val uiState: StateFlow<JournalUiState> = combine(lists, meta, stats, sleep) { l, m, s, night ->
        JournalUiState(
            sleepByDate = night,
            loading = false,
            notes = l.notes,
            diary = l.diary,
            dreams = l.dreams,
            tagsByEntry = m.tags,
            symbolsByEntry = m.symbols,
            attachments = m.attachments,
            diaryMeta = m.diary,
            dreamMeta = m.dream,
            dayMoods = s.moods,
            onThisDay = s.onThisDay,
            tagCounts = s.tagCounts,
            symbolCounts = s.symbolCounts,
            dreamMonths = s.months,
            folders = l.folders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch { entryRepository.setPinned(id, pinned) }
    }

    /** Ticks a checklist item straight from the card. */
    fun toggleCheck(entry: Entry, line: Int) {
        viewModelScope.launch { entryRepository.updateBody(entry.id, toggleCheck(entry.body, line)) }
    }

    /** Undoable delete: hidden now, removed when the snackbar is gone. */
    fun hide(id: String) = entryRepository.hide(id)
    fun restore(id: String) = entryRepository.restore(id)
    fun commitDelete(id: String) = entryRepository.commitDelete(id)
}
