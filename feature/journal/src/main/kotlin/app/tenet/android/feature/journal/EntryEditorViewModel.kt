package app.tenet.android.feature.journal

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import app.tenet.android.core.data.ai.AiFiller
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.common.newUuid
import app.tenet.android.core.common.SleepNight
import app.tenet.android.core.data.EntryRepository
import app.tenet.android.core.data.FoodRepository
import app.tenet.android.core.data.WeekCalendarRepository
import app.tenet.android.core.database.dao.TagCount
import app.tenet.android.core.database.entity.DiaryMeta
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.DreamMeta
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.EntryType
import app.tenet.android.core.database.entity.TagKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** An image attached in the editor; [id] is null until saved. */
data class AttachmentUi(val id: String?, val uri: String, val mimeType: String)

data class EntryEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: EntryType = EntryType.NOTE,
    val title: String = "",
    val body: String = "",
    val entryDate: String = LocalDate.now().toString(),
    // Diary
    val mood: Int = 3,
    val energy: Int? = null,
    val sleepQuality: Int? = null,
    // Dream
    val clarity: Int = 3,
    val lucid: Boolean = false,
    val nightmare: Boolean = false,
    val recurring: Boolean = false,
    val emotions: Set<String> = emptySet(),
    val symbols: List<String> = emptyList(),
    // Notes & all
    val pinned: Boolean = false,
    val color: Int? = null,
    /** Notes only. */
    val folder: String = "",
    val tags: List<String> = emptyList(),
    val attachments: List<AttachmentUi> = emptyList(),
    /** Bumped by undo: text editors with their own state start over from [body]. */
    val revision: Int = 0,
)

/** Diary context of the entry's day (App_Konzept.md 5.3 "Automatische Kontextinfos"). */
data class DayContext(
    val kcal: Int = 0,
    val meals: Int = 0,
    val workouts: List<String> = emptyList(),
)

