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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Test-data seeding through an intent extra exists only where this is true; never in release.
        buildConfigField("boolean", "SEEDING_ENABLED", "false")
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "SEEDING_ENABLED", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
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
