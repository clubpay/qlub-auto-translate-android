package org.qlub

import org.gradle.api.provider.Property

abstract class QlubAutoTranslateExtension {

    abstract val appContext: Property<String>
    abstract val verbose: Property<Boolean>
    abstract val apiKey: Property<String>
    abstract val model: Property<String>
    
    init {
        appContext.convention("A mobile application")
        verbose.convention(false)
        apiKey.convention("")
        model.convention("gpt-5-nano")
    }
}
