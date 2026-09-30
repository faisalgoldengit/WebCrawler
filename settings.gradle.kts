plugins {
    // 0.8.0 is incompatible with Gradle 9 (references removed JvmVendorSpec.IBM_SEMERU)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "WebCrawler"
