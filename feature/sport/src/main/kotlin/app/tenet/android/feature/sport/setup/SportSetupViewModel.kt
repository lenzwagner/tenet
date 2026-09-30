package app.tenet.android.feature.sport.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.datastore.UserSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which disciplines still show the "Jetzt einrichten" card (null while loading). */
@HiltViewModel
class SportSetupViewModel @Inject constructor(
    private val settings: UserSettingsRepository,
) : ViewModel() {
    val done: StateFlow<Set<String>?> = settings.settings.map { it.sportSetupDone }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun skip(discipline: String) {
        viewModelScope.launch { settings.markSportSetupDone(discipline) }
    }
}
