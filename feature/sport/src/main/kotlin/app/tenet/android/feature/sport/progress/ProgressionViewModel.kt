package app.tenet.android.feature.sport.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tenet.android.core.data.ProgressionData
import app.tenet.android.core.data.ProgressionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProgressionViewModel @Inject constructor(
    repository: ProgressionRepository,
) : ViewModel() {
    val state: StateFlow<ProgressionData?> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
