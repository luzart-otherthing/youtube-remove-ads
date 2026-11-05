plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Số phiên bản: CI truyền vào qua biến môi trường (số lần chạy GitHub Actions).
val buildNumber = (System.getenv("BUILD_NUMBER") ?: "1").toInt()
val appVersionName = System.getenv("VERSION_NAME") ?: "2.0.$buildNumber"

// Repo GitHub dùng để kiểm tra bản cập nhật (Releases).
val updateRepo = System.getenv("UPDATE_REPO") ?: "luzart-otherthing/youtube-remove-ads"

android {
    namespace = "com.luzart.tubesach"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luzart.tubesach"
        minSdk = 23
        targetSdk = 36
        versionCode = buildNumber
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("release") {
            // Khoá ký cố định để các bản cập nhật cài đè được lên nhau.
            // Có thể thay bằng khoá riêng qua biến môi trường (GitHub Secrets).
            val ksPath = System.getenv("SIGNING_KEYSTORE") ?: "signing/tubesach.jks"
            storeFile = file(ksPath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD") ?: "tubesach"
            keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "tubesach"
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: "tubesach"
        }
    }

    buildTypes {
        release {
            // Thu gọn APK bằng R8; giữ nguyên các lớp Rhino/NewPipeExtractor dùng reflection.
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
        // NewPipeExtractor dùng API Java 11+ → cần desugaring cho Android cũ.
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Ảnh chụp màn hình (Roborazzi) được ghi vào app/build/outputs/roborazzi
                test.systemProperty("roborazzi.test.record", "true")
                test.systemProperty("screenshots", System.getProperty("screenshots") ?: "false")
                test.systemProperty("online", System.getProperty("online") ?: "false")
                System.getProperty("videoId")?.let { test.systemProperty("videoId", it) }
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

    // Lõi lấy dữ liệu YouTube (giống NewPipe) – không dùng API chính thức, không quảng cáo.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Trình phát video
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Giao diện
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Android Auto cho máy Android cũ (≤ 14): màn hình kiểu ứng dụng bản đồ, vẽ video lên màn hình xe
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    testImplementation("androidx.car.app:app-testing:1.7.0")
    // Chuyển coroutine ↔ ListenableFuture cho thư viện Android Auto (MediaLibraryService)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")

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
