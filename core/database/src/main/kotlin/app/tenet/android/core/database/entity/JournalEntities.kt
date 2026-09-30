package app.tenet.android.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** GENERAL = normal tag, DREAM_SYMBOL = dream symbol (person, place, object). */
enum class TagKind { GENERAL, DREAM_SYMBOL }

/** Cross-module tag (App_Konzept.md 6 / 7.3). Unique per name and kind. */
@Entity(
    tableName = "Tag",
    indices = [Index(value = ["name", "kind"], unique = true)],
)
data class Tag(
    @PrimaryKey val id: String,
    val name: String,
    val kind: TagKind,
)

@Entity(
    tableName = "EntryTag",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class EntryTag(
    val entryId: String,
    val tagId: String,
)

/** Image attached to an entry; [uri] is a persisted content:// grant. */
@Entity(
    tableName = "Attachment",
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class Attachment(
    @PrimaryKey val id: String,
    val entryId: String,
    val uri: String,
    val mimeType: String,
    val createdAt: Long,
)
