package com.carbon.prolocker.feature.backup.di

import com.carbon.prolocker.feature.backup.data.BackupPreferences
import com.carbon.prolocker.feature.backup.data.BackupRepository
import com.carbon.prolocker.feature.backup.ui.BackupAppsViewModel
import com.carbon.prolocker.feature.backup.ui.BackupCategoryViewModel
import com.carbon.prolocker.feature.backup.ui.BackupHomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val backupModule = module {
    single { BackupPreferences(androidContext()) }
    single { BackupRepository(androidContext(), get()) }

    viewModel { BackupHomeViewModel(get(), get()) }
    viewModel { BackupCategoryViewModel(context = androidContext(), repository = get(), analyticsManager = get()) }
    viewModel { BackupAppsViewModel(context = androidContext(), repository = get(), analyticsManager = get()) }
}
