plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "app.expenses"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.expenses"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Test-data seeding through an intent extra exists only where this is true; never in release.
        buildConfigField("boolean", "SEEDING_ENABLED", "false")
        // Whether this build reads bank texts. Off only in the Google Play build, which has no SMS permission (see src/play/AndroidManifest.xml).
        buildConfigField("boolean", "SMS_CAPTURE", "true")
    }

    // The release key lives outside the repository: its path and passwords are Gradle properties in ~/.gradle/gradle.properties. Without them the release
    // build is simply unsigned, never signed with the debug key.
    signingConfigs {
        if (providers.gradleProperty("EXPENSES_STORE_FILE").isPresent) {
            create("release") {
                storeFile = file(providers.gradleProperty("EXPENSES_STORE_FILE").get())
                storePassword = providers.gradleProperty("EXPENSES_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("EXPENSES_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("EXPENSES_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SEEDING_ENABLED", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // The Google Play build: release code and signing, without the SMS permission and receiver. `./gradlew :androidApp:bundlePlay` makes the .aab to upload.
        create("play") {
            initWith(getByName("release"))
            signingConfigs.findByName("release")?.let { signingConfig = it }
            matchingFallbacks += "release"
            buildConfigField("boolean", "SMS_CAPTURE", "false")
        }
        // Release code (R8, not debuggable) signed with the debug key, for measuring startup and scrolling honestly.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
            buildConfigField("boolean", "SEEDING_ENABLED", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:storage"))
    implementation(project(":core:parsers"))
    implementation(project(":shared"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.glance.appwidget)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.glance.appwidget.testing)
    androidTestImplementation(libs.sqlcipher.android)
    androidTestImplementation(libs.sqldelight.android.driver)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

// The Google Play build has its own package name: `app.expenses` is already taken on Play by another developer. The code's namespace stays `app.expenses`.
// The directly installed build keeps `app.expenses`, so testers who have it keep their data and update in place.
androidComponents {
    onVariants(selector().withBuildType("play")) { variant -> variant.applicationId.set("io.github.chandu007.expenses") }
}
