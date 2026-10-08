package app.tenet.android.feature.journal

/**
 * Built-in note backgrounds: 20 beach, sea and island photos (Unsplash licence,
 * free to use without attribution; sources in feature/journal/docs/note-backgrounds.md).
 * A chosen one is stored as an attachment with [MIME] and the uri "tenet-bg:NN".
 */
internal object NoteBackgrounds {
    const val MIME = "background/preset"
    private const val PREFIX = "tenet-bg:"

    val all: List<Int> = listOf(
        R.drawable.note_bg_01, R.drawable.note_bg_02, R.drawable.note_bg_03, R.drawable.note_bg_04,
        R.drawable.note_bg_05, R.drawable.note_bg_06, R.drawable.note_bg_07, R.drawable.note_bg_08,
        R.drawable.note_bg_09, R.drawable.note_bg_10, R.drawable.note_bg_11, R.drawable.note_bg_12,
        R.drawable.note_bg_13, R.drawable.note_bg_14, R.drawable.note_bg_15, R.drawable.note_bg_16,
        R.drawable.note_bg_17, R.drawable.note_bg_18, R.drawable.note_bg_19, R.drawable.note_bg_20,
    )

    fun uri(index: Int) = PREFIX + (index + 1).toString().padStart(2, '0')

    /** Index of a preset uri, null for anything else. */
    fun indexOf(uri: String): Int? = uri.removePrefix(PREFIX).takeIf { uri.startsWith(PREFIX) }?.toIntOrNull()?.minus(1)?.takeIf { it in all.indices }

    /**
     * The note's wallpaper, one at a time: a chosen built-in photo, else (without a
     * colour) the note's own first photo. Same in the tile and the open note.
     */
    fun wallpaperUri(uris: List<Pair<String, String>>, color: Int?): String? =
        uris.firstOrNull { it.second == MIME }?.first
            ?: if (color == null) uris.firstOrNull { it.second.startsWith("image") }?.first else null

    /** What Coil loads for an attachment uri: the drawable for presets, else the uri itself. */
    fun model(uri: String): Any = indexOf(uri)?.let { all[it] } ?: uri
}
