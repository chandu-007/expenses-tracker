import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

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
        iosSimulatorArm64 {
            // The default native test binary is a DEBUG one; the iPhone app ships the optimised (RELEASE) framework, where an optimiser-only crash was seen that the
            // debug binary did not show. `./gradlew :core:domain:iosSimulatorArm64ReleaseTest -PenableIos=true` runs the same commonTest suite as an optimised binary.
            binaries.test(listOf(NativeBuildType.RELEASE))
            testRuns.create("release") { setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE)) }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotest.property)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
