import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.4.0"
}

object Versions {
    const val kotlin = "1.4.0"
    const val ktor = "1.4.0"
    const val logback= "1.2.3"
}

repositories {
    mavenCentral()
    google()
    jcenter()
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().all {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "MainKt"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}")
    implementation( "io.ktor:ktor-server-netty:${Versions.ktor}")
    implementation( "io.ktor:ktor-websockets:${Versions.ktor}")
    implementation( "ch.qos.logback:logback-classic:${Versions.logback}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktor}")

    testImplementation(group = "junit", name = "junit", version = "4.12")
}