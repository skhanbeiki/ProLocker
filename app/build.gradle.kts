plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.secrets)
    alias(libs.plugins.google.services)
}
android {
    namespace = "com.carbon.prolocker"
    compileSdk { version = release(36) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.carbon.prolocker"
        minSdk = 24
        targetSdk = 36
        versionCode = 90
        versionName ="5.2.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        addManifestPlaceholders(
            mapOf(
                "TapsellMediationAppKey" to "8cfe437a-6b2f-4a3d-bdaa-b63d1a6d4b94",
                "TapsellMediationAdmobAdapterSignature" to "ca-app-pub-3940256099942544~3347511713",
            )
        )

        resourceConfigurations += listOf(
            "fa",
            "en"
        )
    }

    flavorDimensions += "store"
    productFlavors {
        create("bazaar") {
            dimension = "store"
            applicationIdSuffix = ""
            buildConfigField("String", "MARKET_NAME", "\"Bazaar\"")
            buildConfigField("String", "MARKET_TYPE", "\"bazaar\"")
        }
        create("myket") {
            dimension = "store"
            applicationIdSuffix = ""
            buildConfigField("String", "MARKET_NAME", "\"Myket\"")
            buildConfigField("String", "MARKET_TYPE", "\"myket\"")
        }
        create("googleplay") {
            dimension = "store"
            applicationIdSuffix = ""
            buildConfigField("String", "MARKET_NAME", "\"Google Play\"")
            buildConfigField("String", "MARKET_TYPE", "\"googleplay\"")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore.jks")
            storePassword = "-Prolocker$^carbon>siamak<reza>mahmoud<mehdi!"
            keyAlias = "prolocker"
            keyPassword = "-Prolocker$^carbon>siamak<reza>mahmoud<mehdi!"
        }
    }

    buildTypes {
        release {

            isCrunchPngs = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }

}

val androidComponents = extensions.getByType<com.android.build.api.variant.ApplicationAndroidComponentsExtension>()
androidComponents.onVariants { variant ->
    val flavor = variant.flavorName ?: ""
    val bType = variant.buildType ?: ""
    val vCode = android.defaultConfig.versionCode ?: 90
    variant.outputs.forEach { output ->
        val impl = output as? com.android.build.api.variant.impl.VariantOutputImpl
        if (bType == "release") {
            impl?.outputFileName?.set("ProLocker-$flavor-$vCode.apk")
        } else {
            impl?.outputFileName?.set("ProLocker-$flavor-$vCode-$bType.apk")
        }
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.cardview)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.work.runtime.ktx)

    implementation(libs.play.services.ads)
    implementation(libs.tapsell)
    implementation(libs.legacy)
    implementation(libs.adivery)

    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)
    implementation(libs.persiandate)
    implementation(libs.lottie.compose)
}


