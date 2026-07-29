package com.carbon.prolocker.core.domain

import com.carbon.prolocker.core.repository.BackgroundRepository

class CheckNewBackgroundsUseCase(private val repository: BackgroundRepository) {
    suspend operator fun invoke(lastId: Int): Int {
        return repository.checkNew(lastId)
    }
}
