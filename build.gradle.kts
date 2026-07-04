plugins {
    kotlin("jvm") version "2.2.20"
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
}


tasks.test {
    useJUnitPlatform()
}