package io.github.clubpay

import org.gradle.api.Project
import org.gradle.api.Plugin

class QlubAutoTranslatePlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("qlubAutoTranslate", QlubAutoTranslateExtension::class.java)
        
        project.tasks.register("qlubAutoTranslate", TranslateMissingStringsTask::class.java) {
            this.extension = extension
        }
    }
}
