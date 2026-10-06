plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "app.expenses.core.domain"
        compileSdk = 37
        minSdk = 26
        withHostTestBuilder {}
    }
    jvm()

    // iOS targets need full Xcode (not just Command Line Tools). Enable with -PenableIos=true
    // on a machine or CI runner that has it. Off by default so `check` passes everywhere.
    if (providers.gradleProperty("enableIos").orNull == "true") {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.property)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
