plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.tommycbird"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Build against IDEA Community; the plugin only uses generic platform APIs,
        // so it installs and runs fine in CLion (and any other JetBrains IDE).
        create("IC", "2024.2.5")
        instrumentationTools()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "232"
            untilBuild = "261.*"
        }
    }
}

kotlin {
    jvmToolchain(17)
}
