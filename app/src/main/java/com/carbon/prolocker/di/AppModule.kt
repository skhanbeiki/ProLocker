package com.carbon.prolocker.di

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.carbon.prolocker.core.database.AppDatabase
import com.carbon.prolocker.core.database.LockedAppsRepository
import com.carbon.prolocker.core.datastore.PreferencesRepository
import com.carbon.prolocker.core.datastore.UserPreferencesSerializer
import com.carbon.prolocker.core.permissions.PermissionHealthMonitor
import com.carbon.prolocker.core.security.CameraCaptureManager
import com.carbon.prolocker.core.security.DeviceAdminManager
import com.carbon.prolocker.core.security.EventLogManager
import com.carbon.prolocker.core.security.IntruderManager
import com.carbon.prolocker.core.security.RecoveryManager
import com.carbon.prolocker.core.security.SecurityScoreManager
import com.carbon.prolocker.core.security.StealthModeManager
import com.carbon.prolocker.core.service.FailedAttemptManager
import com.carbon.prolocker.core.service.ForegroundAppDetector
import com.carbon.prolocker.core.service.HybridDetectionEngine
import com.carbon.prolocker.core.service.LockSessionManager
import com.carbon.prolocker.core.utils.VibrationManager
import com.carbon.prolocker.feature.home.GetInstalledAppsUseCase
import com.carbon.prolocker.feature.lock.PatternSetupViewModel
import com.carbon.prolocker.feature.lock.PinSetupViewModel
import com.carbon.prolocker.feature.onboarding.SuccessViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val useCaseModule = module {
    factory { GetInstalledAppsUseCase(androidContext()) }
    factory { com.carbon.prolocker.core.domain.CheckUpdateUseCase(get()) }
    factory { com.carbon.prolocker.core.domain.GetBackgroundsUseCase(get()) }
    factory { com.carbon.prolocker.core.domain.CheckNewBackgroundsUseCase(get()) }
    factory { com.carbon.prolocker.core.domain.ReportBackgroundDownloadUseCase(get()) }
}

val serviceModule = module {
    single { ForegroundAppDetector(androidContext()) }
    single { HybridDetectionEngine(get(), androidContext()) }
    single { LockSessionManager(get()) }
    single { com.carbon.prolocker.core.service.ProtectionManager(androidContext(), get(), get(), get()) }
    single { FailedAttemptManager() }
    single { CameraCaptureManager(androidContext()) }
    single { VibrationManager(androidContext()) }
    single { IntruderManager(androidContext(), get(), get(), get(), get()) }
    single { RecoveryManager(get()) }
    single { StealthModeManager(get()) }
    single { DeviceAdminManager(androidContext()) }
    single { EventLogManager(get()) }
    single { SecurityScoreManager(androidContext(), get(), get()) }
    single { PermissionHealthMonitor(androidContext(), get()) }
    single { com.carbon.prolocker.core.language.LanguageManager(get()) }
    single { com.carbon.prolocker.core.rate.RateAppManager(get(), get()) }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "app_locker_db"
        ).fallbackToDestructiveMigration().build()
    }
    single { get<AppDatabase>().lockedAppDao() }
    single { get<AppDatabase>().intruderEventDao() }
    single { get<AppDatabase>().securityEventDao() }
    single { get<AppDatabase>().crashDao() }
    single { get<AppDatabase>().callBlockerDao() }
    single { LockedAppsRepository(get()) }
}


val dataStoreModule = module {
    single {
        DataStoreFactory.create(
            serializer = UserPreferencesSerializer,
            produceFile = {
                try {
                    androidContext().dataStoreFile("user_prefs.pb")
                } catch (e: Exception) {
                    // Fallback to a file in the cache directory if dataStoreFile fails
                    java.io.File(androidContext().cacheDir, "user_prefs.pb")
                }
            }
        )
    }
}

val repositoryModule = module {
    single { PreferencesRepository(get()) }
    single { com.carbon.prolocker.network.repository.RemoteConfigRepository(get(), get(), androidContext()) }
    single { com.carbon.prolocker.core.database.CrashRepository(get(), get(), get(), androidContext()) }
    single { com.carbon.prolocker.core.repository.UpdateRepository(get()) }
    single { com.carbon.prolocker.core.repository.BackgroundRepository(get()) }
    single { com.carbon.prolocker.core.analytics.AnalyticsManager(androidContext()) }
    single { com.carbon.prolocker.feature.privacyauditor.PrivacyAuditorRepository(androidContext()) }
}

val viewModelModule = module {
    viewModel { PatternSetupViewModel(get()) }
    viewModel { PinSetupViewModel(get()) }
    viewModel { SuccessViewModel(get()) }
    viewModel { com.carbon.prolocker.feature.onboarding.PermissionsViewModel() }
    viewModel { com.carbon.prolocker.feature.home.HomeViewModel(get(), get(), get(), androidContext(), get(), get(), get()) }
    viewModel { com.carbon.prolocker.feature.account.AccountViewModel(get(), get(), get(), get(), get()) }
    viewModel { com.carbon.prolocker.feature.security.SecurityViewModel(get(), get(), get(), get()) }
    viewModel { com.carbon.prolocker.feature.main.MainViewModel(get(), get(), get()) }
    viewModel { com.carbon.prolocker.feature.gallery.BackgroundGalleryViewModel(get(), get(), get(), get()) }
    viewModel { com.carbon.prolocker.feature.entrylock.EntryLockViewModel(get(), get(), androidContext()) }
    viewModel { com.carbon.prolocker.feature.privacyauditor.PrivacyAuditorViewModel(get()) }
}


val appModule = module {
    includes(
        databaseModule,
        dataStoreModule,
        repositoryModule,
        useCaseModule,
        serviceModule,
        viewModelModule,
        adModule,
        com.carbon.prolocker.feature.hidefile.di.hideFileModule,
        com.carbon.prolocker.feature.backup.di.backupModule,
        com.carbon.prolocker.feature.callblocker.di.callBlockerModule
    )
}
