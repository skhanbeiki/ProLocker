package com.carbon.prolocker.feature.callblocker.di

import com.carbon.prolocker.feature.callblocker.data.CallBlockerRepository
import com.carbon.prolocker.feature.callblocker.ui.CallBlockerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val callBlockerModule = module {
    single { CallBlockerRepository(androidContext(), get()) }
    viewModel { CallBlockerViewModel(androidContext(), get()) }
}
