plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// Phần lõi dùng chung cho app điện thoại (:app) và app TV (:tv):
// lấy dữ liệu YouTube (InnerTube + NewPipeExtractor), trình phát, SponsorBlock, lưu lịch sử, kiểm tra cập nhật.
android {
    namespace = "com.luzart.tubesach.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        // NewPipeExtractor dùng API Java 11+ → cần desugaring cho Android cũ.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
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

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // Lõi lấy dữ liệu YouTube (giống NewPipe) – không dùng API chính thức, không quảng cáo.
    api("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    api("com.squareup.okhttp3:okhttp:4.12.0")

    // Trình phát video
    api("androidx.media3:media3-exoplayer:$media3")
    api("androidx.media3:media3-exoplayer-dash:$media3")
    api("androidx.media3:media3-exoplayer-hls:$media3")
    api("androidx.media3:media3-session:$media3")

    api("androidx.core:core-ktx:1.17.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
