package app.tenet.android.core.common

import java.util.UUID

/**
 * All entities use string UUIDs as primary keys so a later cloud sync
 * does not require an ID migration (see App_Konzept.md, offene Entscheidung 3).
 */
fun newUuid(): String = UUID.randomUUID().toString()
