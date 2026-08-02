package com.carbon.prolocker.feature.hidefile.di

import com.carbon.prolocker.feature.hidefile.HideFileViewModel
import com.carbon.prolocker.feature.hidefile.data.HideFileDatabase
import com.carbon.prolocker.feature.hidefile.data.HideFileRepository
import com.carbon.prolocker.feature.hidefile.data.HideFileStorage
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val hideFileModule = module {
    single { HideFileDatabase(androidContext()) }
    single { HideFileStorage(androidContext()) }
    single { HideFileRepository(get(), get()) }
    viewModel { HideFileViewModel(get()) }
}
