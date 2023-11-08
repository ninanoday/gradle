/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.configurationcache.isolated

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

import java.util.function.BiConsumer
import java.util.function.Function

class IsolatedProjectsToolingApiIdeaProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    // TODO: add test for BasicIdeaProject

    // TODO: write a couple of tests with different project setups to make sure the isolated Idea model matches the non-isolated one

    def "can fetch IdeaProject model for empty projects"() {
        settingsFile << """
            rootProject.name = 'root'

            include(":lib1")
            include(":lib1:lib11")
        """

        when:
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.children.size() > 0
        expectedIdeaModel.children.every { it.children.isEmpty() } // IdeaModules are always flattened

        when:
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            // IdeaProject, GradleProject, IsolatedGradleProject
            modelsCreated(":", 4)
            // IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":lib1", 2)
            modelsCreated(":lib1:lib11", 2)
        }

        then:
        assertIdeaProject(ideaModel, expectedIdeaModel)

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModel(IdeaProject)

        then:
        fixture.assertStateLoaded()
    }

    private void assertIdeaProject(IdeaProject actual, IdeaProject expected) {
        assertProperties(actual, expected, [
            { it.parent },
            { it.name },
            { it.description },
            { it.jdkName },
            { it.languageLevel.level },
        ])

        assertLanguageSettings(actual.javaLanguageSettings, expected.javaLanguageSettings)

        assertMany(actual.children, expected.children, this::assertIdeaModule)
    }

    private void assertIdeaModule(IdeaModule actualModule, IdeaModule expectedModule) {
        assertProperties(actualModule, expectedModule, [
            { it.name },
            { it.projectIdentifier.projectPath },
            { it.projectIdentifier.buildIdentifier.rootDir },
            { it.jdkName },
            { it.gradleProject.path },
            { it.project.languageLevel.level },
            { it.compilerOutput.inheritOutputDirs },
            { it.compilerOutput.outputDir },
            { it.compilerOutput.testOutputDir },
        ])

        assertLanguageSettings(actualModule.javaLanguageSettings, expectedModule.javaLanguageSettings)
        assertMany(actualModule.contentRoots, expectedModule.contentRoots, this::assertContentRoot)
        assertMany(actualModule.dependencies, expectedModule.dependencies, this::assertDependency)
    }

    private void assertContentRoot(IdeaContentRoot actual, IdeaContentRoot expected) {
        assertProperties(actual, expected, [
            { it.rootDirectory },
            { it.excludeDirectories },
        ])
    }

    private void assertDependency(IdeaDependency actual, IdeaDependency expected) {
        assertProperties(actual, expected, [
            { it.scope.scope },
            { it.exported },
        ])

        if (expected instanceof IdeaModuleDependency) {
            assertProperties(actual, expected, [
                { it.targetModuleName },
            ])
        }

        if (expected instanceof IdeaSingleEntryLibraryDependency) {
            assertProperties(actual, expected, [
                { it.file },
                { it.source },
                { it.javadoc },
                { it.exported },
            ])
        }
    }

    private void assertLanguageSettings(IdeaJavaLanguageSettings actual, IdeaJavaLanguageSettings expected) {
        assertProperties(actual, expected, [
            { it.languageLevel },
            { it.targetBytecodeVersion },
            { it.jdk?.javaVersion },
            { it.jdk?.javaHome },
        ])
    }

    private static <T> void assertMany(DomainObjectSet<T> actual, DomainObjectSet<T> expected, BiConsumer<T, T> assertion) {
        actual.size() == expected.size()
        [actual, expected].collect { it.all }
            .transpose()
            .each { actualItem, expectedItem ->
                assertion(actualItem, expectedItem)
            }
    }

    private static <T> void assertProperties(T actual, T expected, List<Function<T, ?>> properties) {
        assert (actual == null) == (expected == null)
        if (expected == null) {
            return
        }

        for (def prop in properties) {
            assert prop(actual) == prop(expected)
        }
    }

}
