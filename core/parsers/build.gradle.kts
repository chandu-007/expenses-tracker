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
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
