package org.qlub

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Test
import org.junit.Assert.assertNotNull

class QlubAutoTranslatePluginTest {
    @Test fun `plugin registers task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.qlub.auto-translate")
        assertNotNull(project.tasks.findByName("qlubAutoTranslate"))
    }
}
