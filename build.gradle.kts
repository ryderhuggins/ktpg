plugins {
    kotlin("multiplatform") version "1.9.20-RC2"
}

group = "org.ktpg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
            staticLib()
        }
    }
    sourceSets {
        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-network:2.3.7")
                implementation("io.ktor:ktor-network-tls:2.3.7")
                implementation(project.dependencies.platform("org.kotlincrypto.hash:bom:0.4.0"))
                implementation("org.kotlincrypto.hash:md")
                implementation(project.dependencies.platform("org.kotlincrypto.macs:bom:0.4.0"))
                implementation("org.kotlincrypto.macs:hmac-sha2")
                implementation("com.michael-bull.kotlin-result:kotlin-result:1.1.18")
            }
        }
        val nativeTest by getting
    }
}