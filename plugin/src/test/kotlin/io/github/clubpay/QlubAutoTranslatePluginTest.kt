package io.github.clubpay

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import org.junit.Assert.assertNotNull

class QlubAutoTranslatePluginTest {
    @Test fun `plugin registers task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.clubpay.auto-translate")
        assertNotNull(project.tasks.findByName("qlubAutoTranslate"))
    }
}
