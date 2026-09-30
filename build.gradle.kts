import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.20"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}
val ktor_version: String by project


dependencies {
    testImplementation(kotlin("test"))
    implementation("io.ktor:ktor-client-core:${ktor_version}")
    implementation("io.ktor:ktor-client-cio:${ktor_version}")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("redis.clients:jedis:5.1.0")
    implementation("org.postgresql:postgresql:42.7.3")   // JDBC driver
    implementation("com.zaxxer:HikariCP:5.1.0")          // connection pool
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")
    implementation("org.flywaydb:flyway-core:10.15.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.15.0")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")         // logger backend for Flyway/Hikari/Jedis/Ktor

}

// Kotlin 2.2.x can emit bytecode up to JVM 24, but your Gradle JDK is Liberica 25.
// Pin Java AND Kotlin to the same target explicitly so they can never drift apart
// (that mismatch is what "Inconsistent JVM Target Compatibility" was about).
java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
    }
}

application {
    mainClass.set("org.example.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
