plugins {
    kotlin("jvm") version "2.4.21" apply false
    kotlin("plugin.serialization") version "2.4.21" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }
    group = "ru.zhma.campaign"
    version = "0.1.0"
}
