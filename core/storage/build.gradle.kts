plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "app.expenses.core.storage"
        compileSdk = 37
        minSdk = 26
    }
    jvm()

    if (providers.gradleProperty("enableIos").orNull == "true") {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.sqlite)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

sqldelight {
    databases {
        create("ExpensesDb") {
            packageName.set("app.expenses.core.storage.db")
            // ON CONFLICT ... DO UPDATE needs SQLite 3.24+; the default dialect is 3.18.
            // Android's SQLCipher 4.x and the JVM sqlite-jdbc driver both bundle newer SQLite.
            dialect(libs.sqldelight.sqlite.dialect)
            verifyMigrations.set(true)
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}

// BackupFormatDocTest reads docs/BACKUP_FORMAT.md; declare it so a change to the document alone re-runs the test instead of being skipped as up to date.
tasks.withType<Test>().configureEach { inputs.file(rootProject.file("docs/BACKUP_FORMAT.md")).withPropertyName("backupFormatDoc") }

// UiConventionsTest reads the screen sources; declare them so a change there re-runs it.
tasks.withType<Test>().configureEach { inputs.dir(rootProject.file("androidApp/src/main/kotlin/app/expenses/ui")).withPropertyName("uiSources") }
