plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// App dành cho Google TV / Android TV (ví dụ TV Coocaa 55Y73). Dùng chung phần lõi :core với app điện thoại.
// Số phiên bản: CI truyền vào qua biến môi trường (số lần chạy GitHub Actions) – giống app điện thoại.
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
val appVersionName = System.getenv("VERSION_NAME") ?: "2.0.$buildNumber"
val updateRepo = System.getenv("UPDATE_REPO") ?: "luzart-otherthing/youtube-remove-ads"

android {
    namespace = "com.luzart.tubesach.tv"
    compileSdk = 36

    defaultConfig {
        // Khác app điện thoại → cài được cả hai trên cùng một máy, không đè lên nhau.
        applicationId = "com.luzart.tubesach.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = buildNumber
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("release") {
            // Dùng chung khoá ký với app điện thoại (có thể thay qua GitHub Secrets).
            val ksPath = System.getenv("SIGNING_KEYSTORE") ?: "../app/signing/tubesach.jks"
            storeFile = file(ksPath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "tubesach"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "tubesach"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "tubesach"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    sourceSets {
        // Dữ liệu YouTube thật đã lưu sẵn (dùng chung với test của app điện thoại).
        getByName("test").resources.srcDir("../app/src/test/resources")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("roborazzi.test.record", "true")
                test.systemProperty("screenshots", System.getProperty("screenshots") ?: "false")
                test.systemProperty("online", System.getProperty("online") ?: "false")
                test.testLogging.showStandardStreams = true
                System.getenv("ROBOLECTRIC_REPO")?.let {
                    test.systemProperty("robolectric.dependency.repo.url", it)
                }
            }
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val media3 = "1.11.1"
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // Phần lõi: lấy dữ liệu YouTube, trình phát, SponsorBlock, lịch sử
    implementation(project(":core"))
    implementation("androidx.media3:media3-ui:$media3")

    // Giao diện (Compose, điều khiển bằng remote)
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")

    // Ảnh thu nhỏ
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.77.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
