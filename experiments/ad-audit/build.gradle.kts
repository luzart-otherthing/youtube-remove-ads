// Công cụ đo: gọi YouTube thật bằng ĐÚNG mã InnerTube/Parser của Tube Sạch (chỉ chép sang để biên dịch,
// không sửa gì trong app), rồi đếm quảng cáo YouTube gửi về và kiểm tra có cái nào lọt ra danh sách không.
//
// Chạy:  ./gradlew -p experiments/ad-audit run --args="experiments/ad-audit/ket-qua.md"
plugins {
    kotlin("jvm") version "2.2.21"
    application
}

val appSrc = rootDir.resolve("../../app/src/main/java/com/luzart/tubesach")

// Chép (read-only) các file cần dùng của app sang thư mục build.
val copyAppSources by tasks.registering(Sync::class) {
    from(appSrc) {
        include(
            "innertube/InnerTube.kt",
            "innertube/Parser.kt",
            "innertube/Json.kt",
            "innertube/DirectStreams.kt",
            "data/VideoItem.kt",
        )
    }
    into(layout.buildDirectory.dir("appsrc"))
}

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        kotlin.srcDir(copyAppSources)
    }
}

dependencies {
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // Trên Android org.json có sẵn; trên máy tính cần thư viện này.
    implementation("org.json:json:20250517")
}

application {
    mainClass.set("AdAuditKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir.resolve("../..")
}
