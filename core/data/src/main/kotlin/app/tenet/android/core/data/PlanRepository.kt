package app.tenet.android.core.data

import app.tenet.android.core.database.dao.SportDao
import app.tenet.android.core.database.entity.Discipline
import app.tenet.android.core.database.entity.TrainingPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Switching between the plans of a discipline: a new plan only deactivates
 * the old one, so earlier plans can be picked again. Statistics never depend
 * on the plan – they read every session of the discipline.
 */
@Singleton
class PlanRepository @Inject constructor(
    private val sportDao: SportDao,
) {
    fun observePlans(discipline: Discipline): Flow<List<TrainingPlan>> = sportDao.observePlans(discipline)

    suspend fun activate(discipline: Discipline, planId: String) = sportDao.activatePlan(discipline, planId)
}
