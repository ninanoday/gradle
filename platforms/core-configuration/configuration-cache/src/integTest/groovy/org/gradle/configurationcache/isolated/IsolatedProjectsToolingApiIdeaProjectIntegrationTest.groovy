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

import org.gradle.api.JavaVersion
import org.gradle.tooling.model.idea.BasicIdeaProject
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaJavaLanguageSettings
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

import static org.gradle.configurationcache.isolated.ToolingModelChecker.checkGradleProject
import static org.gradle.configurationcache.isolated.ToolingModelChecker.checkModel
import static org.gradle.configurationcache.isolated.ToolingModelChecker.checkProjectIdentifier

class IsolatedProjectsToolingApiIdeaProjectIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {

    def "can fetch IdeaProject model for root and re-fetch cached"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            // IdeaProject, intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", 3)
        }

        then:
        ideaModel.name == "root"

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModel(IdeaProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch BasicIdeaProject model for root and re-fetch cached"() {
        settingsFile << """
            rootProject.name = 'root'
        """

        when:
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateStored {
            // BasicIdeaProject, intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", 3)
        }

        then:
        ideaModel.name == "root"

        when:
        executer.withArguments(ENABLE_CLI)
        fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateLoaded()
    }

    def "can fetch IdeaProject model for empty projects"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
            include(":lib1:lib11")
        """

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.modules.size() == 3
        expectedIdeaModel.modules.every { it.children.isEmpty() } // IdeaModules are always flattened

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            // IdeaProject, intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", 3)
            // intermediate IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":lib1", 2)
            modelsCreated(":lib1:lib11", 2)
        }

        then:
        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

    def "can fetch IdeaProject model for java projects"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
        """

        file("lib1/build.gradle") << """
            plugins {
                id 'java'
            }
            java.targetCompatibility = JavaVersion.VERSION_1_8
            java.sourceCompatibility = JavaVersion.VERSION_1_8
        """

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.modules.name == ["root", "lib1"]
        expectedIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        expectedIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            // IdeaProject, intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", 3)
            // intermediate IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":lib1", 2)
        }

        then:
        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

    def "can fetch IdeaProject model for projects with java and idea plugins"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":lib1")
            include(":lib2")
        """

        file("lib1/build.gradle") << """
            import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
            plugins {
                id 'java'
                id 'idea'
            }
            idea.module.languageLevel = new IdeaLanguageLevel(7)
            idea.module.targetBytecodeVersion = JavaVersion.VERSION_1_7
            java.targetCompatibility = JavaVersion.VERSION_1_8
            java.sourceCompatibility = JavaVersion.VERSION_1_8
        """

        file("lib2/build.gradle") << """
            import org.gradle.plugins.ide.idea.model.IdeaLanguageLevel
            plugins {
                id 'idea'
            }
            idea.module.languageLevel = new IdeaLanguageLevel(21)
            idea.module.targetBytecodeVersion = JavaVersion.VERSION_21
        """

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = fetchModel(IdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        expectedIdeaModel.javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_8
        expectedIdeaModel.javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_8
        expectedIdeaModel.modules.name == ["root", "lib1", "lib2"]
        expectedIdeaModel.modules[1].javaLanguageSettings.languageLevel == JavaVersion.VERSION_1_7
        expectedIdeaModel.modules[1].javaLanguageSettings.targetBytecodeVersion == JavaVersion.VERSION_1_7
        expectedIdeaModel.modules[2].javaLanguageSettings == null

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(IdeaProject)

        then:
        fixture.assertStateStored {
            // IdeaProject, intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", 3)
            // intermediate IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":lib1", 2)
            modelsCreated(":lib2", 2)
        }

        then:
        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

    def "can fetch BasicIdeaProject model without resolving external dependencies"() {
        settingsFile << """
            rootProject.name = 'root'
            include(":api")
            include(":impl")
        """

        file("api/build.gradle") << """
            plugins {
                id 'java'
            }
        """

        file("impl/build.gradle") << """
            plugins {
                id 'java'
                id 'idea'
            }

            dependencies {
                implementation(project(":api"))
                testImplementation("i.dont:Exist:2.4")
            }
        """

        when: "fetching without Isolated Projects"
        def expectedIdeaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertNoConfigurationCache()
        with(expectedIdeaModel.children.find { it.name == "impl" }) { impl ->
            impl.dependencies.size() == 1
            def apiDep = impl.dependencies[0] as IdeaModuleDependency
            apiDep.targetModuleName == "api"
        }

        when: "fetching with Isolated Projects"
        executer.withArguments(ENABLE_CLI)
        def ideaModel = fetchModel(BasicIdeaProject)

        then:
        fixture.assertStateStored {
            // BasicIdeaProject, intermediate IsolatedGradleProjectInternal, IsolatedIdeaModuleInternal
            modelsCreated(":", 3)
            // intermediate IsolatedGradleProject, IsolatedIdeaModule
            modelsCreated(":api", 2)
            modelsCreated(":impl", 2)
        }

        then:
        checkIdeaProject(ideaModel, expectedIdeaModel)
    }

    private static void checkIdeaProject(IdeaProject actual, IdeaProject expected) {
        checkModel(actual, expected, [
            { it.parent },
            { it.name },
            { it.description },
            { it.jdkName },
            { it.languageLevel.level },
            [{ it.children }, { a, e -> checkIdeaModule(a, e) }],
        ])
    }

    private static void checkIdeaModule(IdeaModule actualModule, IdeaModule expectedModule) {
        checkModel(actualModule, expectedModule, [
            { it.name },
            [{ it.projectIdentifier }, { a, e -> checkProjectIdentifier(a, e) }],
            [{ it.javaLanguageSettings }, { a, e -> checkLanguageSettings(a, e) }],
            { it.jdkName },
            [{ it.contentRoots }, { a, e -> checkContentRoot(a, e) }],
            [{ it.gradleProject }, { a, e -> checkGradleProject(a, e) }],
            { it.project.languageLevel.level }, // shallow check to avoid infinite recursion
            { it.compilerOutput.inheritOutputDirs },
            { it.compilerOutput.outputDir },
            { it.compilerOutput.testOutputDir },
            [{ it.dependencies }, { a, e -> checkDependency(a, e) }],
        ])
    }

    private static void checkContentRoot(IdeaContentRoot actual, IdeaContentRoot expected) {
        checkModel(actual, expected, [
            { it.rootDirectory },
            { it.excludeDirectories },
        ])
    }

    private static void checkDependency(IdeaDependency actual, IdeaDependency expected) {
        checkModel(actual, expected, [
            { it.scope.scope },
            { it.exported },
        ])

        if (expected instanceof IdeaModuleDependency) {
            checkModel(actual, expected, [
                { it.targetModuleName },
            ])
        }

        if (expected instanceof IdeaSingleEntryLibraryDependency) {
            checkModel(actual, expected, [
                { it.file },
                { it.source },
                { it.javadoc },
                { it.exported },
            ])
        }
    }

    private static void checkLanguageSettings(IdeaJavaLanguageSettings actual, IdeaJavaLanguageSettings expected) {
        checkModel(actual, expected, [
            { it.languageLevel },
            { it.targetBytecodeVersion },
            { it.jdk?.javaVersion },
            { it.jdk?.javaHome },
        ])
    }
}
