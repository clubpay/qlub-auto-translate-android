package io.github.clubpay

import org.gradle.api.Project
import org.gradle.api.Plugin

class QlubAutoTranslatePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("qlubAutoTranslate", QlubAutoTranslateExtension::class.java)

        project.tasks.register("qlubAutoTranslate", TranslateMissingStringsTask::class.java) {
            apiKey.set(extension.apiKey)
            appContext.set(extension.appContext)
            verbose.set(extension.verbose)
            model.set(extension.model)
            projectDir.set(project.layout.projectDirectory)
            langs.set(project.providers.gradleProperty("langs"))
            lang.set(project.providers.gradleProperty("lang"))
        }
    }
}
