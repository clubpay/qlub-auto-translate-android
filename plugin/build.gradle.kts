plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "org.qlub"
version = "1.0.0"

gradlePlugin {
    website.set("https://github.com/clubpay/qlub-auto-translate-android")
    vcsUrl.set("https://github.com/clubpay/qlub-auto-translate-android.git")

    plugins {
        create("auto-translate") {
            id = "org.qlub.auto-translate"
            implementationClass = "org.qlub.QlubAutoTranslatePlugin"
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
