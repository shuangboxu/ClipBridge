package com.xushuangbo.clipbridge.feature.bootstrap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.session.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BootstrapViewModel(
    private val bootstrapCoordinator: BootstrapCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow<BootstrapState>(BootstrapState.LoadingLocal)
    val state: StateFlow<BootstrapState> = _state.asStateFlow()

    init {
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _state.value = bootstrapCoordinator.run { intermediateState ->
                _state.value = intermediateState
            }
        }
    }

    companion object {
        fun factory(
            sessionStore: SessionStore,
            authApiClient: AuthApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BootstrapViewModel(
                        bootstrapCoordinator = BootstrapCoordinator(
                            sessionStore = sessionStore,
                            authApiClient = authApiClient,
                        ),
                    ) as T
                }
            }
        }
    }
}