/** Where a tapped `[[link]]` leads. */
sealed interface LinkTarget {
    data class Existing(val id: String, val type: EntryType) : LinkTarget
    data class Create(val title: String) : LinkTarget
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class EntryEditorViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val foodRepository: FoodRepository,
    private val weekCalendarRepository: WeekCalendarRepository,
    private val aiFiller: AiFiller,
    private val healthConnect: app.tenet.android.core.data.health.HealthConnectRepository,
) : ViewModel() {

    // ---- AI fill-in (NVIDIA NIM) ------------------------------------------

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()
    val aiAvailable: Boolean get() = aiFiller.enabled

    private val _aiResult = Channel<String>(Channel.BUFFERED)
    /** Summary of what the AI filled in (for the undo snackbar). */
    val aiResult = _aiResult.receiveAsFlow()
    private var beforeAi: EntryEditorState? = null

    /**
     * Fills title/metadata from the body text. Only empty fields and
     * untouched defaults are filled; lists are merged, never replaced.
     */
    fun aiFill() {
        val s = _state.value
        if (s.body.isBlank() || _aiBusy.value || !aiFiller.enabled) return
        _aiBusy.value = true
        viewModelScope.launch {
            val before = _state.value
            val filled: List<String> = when (before.type) {
                EntryType.DREAM -> aiFiller.dream(before.body, DreamEmotions)?.let { f ->
                    update {
                        copy(
                            title = title.ifBlank { f.title.orEmpty() },
                            clarity = f.clarity ?: clarity,
                            lucid = lucid || f.lucid == true,
                            nightmare = nightmare || f.nightmare == true,
                            recurring = recurring || f.recurring == true,
                            emotions = emotions + f.emotions,
                            symbols = (symbols + f.symbols.filter { n -> symbols.none { it.equals(n, true) } }),
                        )
                    }
                    listOfNotNull(
                        "Titel".takeIf { before.title.isBlank() && f.title != null },
                        "Klarheit".takeIf { f.clarity != null },
                        "Emotionen".takeIf { f.emotions.isNotEmpty() },
                        "Symbole".takeIf { f.symbols.isNotEmpty() },
                        "Luzid".takeIf { f.lucid == true },
                        "Albtraum".takeIf { f.nightmare == true },
                    )
                }
                EntryType.DIARY -> aiFiller.diary(before.body)?.let { f ->
                    update {
                        copy(
                            title = title.ifBlank { f.title.orEmpty() },
                            mood = f.mood ?: mood,
                            energy = energy ?: f.energy,
                            sleepQuality = sleepQuality ?: f.sleep,
                            tags = tags + f.tags.filter { n -> tags.none { it.equals(n, true) } },
                        )
                    }
                    listOfNotNull(
                        "Titel".takeIf { before.title.isBlank() && f.title != null },
                        "Stimmung".takeIf { f.mood != null },
                        "Energie".takeIf { before.energy == null && f.energy != null },
                        "Schlaf".takeIf { before.sleepQuality == null && f.sleep != null },
                        "Tags".takeIf { f.tags.isNotEmpty() },
                    )
                }
                EntryType.NOTE -> aiFiller.note(before.body)?.let { f ->
                    update {
                        copy(
                            title = title.ifBlank { f.title.orEmpty() },
                            tags = tags + f.tags.filter { n -> tags.none { it.equals(n, true) } },
                        )
                    }
                    listOfNotNull(
                        "Titel".takeIf { before.title.isBlank() && f.title != null },
                        "Tags".takeIf { f.tags.isNotEmpty() },
                    )
                }
            } ?: emptyList()
            _aiBusy.value = false
            if (filled.isEmpty()) {
                _aiResult.send("KI konnte nichts ergänzen.")
            } else {
                beforeAi = before
                _aiResult.send("KI hat ergänzt: " + filled.joinToString(", "))
            }
        }
    }

    /** Undo the last AI fill-in (the body stays as it is). */
    fun undoAi() {
        val before = beforeAi ?: return
        beforeAi = null
        update { before.copy(body = body) }
    }

    private val _state = MutableStateFlow(EntryEditorState())
    val state: StateFlow<EntryEditorState> = _state.asStateFlow()

    private val _sleepNight = MutableStateFlow<SleepNight?>(null)
    val sleepNight: StateFlow<SleepNight?> = _sleepNight.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private val _links = Channel<LinkTarget>(Channel.BUFFERED)
    val links = _links.receiveAsFlow()

    private var entryId: String? = null
    private var originalCreatedAt: Long? = null
    private var savedAttachmentIds: Set<String> = emptySet()
    private var loadedFor: String? = null

    /** Existing tags and dream symbols as suggestions, most used first. */
    val tagSuggestions: StateFlow<List<TagCount>> = entryRepository.observeTagCounts(TagKind.GENERAL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val symbolSuggestions: StateFlow<List<TagCount>> = entryRepository.observeTagCounts(TagKind.DREAM_SYMBOL)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Entries linking here via `[[title]]`. */
    val backlinks: StateFlow<List<Entry>> = _state
        .map { it.title.trim() to (entryId ?: "") }
        .distinctUntilChanged()
        .flatMapLatest { (title, id) ->
            if (title.isBlank()) flowOf(emptyList()) else entryRepository.observeBacklinks(title, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayContext: StateFlow<DayContext> = _state
        .map { it.type to it.entryDate }
        .distinctUntilChanged()
        .flatMapLatest { (type, date) -> if (type == EntryType.DIARY) contextFor(date) else flowOf(DayContext()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayContext())

    private fun contextFor(date: String): Flow<DayContext> {
        val day = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
        return combine(
            foodRepository.observeLogs(date),
            weekCalendarRepository.observeSessionsBetween(day, day.plusDays(1)),
        ) { logs, sessions ->
            DayContext(
                kcal = logs.sumOf { it.kcal.toDouble() }.toInt(),
                meals = logs.size,
                workouts = sessions.map { row ->
                    val label = when (row.session.discipline) {
                        Discipline.GYM -> "Gym"
                        Discipline.CALISTHENICS -> "Calisthenics"
                        Discipline.RUNNING -> "Lauf"
                    }
                    row.workoutTitle?.let { "$label · $it" } ?: label
                },
            )
        }
    }

    /**
     * Loads an entry or prepares a new one of [type]; [title] and [date]
     * prefill new entries (e.g. from a `[[link]]` or the mood calendar).
     */
    fun load(id: String?, type: EntryType, title: String = "", date: String = "") {
        val key = id ?: "new:$type:$title:$date"
        if (loadedFor == key) return
        loadedFor = key
        if (id == null) {
            entryId = null
            _state.value = EntryEditorState(
                loading = false,
                isNew = true,
                type = type,
                title = title,
                entryDate = date.ifBlank { LocalDate.now().toString() },
            )
            refreshSleepForDream()
            return
        }
        viewModelScope.launch {
            val bundle = entryRepository.get(id)
            if (bundle == null) {
                _state.value = EntryEditorState(loading = false, isNew = true, type = type)
                return@launch
            }
            val entry = bundle.entry
            entryId = id
            originalCreatedAt = entry.createdAt
            val tags = entryRepository.tagsFor(id)
            val attachments = entryRepository.observeAttachmentsOnce(id)
            savedAttachmentIds = attachments.map { it.id }.toSet()
            _state.value = EntryEditorState(
                loading = false,
                isNew = false,
                type = entry.type,
                title = entry.title,
                body = entry.body,
                entryDate = entry.entryDate,
                mood = bundle.diaryMeta?.mood ?: 3,
                energy = bundle.diaryMeta?.energy,
                sleepQuality = bundle.diaryMeta?.sleepQuality,
                clarity = bundle.dreamMeta?.clarity ?: 3,
                lucid = bundle.dreamMeta?.lucid ?: false,
                nightmare = bundle.dreamMeta?.nightmare ?: false,
                recurring = bundle.dreamMeta?.recurring ?: false,
                emotions = bundle.dreamMeta?.emotions
                    ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
                    ?: emptySet(),
                symbols = tags.filter { it.kind == TagKind.DREAM_SYMBOL }.map { it.name },
                pinned = entry.pinned,
                color = entry.color,
                folder = entry.folder.orEmpty(),
                tags = tags.filter { it.kind == TagKind.GENERAL }.map { it.name },
                attachments = attachments.map { AttachmentUi(it.id, it.uri, it.mimeType) },
            )
            refreshSleepForDream()
        }
    }

    /** Loads Health Connect sleep for the dream's wake-up day. */
    private fun refreshSleepForDream() {
        val snapshot = _state.value
        _sleepNight.value = null
        if (snapshot.type != EntryType.DREAM) return
        val day = runCatching { LocalDate.parse(snapshot.entryDate) }.getOrNull() ?: return
        viewModelScope.launch {
            val from = day.minusDays(1)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
            val night = runCatching { healthConnect.sleepNights(from)[day] }.getOrNull()
            if (_state.value.type == EntryType.DREAM && _state.value.entryDate == snapshot.entryDate) {
                _sleepNight.value = night
            }
        }
    }

    // ---- Undo ------------------------------------------------------------

    /** Earlier states; typing is grouped into steps (a pause of 1 s starts a new one). */
    private val history = ArrayDeque<EntryEditorState>()
    private var lastSnapshotAt = 0L
    /** The user changed something (mood alone makes a diary entry worth keeping). */
    private var edited = false
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private fun update(block: EntryEditorState.() -> EntryEditorState) {
        val before = _state.value
        val after = before.block()
        if (after == before) return
        if (!before.loading) {
            edited = true
            val now = System.currentTimeMillis()
            val typing = after.title != before.title || after.body != before.body
            if (!typing || now - lastSnapshotAt > 1_000 || history.isEmpty()) {
                history.addLast(before)
                if (history.size > 100) history.removeFirst()
                _canUndo.value = true
            }
            lastSnapshotAt = now
        }
        _state.value = after
    }

    /** One step back; attachments stay as they are (their files may be gone already). */
    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        _canUndo.value = history.isNotEmpty()
        lastSnapshotAt = 0L
        val current = _state.value
        _state.value = previous.copy(
            isNew = current.isNew,
            attachments = current.attachments,
            revision = current.revision + 1,
        )
    }

    fun onTitle(value: String) = update { copy(title = value) }
    fun onBody(value: String) = update { copy(body = value) }
    fun onMood(value: Int) = update { copy(mood = value.coerceIn(1, 5)) }
    fun onEnergy(value: Int?) = update { copy(energy = value) }
    fun onSleep(value: Int?) = update { copy(sleepQuality = value) }
    fun onClarity(value: Float) = update { copy(clarity = value.toInt().coerceIn(1, 5)) }
    fun onLucid(value: Boolean) = update { copy(lucid = value) }
    fun onNightmare(value: Boolean) = update { copy(nightmare = value) }
    fun onRecurring(value: Boolean) = update { copy(recurring = value) }
    fun onPinned(value: Boolean) = update { copy(pinned = value) }
    /** A colour as background replaces a chosen photo background (and the other way round). */
    fun onColor(value: Int?) = update {
        copy(color = value, attachments = if (value != null) attachments.filter { it.mimeType != NoteBackgrounds.MIME } else attachments)
    }

    /** Built-in photo background [index] (null = none). */
    fun onBackground(index: Int?) = update {
        val rest = attachments.filter { it.mimeType != NoteBackgrounds.MIME }
        copy(
            color = if (index != null) null else color,
            attachments = if (index == null) rest else listOf(AttachmentUi(null, NoteBackgrounds.uri(index), NoteBackgrounds.MIME)) + rest,
        )
    }
    fun onFolder(value: String) = update { copy(folder = value) }

    /** Existing note folders as suggestions. */
    val folders: StateFlow<List<String>> = entryRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun toggleEmotion(emotion: String) = update {
        copy(emotions = if (emotion in emotions) emotions - emotion else emotions + emotion)
    }

    fun addTag(name: String) = update {
        val clean = name.trim().removePrefix("#")
        if (clean.isEmpty() || tags.any { it.equals(clean, ignoreCase = true) }) this else copy(tags = tags + clean)
    }
    fun removeTag(name: String) = update { copy(tags = tags - name) }

    fun addSymbol(name: String) = update {
        val clean = name.trim()
        if (clean.isEmpty() || symbols.any { it.equals(clean, ignoreCase = true) }) this else copy(symbols = symbols + clean)
    }
    fun removeSymbol(name: String) = update { copy(symbols = symbols - name) }

    fun addAttachment(uri: String, mimeType: String) = update {
        copy(attachments = attachments + AttachmentUi(null, uri, mimeType))
    }
    fun removeAttachment(item: AttachmentUi) {
        // A recording that was never saved can go right away.
        if (item.id == null) EntryRepository.deleteLocalFile(item.uri)
        update { copy(attachments = attachments - item) }
    }

    /** Appends dictated text (speech input) to the body. */
    fun appendDictation(text: String) {
        update { copy(body = if (body.isBlank()) text else body.trimEnd() + "\n" + text) }
        // Spoken entries get their fields filled automatically.
        if (aiFiller.enabled) aiFill()
    }

    /** Resolves a tapped `[[title]]` link to an existing or new note. */
    fun openLink(title: String) {
        viewModelScope.launch {
            val target = entryRepository.findByTitle(title)
            _links.send(
                if (target != null) LinkTarget.Existing(target.id, target.type) else LinkTarget.Create(title),
            )
        }
    }

    // ---- Saving ------------------------------------------------------------

    /** Writes run one after another; set once the entry was deleted. */
    private val saveMutex = kotlinx.coroutines.sync.Mutex()
    private var closed = false

    init {
        // Autosave: shortly after the last change, without pressing anything.
        viewModelScope.launch {
            _state
                .filter { !it.loading }
                // Only user content counts (not what saving itself writes back).
                .map { s -> s.copy(revision = 0, isNew = false, attachments = s.attachments.map { it.copy(id = null) }) }
                .distinctUntilChanged()
                .drop(1) // the freshly loaded state
                .debounce(800)
                .collect { persist() }
        }
    }

    private fun isEmpty(s: EntryEditorState) =
        s.title.isBlank() && s.body.replace("- [ ]", "").isBlank() && s.attachments.isEmpty()

    /** Stores the current state (no-op for a new, still empty entry). */
    private suspend fun persist() = saveMutex.withLock {
        val s = _state.value
        // A new note needs content; a new diary or dream entry also counts once mood,
        // sleep, energy or other fields were set.
        val worthKeeping = !isEmpty(s) || (s.type != EntryType.NOTE && edited)
        if (closed || s.loading || (entryId == null && !worthKeeping)) return@withLock
        val now = System.currentTimeMillis()
        val id = entryId ?: newUuid().also { entryId = it }
        val entry = Entry(
            id = id,
            type = s.type,
            title = s.title.trim(),
            body = s.body,
            createdAt = originalCreatedAt ?: now.also { originalCreatedAt = it },
            updatedAt = now,
            entryDate = s.entryDate,
            pinned = s.pinned,
            color = s.color,
            folder = s.folder.trim().takeIf { s.type == EntryType.NOTE && it.isNotEmpty() },
        )
        val diaryMeta = if (s.type == EntryType.DIARY) {
            DiaryMeta(entryId = id, mood = s.mood, energy = s.energy, sleepQuality = s.sleepQuality)
        } else {
            null
        }
        val dreamMeta = if (s.type == EntryType.DREAM) {
            DreamMeta(
                entryId = id,
                clarity = s.clarity,
                lucid = s.lucid,
                nightmare = s.nightmare,
                recurring = s.recurring,
                emotions = s.emotions.joinToString(",").ifEmpty { null },
            )
        } else {
            null
        }
        entryRepository.save(entry, diaryMeta, dreamMeta)
        entryRepository.setTags(id, s.tags, if (s.type == EntryType.DREAM) s.symbols else emptyList())
        // Sync attachments: drop removed ones, add new ones (and remember their ids).
        val keep = s.attachments.mapNotNull { it.id }.toSet()
        (savedAttachmentIds - keep).forEach { entryRepository.deleteAttachment(it) }
        val added = s.attachments.filter { it.id == null }.associate { it.uri to entryRepository.addAttachment(id, it.uri, it.mimeType) }
        savedAttachmentIds = keep + added.values
        if (s.isNew || added.isNotEmpty()) {
            // Not through update(): this is no user edit (no undo step, no new autosave).
            _state.value = _state.value.let { cur ->
                cur.copy(isNew = false, attachments = cur.attachments.map { a -> if (a.id == null) a.copy(id = added[a.uri]) else a })
            }
        }
    }

    /** "Fertig": store now and close. */
    fun save() {
        viewModelScope.launch {
            persist()
            _saved.send(Unit)
        }
    }

    /** Hides the entry and closes; returns its id for the undo snackbar. */
    fun delete(): String? {
        closed = true
        val id = entryId
        if (id != null) entryRepository.hide(id)
        viewModelScope.launch { _saved.send(Unit) }
        return id
    }

    fun restore(id: String) = entryRepository.restore(id)
    fun commitDelete(id: String) = entryRepository.commitDelete(id)
}
