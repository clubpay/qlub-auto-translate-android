plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
}

group = "org.qlub"
version = "1.0.7"

gradlePlugin {
    website.set("https://github.com/batuhangoktepe/qlub-auto-translate")
    vcsUrl.set("https://github.com/batuhangoktepe/qlub-auto-translate.git")

    plugins {
        create("auto-translate") {
            id = "org.qlub.auto-translate"
            implementationClass = "org.qlub.QlubAutoTranslatePlugin"
            displayName = "Qlub Auto Translate Plugin"
            description = "Qlub Auto Translate Gradle Plugin"
            tags.set(listOf("translation", "automation", "json", "android"))
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
