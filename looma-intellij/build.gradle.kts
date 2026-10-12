import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "io.github.djamelkorei"
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde.isNullOrBlank()) {
            intellijIdea(providers.gradleProperty("platformVersion"))
        } else {
            local(localIde)
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
    // The engine's compiled classes (mvn -pl looma-core compile), to check the directive table against them.
    testRuntimeOnly(files("../looma-core/target/classes"))
    testRuntimeOnly("org.springframework:spring-expression:7.0.9")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    pluginConfiguration {
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    // JetBrains Marketplace: PUBLISH_TOKEN from https://plugins.jetbrains.com/author/me/tokens.
    // Signing is optional but recommended; see docs/releasing.md for how to make the certificate and key.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // A version like 0.2.0-beta goes to the "beta" channel, which only users who opt in see.
        channels = providers.gradleProperty("pluginVersion").map { v ->
            listOf(v.substringAfter('-', "").ifEmpty { "default" })
        }
    }
    pluginVerification {
        ides {
            // The oldest supported release, plus the installed IDE when building against one.
            create(IntelliJPlatformType.IntellijIdeaCommunity, providers.gradleProperty("verifyOldestVersion"))
            val localIde = providers.gradleProperty("localIdePath").orNull
            if (!localIde.isNullOrBlank()) local(localIde)
        }
    }
}

// ./gradlew runIde -PrunIdeArgs=/path/to/project opens that project in the sandbox IDE.
tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("runIde") {
    providers.gradleProperty("runIdeArgs").orNull?.let { args = listOf(it) }
}
