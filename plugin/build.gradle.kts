plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "io.github.clubpay"
version = "1.0.0"

gradlePlugin {
    website.set("https://github.com/clubpay/qlub-auto-translate-android")
    vcsUrl.set("https://github.com/clubpay/qlub-auto-translate-android.git")

    plugins {
        create("auto-translate") {
            id = "io.github.clubpay.auto-translate"
            implementationClass = "io.github.clubpay.QlubAutoTranslatePlugin"
            displayName = "Qlub Auto Translate"
            description = "Gradle plugin for automatic Android string resource translation using AI. Detects missing translations and uses OpenAI API for context-aware, high-quality localization."
            tags.set(listOf("translation", "automation", "android", "localization", "i18n", "openai", "ai"))
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}
