package com.agendabarber.app.cache

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agendabarber.app.network.AppointmentView
import com.agendabarber.app.ui.apiMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AgendaUiState(
    val loading: Boolean = false,
    val appointments: List<AppointmentView> = emptyList(),
    val error: String = "",
    val offline: Boolean = false,
    val lastSyncedAt: Long? = null
)

class AgendaViewModel(private val repository: AgendaRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AgendaUiState(loading = true))
    val state: StateFlow<AgendaUiState> = mutableState
    private var loadJob: Job? = null

    fun refresh(token: String, day: String, period: String, status: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(loading = true, error = "")
            try {
                val loaded = repository.appointments(token, day, period, status)
                mutableState.value = AgendaUiState(
                    appointments = loaded.appointments,
                    offline = loaded.fromCache,
                    lastSyncedAt = loaded.synchronizedAt
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = AgendaUiState(error = apiMessage(error))
            }
        }
    }

    class Factory(private val repository: AgendaRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AgendaViewModel::class.java))
            return AgendaViewModel(repository) as T
        }
    }
}
