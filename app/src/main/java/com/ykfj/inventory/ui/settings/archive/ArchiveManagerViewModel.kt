package com.ykfj.inventory.ui.settings.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayRecordDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganGroupDao
import com.ykfj.inventory.data.local.db.dao.SoldRecordDao
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.usecase.archive.ArchivableRecordType
import com.ykfj.inventory.domain.usecase.archive.ExportArchiveUseCase
import com.ykfj.inventory.domain.usecase.archive.PurgeArchivedRecordsUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ArchiveManagerUiState(
    val type: ArchivableRecordType = ArchivableRecordType.SOLD,
    /** Inclusive start of day. */
    val startMillis: Long = startOfMonth(),
    /** Inclusive end of day (23:59:59.999). */
    val endMillis: Long = endOfDay(System.currentTimeMillis()),
    val previewCount: Int = 0,
    val isAdmin: Boolean = false,
    val isWorking: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
) {
    val rangeIsValid: Boolean get() = startMillis <= endMillis
}

@HiltViewModel
class ArchiveManagerViewModel @Inject constructor(
    private val soldDao: SoldRecordDao,
    private val layawayDao: LayawayRecordDao,
    private val damagedDao: DamagedRecordDao,
    private val groupDao: PaluwaganGroupDao,
    private val exportArchive: ExportArchiveUseCase,
    private val purgeArchive: PurgeArchivedRecordsUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ArchiveManagerUiState())
    val state: StateFlow<ArchiveManagerUiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(isAdmin = sessionManager.currentUser.value?.role == UserRole.ADMIN)
        }
        refreshPreview()
    }

    fun setType(type: ArchivableRecordType) {
        _state.update { it.copy(type = type) }
        refreshPreview()
    }

    fun setStartDate(millis: Long) {
        _state.update { it.copy(startMillis = startOfDay(millis)) }
        refreshPreview()
    }

    fun setEndDate(millis: Long) {
        _state.update { it.copy(endMillis = endOfDay(millis)) }
        refreshPreview()
    }

    fun consumeMessages() {
        _state.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun export(thenPurge: Boolean) {
        val s = _state.value
        if (!s.rangeIsValid || s.isWorking) return
        if (thenPurge && !s.isAdmin) {
            _state.update { it.copy(errorMessage = "Only admin can purge archives") }
            return
        }
        val actorId = sessionManager.currentUser.value?.id ?: return

        _state.update { it.copy(isWorking = true, infoMessage = null, errorMessage = null) }
        viewModelScope.launch {
            val result = exportArchive(s.type, s.startMillis, s.endMillis)
            when (result) {
                is ExportArchiveUseCase.Result.NoRecords -> {
                    _state.update {
                        it.copy(isWorking = false, errorMessage = "No archived records in that range")
                    }
                }
                is ExportArchiveUseCase.Result.WriteFailed -> {
                    _state.update {
                        it.copy(isWorking = false, errorMessage = "Export failed: ${result.message}")
                    }
                }
                is ExportArchiveUseCase.Result.Success -> {
                    if (thenPurge) {
                        val deleted = purgeArchive(s.type, s.startMillis, s.endMillis, actorId)
                        _state.update {
                            it.copy(
                                isWorking = false,
                                infoMessage = "Exported ${result.rowCount} → ${result.fileName}; purged $deleted",
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                isWorking = false,
                                infoMessage = "Exported ${result.rowCount} → ${result.fileName}",
                            )
                        }
                    }
                    refreshPreview()
                }
            }
        }
    }

    private fun refreshPreview() {
        val s = _state.value
        if (!s.rangeIsValid) {
            _state.update { it.copy(previewCount = 0) }
            return
        }
        viewModelScope.launch {
            val count = when (s.type) {
                ArchivableRecordType.SOLD ->
                    soldDao.getArchivedInRange(s.startMillis, s.endMillis).size
                ArchivableRecordType.LAYAWAY ->
                    layawayDao.getArchivedInRange(s.startMillis, s.endMillis).size
                ArchivableRecordType.DAMAGED ->
                    damagedDao.getArchivedInRange(s.startMillis, s.endMillis).size
                ArchivableRecordType.PALUWAGAN ->
                    groupDao.getArchivedInRange(s.startMillis, s.endMillis).size
            }
            _state.update { it.copy(previewCount = count) }
        }
    }
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun startOfMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
