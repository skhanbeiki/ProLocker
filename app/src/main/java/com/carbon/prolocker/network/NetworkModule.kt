package com.carbon.prolocker.network

import org.koin.dsl.module

val networkModule = module {
    single { HttpClientProvider.provide() }
    single { com.carbon.prolocker.network.api.ConfigApi(get()) }
    single { com.carbon.prolocker.network.api.CrashApi(get()) }
    single { com.carbon.prolocker.network.api.UpdateApi(get()) }
    single { com.carbon.prolocker.network.api.BackgroundApi(get()) }
}
