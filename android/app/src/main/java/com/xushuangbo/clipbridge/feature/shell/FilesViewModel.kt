package com.xushuangbo.clipbridge.feature.shell

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xushuangbo.clipbridge.core.files.FileTransferCoordinator
import com.xushuangbo.clipbridge.core.network.AuthApiException
import com.xushuangbo.clipbridge.core.network.FileAssetRecord
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

enum class FileDialogMode {
    Rename,
    Delete,
}

data class FilesUiState(
    val currentDeviceId: String = "",
    val files: List<FileAssetRecord> = emptyList(),
    val searchQuery: String = "",
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val totalFiles: Int = 0,
    val pageSize: Int = 20,
    val totalBytes: Long = 0L,
    val maxUploadBytes: Long = 0L,
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val downloadingFileId: String = "",
    val isSavingName: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val dialogMode: FileDialogMode? = null,
    val selectedFileId: String = "",
    val renameDraft: String = "",
)

class FilesViewModel(
    private val sessionStore: SessionStore,
    private val fileTransferCoordinator: FileTransferCoordinator,
) : ViewModel() {
    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    private val _sessionExitEvents = MutableSharedFlow<String>()
    val sessionExitEvents: SharedFlow<String> = _sessionExitEvents.asSharedFlow()
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private var hasLoadedOnce = false

    fun ensureLoaded() {
        if (hasLoadedOnce || _uiState.value.isLoading) {
            return
        }
        loadFilesPage(_uiState.value.currentPage)
    }

    fun refreshFiles() {
        loadFilesPage(_uiState.value.currentPage)
    }

    fun updateSearchQuery(value: String) {
        _uiState.update {
            it.copy(
                searchQuery = value,
                errorMessage = null,
            )
        }
    }

    fun loadPreviousPage() {
        val previousPage = _uiState.value.currentPage - 1
        if (previousPage < 1 || _uiState.value.isLoading) {
            return
        }
        loadFilesPage(previousPage)
    }

    fun loadNextPage() {
        val nextPage = _uiState.value.currentPage + 1
        if (nextPage > _uiState.value.totalPages || _uiState.value.isLoading) {
            return
        }
        loadFilesPage(nextPage)
    }

    fun uploadSelectedFile(uri: Uri) {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    errorMessage = null,
                )
            }

            try {
                fileTransferCoordinator.uploadPickedFile(
                    session = currentSession,
                    uri = uri,
                    maxUploadBytes = _uiState.value.maxUploadBytes,
                )

                // 新上传的文件一定会出现在最新一页顶部，
                // 所以这里直接回到第一页，避免用户还停在旧页看不到结果。
                reloadPageAfterMutation(
                    session = sessionStore.readSession(),
                    targetPage = 1,
                )
                _toastEvents.emit("文件已上传")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = error.message ?: "读取文件失败，请重试",
                    )
                }
            }
        }
    }

    fun downloadFileToUri(
        fileId: String,
        targetUri: Uri,
    ) {
        val currentSession = requireSession() ?: return
        val file = findFile(fileId)
        if (file == null) {
            _uiState.update { it.copy(errorMessage = "文件不存在或已被移除") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    downloadingFileId = fileId,
                    errorMessage = null,
                )
            }

            try {
                fileTransferCoordinator.downloadFileToUri(
                    session = currentSession,
                    fileId = fileId,
                    targetUri = targetUri,
                )
                _uiState.update {
                    it.copy(downloadingFileId = "")
                }
                _toastEvents.emit("已保存 ${file.originalName}")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        downloadingFileId = "",
                        errorMessage = error.message ?: "保存文件失败，请重试",
                    )
                }
            }
        }
    }

    fun openRenameDialog(fileId: String) {
        val file = findFile(fileId)
        if (file == null) {
            _uiState.update { it.copy(errorMessage = "文件不存在或已被移除") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = FileDialogMode.Rename,
                selectedFileId = file.id,
                renameDraft = file.originalName,
                errorMessage = null,
            )
        }
    }

    fun openDeleteDialog(fileId: String) {
        val file = findFile(fileId)
        if (file == null) {
            _uiState.update { it.copy(errorMessage = "文件不存在或已被移除") }
            return
        }

        _uiState.update {
            it.copy(
                dialogMode = FileDialogMode.Delete,
                selectedFileId = file.id,
                renameDraft = file.originalName,
                errorMessage = null,
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                dialogMode = null,
                selectedFileId = "",
                renameDraft = "",
            )
        }
    }

    fun updateRenameDraft(value: String) {
        _uiState.update { it.copy(renameDraft = value) }
    }

    fun renameSelectedFile() {
        val selectedFile = findSelectedFile()
        val normalizedName = _uiState.value.renameDraft.trim()

        if (selectedFile == null) {
            _uiState.update { it.copy(errorMessage = "文件不存在或已被移除") }
            return
        }

        val validationMessage = validateRenameDraft(normalizedName)
        if (validationMessage != null) {
            _uiState.update { it.copy(errorMessage = validationMessage) }
            return
        }

        val currentSession = requireSession() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSavingName = true,
                    errorMessage = null,
                )
            }

            try {
                val result = fileTransferCoordinator.renameFile(
                    session = currentSession,
                    fileId = selectedFile.id,
                    originalName = normalizedName,
                )

                val updatedFiles = _uiState.value.files.map { file ->
                    if (file.id == result.file.id) {
                        result.file
                    } else {
                        file
                    }
                }

                _uiState.update {
                    it.copy(
                        files = updatedFiles,
                        isSavingName = false,
                        dialogMode = null,
                        selectedFileId = "",
                        renameDraft = "",
                        errorMessage = null,
                    )
                }
                _toastEvents.emit("文件名已更新")
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isSavingName = false,
                        errorMessage = error.message ?: "重命名失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun deleteSelectedFile() {
        val selectedFile = findSelectedFile()
        if (selectedFile == null) {
            _uiState.update { it.copy(errorMessage = "文件不存在或已被移除") }
            return
        }

        val currentSession = requireSession() ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDeleting = true,
                    errorMessage = null,
                )
            }

            try {
                val result = fileTransferCoordinator.deleteFile(
                    session = currentSession,
                    fileId = selectedFile.id,
                )

                val targetPage = calculateTargetPageAfterDelete()
                reloadPageAfterMutation(
                    session = sessionStore.readSession(),
                    targetPage = targetPage,
                )
                _toastEvents.emit(
                    if (result.diskRemoved) {
                        "文件已删除"
                    } else {
                        "记录已删除，但磁盘清理失败"
                    },
                )
            } catch (error: AuthApiException) {
                handleRequestError(error)
            } catch (error: IOException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = error.message ?: "删除失败，请稍后重试",
                    )
                }
            }
        }
    }

    private fun loadFilesPage(page: Int) {
        val currentSession = requireSession() ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                loadFilesPageInternal(
                    session = currentSession,
                    page = page,
                    showLoading = false,
                )
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

    private suspend fun reloadPageAfterMutation(
        session: StoredSession,
        targetPage: Int,
    ) {
        loadFilesPageInternal(
            session = session,
            page = targetPage,
            showLoading = false,
        )
    }

    private suspend fun loadFilesPageInternal(
        session: StoredSession,
        page: Int,
        showLoading: Boolean,
    ) {
        if (showLoading) {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }
        }

        val result = fileTransferCoordinator.listFiles(
            session = session,
            page = page.coerceAtLeast(1),
            pageSize = DEFAULT_PAGE_SIZE,
        )

        hasLoadedOnce = true
        val safeTotalPages = result.pagination.totalPages.coerceAtLeast(1)
        val safePage = result.pagination.page.coerceIn(1, safeTotalPages)

        _uiState.update {
            it.copy(
                currentDeviceId = sessionStore.readSession().currentDeviceId,
                files = result.files,
                currentPage = safePage,
                totalPages = safeTotalPages,
                totalFiles = result.summary.totalFiles,
                pageSize = result.pagination.pageSize.coerceAtLeast(DEFAULT_PAGE_SIZE),
                totalBytes = result.summary.totalBytes,
                maxUploadBytes = result.summary.maxUploadBytes,
                isLoading = false,
                isUploading = false,
                downloadingFileId = "",
                isSavingName = false,
                isDeleting = false,
                errorMessage = null,
                dialogMode = null,
                selectedFileId = "",
                renameDraft = "",
            )
        }
    }

    private fun createInitialState(): FilesUiState {
        val session = sessionStore.readSession()
        return FilesUiState(
            currentDeviceId = session.currentDeviceId,
        )
    }

    private fun calculateTargetPageAfterDelete(): Int {
        val currentState = _uiState.value
        return if (currentState.files.size == 1 && currentState.currentPage > 1) {
            currentState.currentPage - 1
        } else {
            currentState.currentPage
        }
    }

    private fun validateRenameDraft(value: String): String? {
        return when {
            value.isBlank() -> "文件名不能为空"
            value.length > 128 -> "文件名最多 128 个字符"
            value.contains('/') || value.contains('\\') -> "文件名不能包含路径分隔符"
            else -> null
        }
    }

    private fun findFile(fileId: String): FileAssetRecord? {
        return _uiState.value.files.find { file -> file.id == fileId }
    }

    private fun findSelectedFile(): FileAssetRecord? {
        return findFile(_uiState.value.selectedFileId)
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
            _uiState.value = createInitialState()
            emitSessionExit("登录已失效，请重新登录")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                isUploading = false,
                downloadingFileId = "",
                isSavingName = false,
                isDeleting = false,
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
        private const val DEFAULT_PAGE_SIZE = 20

        fun factory(
            sessionStore: SessionStore,
            fileTransferCoordinator: FileTransferCoordinator,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FilesViewModel(
                        sessionStore = sessionStore,
                        fileTransferCoordinator = fileTransferCoordinator,
                    ) as T
                }
            }
        }
    }
}
