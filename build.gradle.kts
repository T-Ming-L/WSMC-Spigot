plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.wsmc.spigot"
version = "1.2.0"
description = "Enable WebSocket support for Minecraft Java (Paper/Spigot 1.20.4+)"

java {
    // Java 17 bytecode runs on both Java 17 (MC 1.18-1.20) and Java 21 (MC 1.21+)
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
}

dependencies {
    // Compile against the minimum supported version (1.20.4).
    // Binary compatible upward to latest Paper releases.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // Netty is provided by Paper/Spigot at runtime.
    // Netty 4.1.x is API-compatible across all supported MC versions.
    compileOnly("io.netty:netty-all:4.1.100.Final")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
}
