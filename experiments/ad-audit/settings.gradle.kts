// Dự án Gradle RIÊNG cho công cụ đo quảng cáo – không nằm trong bản dựng app Tube Sạch.
pluginManagement {
    repositories {
        // Bản sao Maven Central của Google – đỡ bị giới hạn lượt tải (HTTP 429).
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        // Bản sao Maven Central của Google – đỡ bị giới hạn lượt tải (HTTP 429).
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        maven("https://jitpack.io") // NewPipeExtractor
    }
}

rootProject.name = "ad-audit"
