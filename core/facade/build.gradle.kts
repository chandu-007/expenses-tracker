import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// The small, Swift-friendly surface of the shared core that the iPhone app uses. The iOS targets need full Xcode: enable with -PenableIos=true.
kotlin {
    jvmToolchain(17)
    jvm()

    if (providers.gradleProperty("enableIos").orNull == "true") {
        val xcf = XCFramework("ExpensesCore")
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "ExpensesCore"
                isStatic = true
                export(project(":core:domain"))
                xcf.add(this)
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:storage"))
        }
        if (providers.gradleProperty("enableIos").orNull == "true") {
            iosMain.dependencies {
                implementation(libs.sqldelight.native.driver)
            }
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}
