package io.github.clubpay

import org.gradle.api.Project
import org.gradle.api.Plugin

class QlubAutoTranslatePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("qlubAutoTranslate", QlubAutoTranslateExtension::class.java)

        val translateTask = project.tasks.register("qlubAutoTranslate", TranslateMissingStringsTask::class.java) {
            apiKey.set(extension.apiKey)
            appContext.set(extension.appContext)
            verbose.set(extension.verbose)
            model.set(extension.model)
            targetLanguages.set(extension.targetLanguages)
            projectDir.set(project.rootProject.layout.projectDirectory)
            langs.set(project.providers.gradleProperty("langs"))
            lang.set(project.providers.gradleProperty("lang"))
        }

        project.afterEvaluate {
            if (extension.runOnReleaseBuild.get()) {
                project.tasks.matching {
                    (it.name.startsWith("assemble") || it.name.startsWith("bundle")) && it.name.endsWith("Release")
                }.configureEach {
                    dependsOn(translateTask)
                }
            }
        }
    }
}
