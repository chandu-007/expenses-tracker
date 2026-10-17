import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
    android {
        namespace = "app.expenses.shared"
        compileSdk = 37
        minSdk = 26
    }
    if (providers.gradleProperty("enableIos").orNull == "true") {
        val xcf = XCFramework("Shared")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { t ->
            t.binaries.framework(listOf(org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE)) { baseName = "Shared"; isStatic = true; xcf.add(this) }
        }
        // Each Kotlin/Native release link takes several GB of heap (gradle.properties); one at a time, so two never compete for the same memory.
        tasks.matching { it.name == "linkReleaseFrameworkIosSimulatorArm64" }.configureEach { mustRunAfter("linkReleaseFrameworkIosArm64") }
        sourceSets.iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:storage"))
            api(project(":core:parsers"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}
