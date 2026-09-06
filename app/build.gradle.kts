plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.vault.workspace"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.vault.workspace"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "VERSION_NAME", "\"0.1.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("VAULT_KEYSTORE_FILE")
            val ksPass = System.getenv("VAULT_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("VAULT_KEY_ALIAS")
            val keyPass = System.getenv("VAULT_KEY_PASSWORD")
            if (ksFile.isNullOrBlank() || ksPass.isNullOrBlank() ||
                keyAliasEnv.isNullOrBlank() || keyPass.isNullOrBlank()
            ) {
                // Config is registered; assembleRelease task will fail explicitly below
            } else {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = keyAliasEnv
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksFile = System.getenv("VAULT_KEYSTORE_FILE")
            val ksPass = System.getenv("VAULT_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("VAULT_KEY_ALIAS")
            val keyPass = System.getenv("VAULT_KEY_PASSWORD")
            if (ksFile.isNullOrBlank() || ksPass.isNullOrBlank() ||
                keyAliasEnv.isNullOrBlank() || keyPass.isNullOrBlank()
            ) {
                // Will fail at signing time — see afterEvaluate guard
            } else {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

// Fail fast if release assemble is requested without signing env
afterEvaluate {
    tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
        doFirst {
            val ksFile = System.getenv("VAULT_KEYSTORE_FILE")
            val ksPass = System.getenv("VAULT_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("VAULT_KEY_ALIAS")
            val keyPass = System.getenv("VAULT_KEY_PASSWORD")
            if (ksFile.isNullOrBlank() || ksPass.isNullOrBlank() ||
                keyAliasEnv.isNullOrBlank() || keyPass.isNullOrBlank()
            ) {
                throw GradleException(
                    "Release signing env missing. Set VAULT_KEYSTORE_FILE, " +
                        "VAULT_KEYSTORE_PASSWORD, VAULT_KEY_ALIAS, VAULT_KEY_PASSWORD."
                )
            }
            val f = file(ksFile)
            if (!f.exists()) {
                throw GradleException("Keystore file not found: $ksFile")
            }
            android.signingConfigs.getByName("release").apply {
                storeFile = f
                storePassword = ksPass
                keyAlias = keyAliasEnv
                keyPassword = keyPass
            }
            android.buildTypes.getByName("release").signingConfig =
                android.signingConfigs.getByName("release")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource:1.5.1")
    implementation("androidx.media3:media3-common:1.5.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// Room schema export
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
