package com.ykfj.inventory.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import java.util.Calendar
import javax.inject.Inject
import kotlin.OptIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Exposes the two red-alert badge counts shown on the sidebar:
 * - Layaway: ACTIVE records whose due_date is strictly in the past.
 * - Paluwagan: UNPAID payments whose computed due-date falls within today.
 *
 * Both counts are time-derived, so this VM ticks every [TICK_INTERVAL_MS]
 * to refresh the SQL window — without the tick, a row that becomes overdue
 * by clock advance alone would never trigger a Flow emission.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SidebarBadgeViewModel @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val paluwaganRepository: PaluwaganRepository,
) : ViewModel() {

    /** Emits the current wall-clock time once per [TICK_INTERVAL_MS]. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_INTERVAL_MS)
        }
    }

    val layawayOverdueCount: StateFlow<Int> = ticker
        .flatMapLatest { now -> layawayRepository.observeOverdueCount(now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    val paluwaganDueTodayCount: StateFlow<Int> = ticker
        .flatMapLatest { now ->
            val (start, end) = todayBoundsMillis(now)
            paluwaganRepository.observeDueTodayCount(start, end)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    /** Returns local-day [start, end] epoch ms for the day containing [now]. */
    private fun todayBoundsMillis(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = start + DAY_MS - 1
        return start to end
    }

    private companion object {
        /** Refresh cadence — matches the idle-timeout check in MainActivity. */
        const val TICK_INTERVAL_MS = 60_000L
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAY_MS = 86_400_000L
    }
}
