plugins {
    kotlin("jvm") version "2.3.20"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.24"
    id("com.gradleup.shadow") version "8.3.10"
}

group = "project.kompass"
version = "1.6"

repositories {
    mavenCentral()
    maven {
        name = "papermc-repo"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven {
        name = "sonatype"
        url = uri("https://oss.sonatype.org/content/groups/public/")
    }
    maven {
        name = "codemc-releases"
        url = uri("https://repo.codemc.io/repository/maven-releases/")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    paperweight.paperDevBundle("26.3.build.+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation("com.github.retrooper:packetevents-spigot:2.14.0")
}

tasks {
    assemble {
        // Minecraft 26.1+ is natively unobfuscated; reobfJar is retired in favor of shadowJar
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("kotlin", "project.kompass.tweakskompass.libs.kotlin")
        relocate("com.github.retrooper.packetevents", "project.kompass.tweakskompass.libs.packetevents")
        relocate("io.github.retrooper.packetevents", "project.kompass.tweakskompass.libs.packetevents")
    }
}