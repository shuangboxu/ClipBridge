package com.xushuangbo.clipbridge.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.network.PublicShareApiClient
import com.xushuangbo.clipbridge.core.network.PublicShareFileRecord
import com.xushuangbo.clipbridge.core.network.PublicShareOpenResult
import com.xushuangbo.clipbridge.core.network.PublicShareRecord
import com.xushuangbo.clipbridge.core.share.PublicShareCrypto
import com.xushuangbo.clipbridge.core.share.PublicShareLinkBuilder
import com.xushuangbo.clipbridge.core.share.PublicShareLinkParser
import com.xushuangbo.clipbridge.core.session.SessionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

data class ScanShareUiState(
    val serviceAddress: String = "",
    val rawScannedText: String = "",
    val shareToken: String = "",
    val publicLink: String = "",
    val password: String = "",
    val share: PublicShareRecord? = null,
    val textContent: String = "",
    val contentOpen: Boolean = false,
    val isResolving: Boolean = false,
    val isOpening: Boolean = false,
    val errorMessage: String? = null,
) {
    val hasShareContent: Boolean
        get() = share?.let { it.hasTextContent || it.hasFileContent } == true

    val files: List<PublicShareFileRecord>
        get() = share?.files.orEmpty()
}

class ScanShareViewModel(
    private val sessionStore: SessionStore,
    private val publicShareApiClient: PublicShareApiClient,
) : ViewModel() {
    private var countdownJob: Job? = null

    private val _uiState = MutableStateFlow(
        ScanShareUiState(
            serviceAddress = sessionStore.readSession().baseUrl,
        ),
    )
    val uiState: StateFlow<ScanShareUiState> = _uiState.asStateFlow()

    fun resolveScannedText(rawValue: String) {
        if (_uiState.value.isResolving || _uiState.value.isOpening) {
            return
        }

        val baseUrl = sessionStore.readSession().baseUrl.trim()
        if (baseUrl.isBlank()) {
            stopCountdown()
            _uiState.update {
                it.copy(errorMessage = "当前没有可用的服务地址，请先登录并检查连接配置")
            }
            return
        }

        val token = PublicShareLinkParser.extractToken(rawValue)
        if (token.isNullOrBlank()) {
            stopCountdown()
            _uiState.update {
                it.copy(
                    rawScannedText = rawValue.trim(),
                    shareToken = "",
                    publicLink = "",
                    share = null,
                    textContent = "",
                    contentOpen = false,
                    errorMessage = "当前二维码不是 ClipBridge 分享链接",
                )
            }
            return
        }

        val publicLink = PublicShareLinkBuilder.build(baseUrl, token).orEmpty()
        stopCountdown()
        _uiState.update {
            it.copy(
                serviceAddress = baseUrl,
                rawScannedText = rawValue.trim(),
                shareToken = token,
                publicLink = publicLink,
                password = "",
                share = null,
                textContent = "",
                contentOpen = false,
                isResolving = true,
                isOpening = false,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val shareMeta = publicShareApiClient.getMeta(baseUrl = baseUrl, token = token)
                _uiState.update {
                    it.copy(
                        share = shareMeta,
                        isResolving = false,
                        errorMessage = null,
                    )
                }

                if (canAutoOpen(shareMeta)) {
                    openShare()
                }
            } catch (error: IOException) {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        share = null,
                        textContent = "",
                        contentOpen = false,
                        isResolving = false,
                        errorMessage = error.message ?: "读取分享信息失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun updatePassword(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                errorMessage = null,
            )
        }
    }

    fun openShare() {
        val currentState = _uiState.value
        val baseUrl = currentState.serviceAddress.trim()
        val shareToken = currentState.shareToken.trim()
        if (baseUrl.isBlank() || shareToken.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "请先完成一次有效扫码")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isOpening = true,
                    errorMessage = null,
                )
            }

            try {
                val previousShare = currentState.share
                val result = publicShareApiClient.openShare(
                    baseUrl = baseUrl,
                    token = shareToken,
                    password = _uiState.value.password.trim(),
                )

                val decryptedText = when {
                    !result.share.hasTextContent -> ""
                    result.share.isEncrypted -> {
                        PublicShareCrypto.decryptText(
                            encryptedPayload = result.encryptedPayload,
                            encryption = result.encryption ?: error("缺少加密元数据"),
                            password = _uiState.value.password.trim(),
                        )
                    }
                    else -> result.textContent
                }

                val mergedShare = mergeShareState(previousShare, result)
                val openedShare = attachDisplayCountdown(previousShare, mergedShare)

                _uiState.update { currentUiState ->
                    currentUiState.copy(
                        share = openedShare,
                        textContent = decryptedText,
                        contentOpen = true,
                        isOpening = false,
                        errorMessage = null,
                    )
                }
                syncCountdown(openedShare)
            } catch (error: Exception) {
                stopCountdown()
                _uiState.update {
                    it.copy(
                        textContent = "",
                        contentOpen = false,
                        isOpening = false,
                        errorMessage = error.message ?: "打开分享失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun clearResult() {
        stopCountdown()
        _uiState.value = ScanShareUiState(
            serviceAddress = sessionStore.readSession().baseUrl,
        )
    }

    fun showUiError(message: String) {
        if (message.isBlank()) {
            return
        }

        _uiState.update {
            it.copy(errorMessage = message)
        }
    }

    override fun onCleared() {
        stopCountdown()
        super.onCleared()
    }

    private fun mergeShareState(
        previous: PublicShareRecord?,
        current: PublicShareOpenResult,
    ): PublicShareRecord {
        val currentShare = current.share
        return PublicShareRecord(
            token = currentShare.token.ifBlank { previous?.token.orEmpty() },
            status = currentShare.status.ifBlank { previous?.status.orEmpty() },
            contentKind = currentShare.contentKind.ifBlank { previous?.contentKind.orEmpty() },
            hasTextContent = currentShare.hasTextContent || previous?.hasTextContent == true,
            hasFileContent = currentShare.hasFileContent || previous?.hasFileContent == true,
            isEncrypted = currentShare.isEncrypted || previous?.isEncrypted == true,
            requiresPassword = currentShare.requiresPassword,
            textPreview = previous?.textPreview.orEmpty(),
            allowCopyContent = currentShare.allowCopyContent,
            files = if (currentShare.files.isNotEmpty()) currentShare.files else previous?.files.orEmpty(),
            burnMode = currentShare.burnMode.ifBlank { previous?.burnMode.orEmpty() },
            burnAfterSeconds = currentShare.burnAfterSeconds,
            remainingSeconds = currentShare.remainingSeconds,
            expiresAt = currentShare.expiresAt.ifBlank { previous?.expiresAt.orEmpty() },
            firstOpenedAt = currentShare.firstOpenedAt.ifBlank { previous?.firstOpenedAt.orEmpty() },
            burnDeadline = currentShare.burnDeadline.ifBlank { previous?.burnDeadline.orEmpty() },
            consumedAt = currentShare.consumedAt.ifBlank { previous?.consumedAt.orEmpty() },
            revokedAt = currentShare.revokedAt.ifBlank { previous?.revokedAt.orEmpty() },
            openCount = currentShare.openCount,
            createdAt = currentShare.createdAt.ifBlank { previous?.createdAt.orEmpty() },
        )
    }

    // Web 端会把“真正展示给用户”的倒计时和服务端状态拆开处理，
    // 安卓端这里沿用同样思路，避免首次打开时网络/解密耗时把几秒钟直接吃掉。
    private fun attachDisplayCountdown(
        previous: PublicShareRecord?,
        current: PublicShareRecord,
    ): PublicShareRecord {
        if (!current.burnMode.equals("countdown", ignoreCase = true)) {
            return current
        }

        val displaySeconds = resolveDisplayCountdownSeconds(previous, current)
        if (displaySeconds <= 0L) {
            return current.copy(remainingSeconds = 0L)
        }

        return current.copy(remainingSeconds = displaySeconds)
    }

    private fun resolveDisplayCountdownSeconds(
        previous: PublicShareRecord?,
        current: PublicShareRecord,
    ): Long {
        if (!current.burnMode.equals("countdown", ignoreCase = true)) {
            return 0L
        }

        val configuredSeconds = current.burnAfterSeconds.coerceAtLeast(0).toLong()
        val remainingSeconds = current.remainingSeconds.coerceAtLeast(0L)
        val hadBurnDeadline = previous?.burnDeadline?.isNotBlank() == true

        return if (hadBurnDeadline) {
            remainingSeconds
        } else {
            maxOf(configuredSeconds, remainingSeconds)
        }
    }

    // 倒计时分享在安卓端打开后，需要本地继续走秒并在到点后隐藏内容，
    // 否则用户第一次扫码打开后，界面上会一直保留已过期内容。
    private fun syncCountdown(share: PublicShareRecord) {
        stopCountdown()

        if (!share.burnMode.equals("countdown", ignoreCase = true)) {
            return
        }

        val initialRemainingSeconds = share.remainingSeconds.coerceAtLeast(0L)
        if (initialRemainingSeconds <= 0L) {
            handleCountdownFinished()
            return
        }

        countdownJob = viewModelScope.launch {
            var remainingSeconds = initialRemainingSeconds
            while (isActive && remainingSeconds > 0L) {
                delay(1000)
                remainingSeconds -= 1

                if (remainingSeconds > 0L) {
                    _uiState.update { currentState ->
                        val currentShare = currentState.share ?: return@update currentState
                        currentState.copy(
                            share = currentShare.copy(remainingSeconds = remainingSeconds),
                        )
                    }
                } else {
                    handleCountdownFinished()
                }
            }
        }
    }

    private fun handleCountdownFinished() {
        stopCountdown()
        _uiState.update { currentState ->
            val currentShare = currentState.share ?: return@update currentState
            currentState.copy(
                share = currentShare.copy(
                    status = "consumed",
                    remainingSeconds = 0L,
                ),
                textContent = "",
                contentOpen = false,
                isOpening = false,
                errorMessage = "分享倒计时已结束，内容已自动隐藏。",
            )
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun canAutoOpen(share: PublicShareRecord): Boolean {
        return share.status.equals("active", ignoreCase = true) &&
            !share.requiresPassword &&
            (share.hasTextContent || share.hasFileContent)
    }

    companion object {
        fun factory(
            sessionStore: SessionStore,
            publicShareApiClient: PublicShareApiClient,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ScanShareViewModel(
                        sessionStore = sessionStore,
                        publicShareApiClient = publicShareApiClient,
                    ) as T
                }
            }
        }
    }
}
