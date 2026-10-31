plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
    group = "ru.zhma.campaign"
    version = "0.1.0"
}
