import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing details live outside the repository. Without them a release
// build still runs locally on the debug key — but it is not shippable, and the
// build says so rather than quietly producing an unuploadable APK.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("secrets/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

// The AdMob identity lives outside the repository the same way. Without the
// file every build keeps Google's public test IDs, so a bare checkout still
// builds and shows a (test) banner — but the release is not shippable, and
// the build says so.
val admobTestAppId = "ca-app-pub-3940256099942544~3347511713"
val admobTestBannerId = "ca-app-pub-3940256099942544/6300978111"
val admobProperties = Properties().apply {
    val file = rootProject.file("secrets/admob.env")
    if (file.exists()) file.inputStream().use { load(it) }
}
val admobAppId = admobProperties.getProperty("APP_ID")?.trim() ?: admobTestAppId
val admobBannerId = admobProperties.getProperty("AD_ID")?.trim() ?: admobTestBannerId

android {
    namespace = "tech.idct.weighttracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "tech.idct.weighttracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Google's guidance for development: the real app ID in the manifest,
        // test ad units for every request. Only the release build asks the
        // real banner unit for ads.
        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobTestBannerId\"")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
            if (admobProperties.isEmpty) {
                logger.warn(
                    "No secrets/admob.env — release will serve Google's test banner. " +
                        "See docs/production-checklist.md."
                )
            }
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                // Local smoke-testing only; Play will not accept a debug-signed APK.
                logger.warn(
                    "No secrets/keystore.properties — signing release with the debug key. " +
                        "See docs/production-checklist.md."
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["debug"].kotlin.srcDir("src/debug/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp { arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path) }

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.health.connect.client)

    implementation(libs.billing)
    implementation(libs.play.services.ads)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
