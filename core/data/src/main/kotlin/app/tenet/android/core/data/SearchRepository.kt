package app.tenet.android.core.data

import app.tenet.android.core.database.entity.Recipe
import app.tenet.android.core.database.dao.EntryDao
import app.tenet.android.core.database.dao.FoodDao
import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Entry
import app.tenet.android.core.database.entity.Exercise
import app.tenet.android.core.database.entity.FoodLog
import app.tenet.android.core.common.FtsRank
import javax.inject.Inject
import javax.inject.Singleton

/** Grouped result of the global search (App_Konzept.md 6 "Globale Suche"). */
data class SearchResults(
    val entries: List<Entry> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val foods: List<FoodLog> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty() && exercises.isEmpty() && foods.isEmpty() && recipes.isEmpty()
}

/**
 * Searches notes, diary, dreams (incl. tags and dream symbols), exercises and
 * logged meals. Journal entries go through the FTS4 index (prefix terms,
 * relevance ranked, title hits count triple); the small catalog tables use LIKE.
 */
@Singleton
class SearchRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val sportDao: SportDao,
    private val foodDao: FoodDao,
) {
    suspend fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.length < 2) return SearchResults()
        val pattern = "%$q%"
        return SearchResults(
            entries = searchEntries(q, pattern),
            exercises = sportDao.searchExercises(pattern),
            // One hit per distinct meal name, newest first.
            foods = foodDao.searchLogs(pattern).distinctBy { it.label.lowercase() },
            recipes = foodDao.searchRecipes(pattern),
        )
    }

    private suspend fun searchEntries(query: String, pattern: String): List<Entry> {
        val match = FtsRank.matchExpression(query)
        val ranked = if (match == null) {
            emptyList()
        } else {
            runCatching { entryDao.searchFts(match) }
                .getOrElse { return entryDao.search(pattern) } // malformed MATCH → plain LIKE
                .map { it.entry to FtsRank.score(FtsRank.decode(it.info), TITLE_BODY_WEIGHTS) }
                .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenByDescending { it.first.updatedAt })
                .map { it.first }
        }
        val byTag = entryDao.searchByTag(pattern)
        return (ranked + byTag).distinctBy { it.id }.take(50)
    }

    private companion object {
        val TITLE_BODY_WEIGHTS = doubleArrayOf(3.0, 1.0)
    }
}
