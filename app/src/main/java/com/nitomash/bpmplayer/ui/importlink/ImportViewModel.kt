package com.nitomash.bpmplayer.ui.importlink

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nitomash.bpmplayer.R
import com.nitomash.bpmplayer.data.music.FolderScanner
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRoot
import com.nitomash.bpmplayer.domain.linkimport.AppLibraryRootRepository
import com.nitomash.bpmplayer.domain.linkimport.ImportJobId
import com.nitomash.bpmplayer.domain.linkimport.ImportJobState
import com.nitomash.bpmplayer.domain.linkimport.ImportPreview
import com.nitomash.bpmplayer.domain.linkimport.ImportRequest
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkException
import com.nitomash.bpmplayer.domain.linkimport.PublicLinkImportUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NAME_LOOKUP_DELAY_MS = 700L

data class ImportUiState(
    val loaded: Boolean = false,
    val link: String = "",
    val folderName: String = "",
    val root: AppLibraryRoot? = null,
    val sourceName: String? = null,
    val preview: ImportPreview? = null,
    val jobState: ImportJobState? = null,
    val isInspecting: Boolean = false,
    @androidx.annotation.StringRes val errorRes: Int? = null,
    val errorMessage: String? = null,
) {
    val isRunning: Boolean
        get() = when (jobState) {
            null,
            is ImportJobState.Completed,
            is ImportJobState.Cancelled,
            is ImportJobState.Failed,
            -> false

            is ImportJobState.Queued,
            is ImportJobState.Planning,
            is ImportJobState.Downloading,
            is ImportJobState.Indexing,
            -> true
        }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importUseCase: PublicLinkImportUseCase,
    private val rootRepository: AppLibraryRootRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var activeJobId: ImportJobId? = null
    private var jobObserver: Job? = null
    private var nameLookupJob: Job? = null
    private var folderNameAutoFilled = false
    private var lastResolvedLink: String? = null

    init {
        viewModelScope.launch {
            try {
                val root = rootRepository.getRoot()
                val requested = savedStateHandle.get<String>(IMPORT_ARG_JOB_ID)?.let(::ImportJobId)
                val job = importUseCase.resume(requested)
                val initialState = job?.let { importUseCase.observe(it.id).firstOrNull() }
                activeJobId = job?.id
                folderNameAutoFilled = job != null && job.targetFolderName == null
                lastResolvedLink = job?.link?.takeIf { folderNameAutoFilled }
                _uiState.value = _uiState.value.copy(
                    root = root,
                    link = job?.link.orEmpty(),
                    folderName = job?.targetFolderName ?: job?.sourceName.orEmpty(),
                    sourceName = job?.sourceName,
                    jobState = initialState,
                )
                if (job != null) observe(job.id)
            } catch (error: Exception) {
                setError(R.string.import_error_link, error.message)
            } finally {
                _uiState.value = _uiState.value.copy(loaded = true)
            }
        }
    }

    fun onLinkChange(value: String) {
        val current = _uiState.value
        lastResolvedLink = null
        _uiState.value = current.copy(
            link = value,
            sourceName = null,
            preview = null,
            folderName = if (folderNameAutoFilled) "" else current.folderName,
            errorRes = null,
            errorMessage = null,
        )
        nameLookupJob?.cancel()
        nameLookupJob = null
        if (value.isBlank()) return
        nameLookupJob = viewModelScope.launch {
            delay(NAME_LOOKUP_DELAY_MS)
            resolveName(value.trim())
        }
    }

    fun onLinkFocusLost() {
        val link = _uiState.value.link.trim()
        nameLookupJob?.cancel()
        nameLookupJob = null
        if (link.isBlank()) return
        nameLookupJob = viewModelScope.launch { resolveName(link) }
    }

    fun onFolderNameChange(value: String) {
        folderNameAutoFilled = false
        _uiState.value = _uiState.value.copy(folderName = value)
    }

    fun onRootPicked(uri: Uri) {
        viewModelScope.launch {
            val root = rootRepository.saveRoot(uri.toString(), FolderScanner.displayName(uri))
            _uiState.value = _uiState.value.copy(root = root, errorRes = null, errorMessage = null)
        }
    }

    fun start() {
        val state = _uiState.value
        val link = state.link.trim()
        if (link.isBlank()) {
            setError(R.string.import_error_link)
            return
        }
        if (state.root == null) {
            setError(R.string.import_error_root)
            return
        }
        nameLookupJob?.cancel()
        nameLookupJob = null
        jobObserver?.cancel()
        jobObserver = null
        activeJobId = null
        _uiState.value = state.copy(
            preview = null,
            jobState = null,
            isInspecting = true,
            errorRes = null,
            errorMessage = null,
        )
        viewModelScope.launch {
            val preview = resolvePreview(link)
            if (preview == null) {
                _uiState.value = _uiState.value.copy(isInspecting = false)
                return@launch
            }
            val currentState = _uiState.value
            val folderName = currentState.folderName.ifBlank { preview.sourceName }
            if (folderName != currentState.folderName) folderNameAutoFilled = true
            _uiState.value = currentState.copy(
                sourceName = preview.sourceName,
                preview = preview,
                folderName = folderName,
                isInspecting = false,
            )
            runCatching {
                importUseCase.enqueue(
                    ImportRequest(
                        link = link,
                        targetFolderName = folderName.trim().ifBlank { null },
                    ),
                )
            }.onSuccess { jobId ->
                activeJobId = jobId
                _uiState.value = _uiState.value.copy(jobState = ImportJobState.Queued)
                observe(jobId)
            }.onFailure { error ->
                setError(error.toErrorRes(), error.message)
            }
        }
    }

    fun cancel() {
        val jobId = activeJobId ?: return
        viewModelScope.launch {
            runCatching { importUseCase.cancel(jobId) }
                .onFailure { error -> setError(error.toErrorRes(), error.message) }
        }
    }

    private fun observe(jobId: ImportJobId) {
        jobObserver?.cancel()
        jobObserver = viewModelScope.launch {
            importUseCase.observe(jobId)
                .cancellable()
                .collect { state ->
                    _uiState.value = _uiState.value.copy(jobState = state)
                    if (state is ImportJobState.Completed ||
                        state is ImportJobState.Cancelled ||
                        state is ImportJobState.Failed
                    ) {
                        activeJobId = null
                        jobObserver?.cancel()
                    }
                }
        }
    }

    private suspend fun resolveName(link: String) {
        if (lastResolvedLink == link && _uiState.value.sourceName != null) return
        val result = runCatching { importUseCase.resolveName(link) }
        val name = result.getOrNull()
        if (name.isNullOrBlank()) {
            val error = result.exceptionOrNull()
            if (error != null) setError(error.toErrorRes(), error.message)
            return
        }
        val current = _uiState.value
        if (current.link.trim() != link) return
        val shouldFill = current.folderName.isBlank() || folderNameAutoFilled
        lastResolvedLink = link
        _uiState.value = current.copy(
            sourceName = name,
            folderName = if (shouldFill) name else current.folderName,
            errorRes = null,
            errorMessage = null,
        )
        if (shouldFill) folderNameAutoFilled = true
    }

    private suspend fun resolvePreview(link: String): ImportPreview? {
        val result = runCatching { importUseCase.inspect(link) }
        val preview = result.getOrNull()
        when {
            preview == null -> {
                val error = result.exceptionOrNull()
                setError(error?.toErrorRes() ?: R.string.import_error_link, error?.message)
            }

            preview.supportedFileCount == 0 -> setError(R.string.import_error_no_files)
        }
        return preview?.takeIf { it.supportedFileCount > 0 }
    }

    private fun setError(errorRes: Int, message: String? = null) {
        _uiState.value = _uiState.value.copy(
            errorRes = errorRes,
            errorMessage = message,
        )
    }

    private fun Throwable.toErrorRes(): Int = when (this) {
        is PublicLinkException.UnsupportedLink -> R.string.import_error_unsupported
        is PublicLinkException.LinkNotFound,
        is PublicLinkException.LinkNotPublic,
        is PublicLinkException.AccessDenied,
        is PublicLinkException.RateLimited,
        is PublicLinkException.Network,
        is PublicLinkException.Protocol,
        is PublicLinkException.LimitExceeded,
        -> R.string.import_error_link

        is PublicLinkException.RootMissing -> R.string.import_error_root
        else -> R.string.import_error_link
    }
}
