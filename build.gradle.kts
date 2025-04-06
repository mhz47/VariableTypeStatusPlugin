plugins {
    id("org.jetbrains.intellij.platform") version "2.5.0"
    kotlin("jvm") version "2.1.20"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
}

group = "com.example.variabletypestatus"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    intellijPlatform {
        pycharmCommunity("2024.3.5")
        bundledPlugin("PythonCore")
    }
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains:annotations:24.0.1")
}
