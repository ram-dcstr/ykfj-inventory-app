package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActivePaluwaganGroupsUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
) {
    operator fun invoke(): Flow<List<PaluwaganGroup>> =
        paluwaganRepository.observeActiveGroups()
}
