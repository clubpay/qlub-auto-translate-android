package io.github.clubpay

import java.io.File
import kotlin.test.assertTrue
import kotlin.test.Test
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

class QlubAutoTranslatePluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    @Test fun `can run task`() {
        settingsFile.writeText("")
        buildFile.writeText("""
            plugins {
                id('io.github.clubpay.auto-translate')
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("qlubAutoTranslate")
        runner.withProjectDir(projectDir)
        
        val result = runner.buildAndFail()
        assertTrue(result.output.contains("Missing required parameter"))
    }
    
    @Test fun `task requires language parameter`() {
        settingsFile.writeText("")
        buildFile.writeText("""
            plugins {
                id('io.github.clubpay.auto-translate')
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("qlubAutoTranslate")
        runner.withProjectDir(projectDir)
        
        val result = runner.buildAndFail()
        assertTrue(result.output.contains("Missing required parameter. Usage: -Plangs=tr,de,fr or -Plang=de"))
    }
}
