package com.carbon.prolocker.core.domain

import com.carbon.prolocker.core.repository.BackgroundRepository

class ReportBackgroundDownloadUseCase(private val repository: BackgroundRepository) {
    suspend operator fun invoke(id: Int, packageName: String): Boolean {
        return repository.reportDownload(id, packageName)
    }
}
