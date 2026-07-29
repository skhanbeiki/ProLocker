package com.carbon.prolocker.core.domain

import com.carbon.prolocker.BuildConfig
import com.carbon.prolocker.core.repository.UpdateRepository
import com.carbon.prolocker.network.model.UpdateResponse

class CheckUpdateUseCase(private val repository: UpdateRepository) {
    suspend operator fun invoke(): UpdateResponse? {
        val updateResponse = repository.checkUpdate()
        return if (updateResponse != null && updateResponse.newVerCode > BuildConfig.VERSION_CODE) {
            updateResponse
        } else {
            null
        }
    }
}
