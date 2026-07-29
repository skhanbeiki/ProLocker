package com.carbon.prolocker.core.domain

import com.carbon.prolocker.core.repository.BackgroundRepository
import com.carbon.prolocker.network.model.BackgroundResponse

class GetBackgroundsUseCase(private val repository: BackgroundRepository) {
    suspend operator fun invoke(cursor: String): BackgroundResponse {
        return repository.getBackgrounds(cursor)
    }
}
