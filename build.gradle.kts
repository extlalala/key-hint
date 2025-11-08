import org.jetbrains.kotlin.utils.addToStdlib.assertedCast

plugins {
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "zl"
version = "1.0-SNAPSHOT"

intellij {
    version = "2024.1"
    type = "IC"
    plugins.add("java")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}