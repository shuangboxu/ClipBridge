package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.AccountProfileResult
import com.xushuangbo.clipbridge.core.network.AdminApiClient
import com.xushuangbo.clipbridge.core.network.AdminPrivilegeRequestRecord
import com.xushuangbo.clipbridge.core.network.ApproveBandwidthRequestInput
import com.xushuangbo.clipbridge.core.network.ApproveQuotaRequestInput
import com.xushuangbo.clipbridge.core.network.AuthApiClient
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.BandwidthRequestRecord
import com.xushuangbo.clipbridge.core.network.QuotaRequestRecord
import com.xushuangbo.clipbridge.core.network.ReviewNoteInput
import com.xushuangbo.clipbridge.core.network.TokenBundle
import com.xushuangbo.clipbridge.core.session.SessionStore
import com.xushuangbo.clipbridge.core.session.StoredSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

enum class AdminReviewType {
    Quota,
    Bandwidth,
    Admin,
}

enum class AdminReviewDialogMode {
    Approve,
    Reject,
}

data class AdminReviewsUiState(
    val quotaRequests: List<QuotaRequestRecord> = emptyList(),
    val bandwidthRequests: List<BandwidthRequestRecord> = emptyList(),
    val adminRequests: List<AdminPrivilegeRequestRecord> = emptyList(),
    val dialogMode: AdminReviewDialogMode? = null,
    val selectedReviewType: AdminReviewType? = null,
    val selectedRequestId: String = "",
    val approvedQuotaMbDraft: String = "",
    val approvedUploadKbpsDraft: String = "",
    val approvedDownloadKbpsDraft: String = "",
    val reviewNoteDraft: String = "",
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

class AdminReviewsViewModel(
    private val sessionStore: SessionStore,
    private val authApiClient: AuthApiClient,
    private val adminApiClient: AdminApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AdminReviewsUiState())
    val uiState: StateFlow<AdminReviewsUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoading) {
            return
        }
        refresh()
    }

    fun refresh() {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                loadQueues(currentSession)
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "网络异常，请稍后重试",
                    )
                }
            }
        }
    }

    fun openApproveQuotaDialog(requestId: String) {
        val request = _uiState.value.quotaRequests.find { it.id == requestId }
        if (request == null) {
            _uiState.update { it.copy(errorMessage = "申请不存在或已被处理") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = AdminReviewDialogMode.Approve,
                selectedReviewType = AdminReviewType.Quota,
                selectedRequestId = requestId,
                approvedQuotaMbDraft = (request.requestedQuotaBytes / MB).toString(),
                approvedUploadKbpsDraft = "",
                approvedDownloadKbpsDraft = "",
                reviewNoteDraft = "",
                errorMessage = null,
            )
        }
    }

    fun openApproveBandwidthDialog(requestId: String) {
        val request = _uiState.value.bandwidthRequests.find { it.id == requestId }
        if (request == null) {
            _uiState.update { it.copy(errorMessage = "申请不存在或已被处理") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = AdminReviewDialogMode.Approve,
                selectedReviewType = AdminReviewType.Bandwidth,
                selectedRequestId = requestId,
                approvedQuotaMbDraft = "",
                approvedUploadKbpsDraft = request.requestedUploadKbps.toString(),
                approvedDownloadKbpsDraft = request.requestedDownloadKbps.toString(),
                reviewNoteDraft = "",
                errorMessage = null,
            )
        }
    }

    fun openApproveAdminDialog(requestId: String) {
        val request = _uiState.value.adminRequests.find { it.id == requestId }
        if (request == null) {
            _uiState.update { it.copy(errorMessage = "申请不存在或已被处理") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = AdminReviewDialogMode.Approve,
                selectedReviewType = AdminReviewType.Admin,
                selectedRequestId = requestId,
                approvedQuotaMbDraft = "",
                approvedUploadKbpsDraft = "",
                approvedDownloadKbpsDraft = "",
                reviewNoteDraft = "",
                errorMessage = null,
            )
        }
    }

    fun openRejectDialog(
        type: AdminReviewType,
        requestId: String,
    ) {
        _uiState.update {
            it.copy(
                dialogMode = AdminReviewDialogMode.Reject,
                selectedReviewType = type,
                selectedRequestId = requestId,
                approvedQuotaMbDraft = "",
                approvedUploadKbpsDraft = "",
                approvedDownloadKbpsDraft = "",
                reviewNoteDraft = "",
                errorMessage = null,
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                dialogMode = null,
                selectedReviewType = null,
                selectedRequestId = "",
                approvedQuotaMbDraft = "",
                approvedUploadKbpsDraft = "",
                approvedDownloadKbpsDraft = "",
                reviewNoteDraft = "",
            )
        }
    }

    fun updateApprovedQuotaMbDraft(value: String) {
        _uiState.update { it.copy(approvedQuotaMbDraft = value, errorMessage = null) }
    }

    fun updateApprovedUploadKbpsDraft(value: String) {
        _uiState.update { it.copy(approvedUploadKbpsDraft = value, errorMessage = null) }
    }

    fun updateApprovedDownloadKbpsDraft(value: String) {
        _uiState.update { it.copy(approvedDownloadKbpsDraft = value, errorMessage = null) }
    }

    fun updateReviewNoteDraft(value: String) {
        _uiState.update { it.copy(reviewNoteDraft = value, errorMessage = null) }
    }

    fun submitDialog() {
        val currentSession = requireSession() ?: return
        val currentState = _uiState.value
        val reviewType = currentState.selectedReviewType
        val dialogMode = currentState.dialogMode
        val requestId = currentState.selectedRequestId.trim()

        if (reviewType == null || dialogMode == null || requestId.isBlank()) {
            _uiState.update { it.copy(errorMessage = "申请不存在或已被处理") }
            return
        }
        if (currentState.reviewNoteDraft.length > 500) {
            _uiState.update { it.copy(errorMessage = "审核备注不能超过 500 个字符") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    errorMessage = null,
                )
            }

            try {
                when (dialogMode) {
                    AdminReviewDialogMode.Approve -> approveRequest(
                        session = currentSession,
                        reviewType = reviewType,
                        requestId = requestId,
                    )
                    AdminReviewDialogMode.Reject -> rejectRequest(
                        session = currentSession,
                        reviewType = reviewType,
                        requestId = requestId,
                    )
                }

                refreshCurrentAccountSnapshot()
                loadQueues(sessionStore.readSession())
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        dialogMode = null,
                        selectedReviewType = null,
                        selectedRequestId = "",
                        approvedQuotaMbDraft = "",
                        approvedUploadKbpsDraft = "",
                        approvedDownloadKbpsDraft = "",
                        reviewNoteDraft = "",
                    )
                }
                _toastEvents.emit(
                    if (dialogMode == AdminReviewDialogMode.Approve) {
                        "申请已批准"
                    } else {
                        "申请已拒绝"
                    },
                )
            } catch (error: AuthApiException) {
                if (error.httpCode == 404 || error.httpCode == 409) {
                    loadQueues(sessionStore.readSession())
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            dialogMode = null,
                            selectedReviewType = null,
                            selectedRequestId = "",
                            errorMessage = "申请不存在或已被处理",
                        )
                    }
                } else {
                    handleRequestError(error)
                }
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = error.message ?: "操作失败，请稍后重试",
                    )
                }
            }
        }
    }

    private suspend fun approveRequest(
        session: StoredSession,
        reviewType: AdminReviewType,
        requestId: String,
    ) {
        when (reviewType) {
            AdminReviewType.Quota -> {
                val approvedQuotaMb = _uiState.value.approvedQuotaMbDraft.trim()
                val approvedQuotaValue = approvedQuotaMb.toLongOrNull()
                if (approvedQuotaMb.isNotBlank() && (approvedQuotaValue == null || approvedQuotaValue <= 0L)) {
                    throw AuthApiException(message = "批准后的配额必须是大于 0 的整数 MB")
                }
                val result = adminApiClient.approveQuotaRequest(
                    session = session,
                    requestId = requestId,
                    input = ApproveQuotaRequestInput(
                        approvedQuotaMb = approvedQuotaValue,
                        reviewNote = _uiState.value.reviewNoteDraft.trim(),
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
            }

            AdminReviewType.Bandwidth -> {
                val approvedUpload = _uiState.value.approvedUploadKbpsDraft.trim()
                val approvedDownload = _uiState.value.approvedDownloadKbpsDraft.trim()
                val approvedUploadValue = approvedUpload.toIntOrNull()
                val approvedDownloadValue = approvedDownload.toIntOrNull()
                if (approvedUpload.isNotBlank() && (approvedUploadValue == null || approvedUploadValue <= 0)) {
                    throw AuthApiException(message = "批准后的上传带宽必须是大于 0 的整数 Kbps")
                }
                if (approvedDownload.isNotBlank() && (approvedDownloadValue == null || approvedDownloadValue <= 0)) {
                    throw AuthApiException(message = "批准后的下载带宽必须是大于 0 的整数 Kbps")
                }
                val result = adminApiClient.approveBandwidthRequest(
                    session = session,
                    requestId = requestId,
                    input = ApproveBandwidthRequestInput(
                        approvedUploadKbps = approvedUploadValue,
                        approvedDownloadKbps = approvedDownloadValue,
                        reviewNote = _uiState.value.reviewNoteDraft.trim(),
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
            }

            AdminReviewType.Admin -> {
                val result = adminApiClient.approveAdminRequest(
                    session = session,
                    requestId = requestId,
                    input = ReviewNoteInput(
                        reviewNote = _uiState.value.reviewNoteDraft.trim(),
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
            }
        }
    }

    private suspend fun rejectRequest(
        session: StoredSession,
        reviewType: AdminReviewType,
        requestId: String,
    ) {
        when (reviewType) {
            AdminReviewType.Quota -> {
                val result = adminApiClient.rejectQuotaRequest(
                    session = session,
                    requestId = requestId,
                    input = ReviewNoteInput(
                        reviewNote = _uiState.value.reviewNoteDraft.trim(),
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
            }

            AdminReviewType.Bandwidth -> {
                val result = adminApiClient.rejectBandwidthRequest(
                    session = session,
                    requestId = requestId,
                    input = ReviewNoteInput(
                        reviewNote = _uiState.value.reviewNoteDraft.trim(),
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
            }

            AdminReviewType.Admin -> {
                val result = adminApiClient.rejectAdminRequest(
                    session = session,
                    requestId = requestId,
                    input = ReviewNoteInput(
                        reviewNote = _uiState.value.reviewNoteDraft.trim(),
                    ),
                    onRefreshing = null,
                )
                result.tokens?.let(sessionStore::updateTokens)
            }
        }
    }

    private suspend fun loadQueues(session: StoredSession) {
        val quotaResult = adminApiClient.listPendingQuotaRequests(
            session = session,
            onRefreshing = null,
        )
        quotaResult.tokens?.let(sessionStore::updateTokens)

        val bandwidthResult = adminApiClient.listPendingBandwidthRequests(
            session = sessionStore.readSession(),
            onRefreshing = null,
        )
        bandwidthResult.tokens?.let(sessionStore::updateTokens)

        val adminResult = adminApiClient.listPendingAdminRequests(
            session = sessionStore.readSession(),
            onRefreshing = null,
        )
        adminResult.tokens?.let(sessionStore::updateTokens)

        hasLoadedOnce = true
        _uiState.update {
            it.copy(
                quotaRequests = quotaResult.requests,
                bandwidthRequests = bandwidthResult.requests,
                adminRequests = adminResult.requests,
                isLoading = false,
                isSubmitting = false,
                errorMessage = null,
            )
        }
    }

    private suspend fun refreshCurrentAccountSnapshot() {
        val currentSession = sessionStore.readSession()
        val profile = authApiClient.getCurrentAccount(
            session = currentSession,
            onRefreshing = null,
        )
        saveCurrentAccountSnapshot(profile)
    }

    private fun saveCurrentAccountSnapshot(profile: AccountProfileResult) {
        val currentSession = sessionStore.readSession()
        val tokens = profile.tokens ?: TokenBundle(
            accessToken = currentSession.accessToken,
            refreshToken = currentSession.refreshToken,
        )

        sessionStore.saveAuthBundle(
            baseUrl = currentSession.baseUrl,
            username = profile.username,
            deviceName = currentSession.deviceName,
            currentDeviceId = profile.currentDeviceId,
            tokens = tokens,
            isAdmin = profile.isAdmin,
            storageQuotaBytes = profile.storageQuotaBytes,
            uploadBandwidthKbps = profile.uploadBandwidthKbps,
            downloadBandwidthKbps = profile.downloadBandwidthKbps,
        )
    }

    private fun requireSession(): StoredSession? {
        val currentSession = sessionStore.readSession()
        if (!currentSession.hasCompleteAuth()) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            emitSessionExit("登录已失效，请重新登录")
            return null
        }
        return currentSession
    }

    private fun handleRequestError(error: AuthApiException) {
        if (error.httpCode == 401) {
            sessionStore.clearClipboardSyncState()
            sessionStore.clearAuth()
            _uiState.value = AdminReviewsUiState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isSubmitting = false,
                errorMessage = error.message ?: "服务暂时不可用，请稍后重试",
            )
        }
    }

    private fun emitSessionExit(message: String) {
        viewModelScope.launch {
            _sessionExitEvents.emit(message)
        }
    }

    companion object {
        private const val MB = 1024L * 1024L

        fun factory(
            sessionStore: SessionStore,
            authApiClient: AuthApiClient,
            adminApiClient: AdminApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AdminReviewsViewModel(
                        sessionStore = sessionStore,
                        authApiClient = authApiClient,
                        adminApiClient = adminApiClient,
                    ) as T
                }
            }
        }
    }
}
