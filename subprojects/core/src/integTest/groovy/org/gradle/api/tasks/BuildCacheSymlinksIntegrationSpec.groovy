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

package org.gradle.api.tasks

import org.apache.commons.io.FileUtils
import org.gradle.api.file.LinksStrategy
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import java.nio.file.Files
import java.nio.file.Path

@Requires(UnitTestPreconditions.Symlinks)
class BuildCacheSymlinksIntegrationSpec extends AbstractIntegrationSpec {

    TestFile inputDirectory
    TestFile outputDirectory
    TestFile originalDir
    TestFile originalFile
    TestFile fileLink
    TestFile dirLink

    def setup() {
        inputDirectory = createDir("input")
        outputDirectory = createDir("output")
        executer.withStacktraceEnabled()

        originalDir = inputDirectory.createDir("original")
        originalFile = originalDir.createFile("original.txt") << "some text"
        fileLink = originalDir.file("link").createLink(originalFile.getRelativePathFromBase())
        dirLink = inputDirectory.file("linkDir").createLink(originalDir.getRelativePathFromBase())
    }

    def "copying files again should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        checkUpToDate(linksStrategy, upToDate) {
            // do nothing
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | true
        LinksStrategy.FOLLOW       | true
    }

    def "when target content is changed, the task should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        checkUpToDate(linksStrategy, upToDate) {
            originalFile << "new text"
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | false
    }

    def "when target is changed, the task should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        def sameContentAsOriginal = originalDir.createFile("original2.txt") << originalFile.text

        checkUpToDate(linksStrategy, upToDate) {
            fileLink.delete()
            Files.createSymbolicLink(fileLink.toPath(), Path.of(sameContentAsOriginal.getRelativePathFromBase()))
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | false // should be true
    }

    def "when dir link is replaced by its contents, the task should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        checkUpToDate(linksStrategy, upToDate) {
            dirLink.delete()
            FileUtils.copyDirectory(originalDir, dirLink)
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | false // should be true
    }

    def "when file link is replaced by its contents, the task should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        checkUpToDate(linksStrategy, upToDate) {
            fileLink.delete()
            FileUtils.copyFile(originalFile, fileLink)
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | false // should be true
    }

    def "when file is replaced by a link pointing to the original, the task should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        fileLink.delete()
        dirLink.delete()
        originalDir.createFile(fileLink.name) << originalFile.text

        checkUpToDate(linksStrategy, upToDate) {
            fileLink.delete()
            originalDir.file(fileLink.name).createLink(originalFile.getRelativePathFromBase())
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | false // should be true
    }

    def "when configuration is changed, the task should have up-to-date=#upToDate if linksStrategy was #linksStrategy and became #linksStrategyAfter"() {
        checkUpToDate(linksStrategy, upToDate) {
            buildKotlinFile.text = """
            tasks.register<Copy>("cp") {
                linksStrategy = LinksStrategy.$linksStrategyAfter
                from("${inputDirectory.name}")

                into("${outputDirectory.name}")
            }
            """
        }

        where:
        linksStrategy              | linksStrategyAfter         | upToDate
        LinksStrategy.PRESERVE_ALL | LinksStrategy.FOLLOW       | false
        LinksStrategy.FOLLOW       | LinksStrategy.PRESERVE_ALL | false
    }

    def "when a relative link is replaced to absolute, the task should have up-to-date=#upToDate if linksStrategy=#linksStrategy"() {
        checkUpToDate(linksStrategy, upToDate) {
            fileLink.delete()
            Files.createSymbolicLink(fileLink.toPath(), Path.of(originalFile.getCanonicalPath()))
        }

        where:
        linksStrategy              | upToDate
        LinksStrategy.PRESERVE_ALL | false
        LinksStrategy.FOLLOW       | false // should be true
    }

    private def checkUpToDate(LinksStrategy linksStrategy, Boolean shouldBeUpToDate, Closure change) {
        buildKotlinFile << """
            tasks.register<Copy>("cp") {
                linksStrategy = LinksStrategy.$linksStrategy
                from("${inputDirectory.name}")

                into("${outputDirectory.name}")
            }
            """
        succeeds(":cp")
        executed(":cp")

        change()

        succeeds(":cp")
        if (shouldBeUpToDate) {
            skipped(":cp")
        } else {
            executedAndNotSkipped(":cp")
        }
    }
}
