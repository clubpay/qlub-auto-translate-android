package io.github.clubpay

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class QlubAutoTranslateExtension {

    abstract val appContext: Property<String>
    abstract val verbose: Property<Boolean>
    abstract val apiKey: Property<String>
    abstract val model: Property<String>
    abstract val targetLanguages: ListProperty<String>
    abstract val runOnReleaseBuild: Property<Boolean>

    init {
        appContext.convention("A mobile application")
        verbose.convention(false)
        apiKey.convention("")
        model.convention("gpt-5-nano")
        targetLanguages.convention(emptyList())
        runOnReleaseBuild.convention(false)
    }
}
