import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "app.expenses.core.parsers"
        compileSdk = 37
        minSdk = 26
        withHostTestBuilder {}
    }
    jvm()

    if (providers.gradleProperty("enableIos").orNull == "true") {
        iosArm64()
        iosSimulatorArm64 {
            // Optimised (RELEASE) native test binary, as in :core:domain: `./gradlew :core:parsers:iosSimulatorArm64ReleaseTest -PenableIos=true`.
            binaries.test(listOf(NativeBuildType.RELEASE))
            testRuns.create("release") { setExecutionSourceFrom(binaries.getTest(NativeBuildType.RELEASE)) }
        }
        // Both release test runs boot and use the same simulator: one at a time.
        tasks.matching { it.name == "iosSimulatorArm64ReleaseTest" }.configureEach { mustRunAfter(":core:domain:iosSimulatorArm64ReleaseTest") }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            // PayeeGuess reads a bank narration ("TPT-note-name") with the statement import's own tidy-up, which lives in the domain module
            implementation(project(":core:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
