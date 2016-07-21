/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.gradleware.tooling.toolingmodel.repository.internal

import com.google.common.collect.ImmutableList
import com.google.common.eventbus.EventBus
import com.google.common.eventbus.Subscribe
import com.gradleware.tooling.spock.VerboseUnroll
import com.gradleware.tooling.toolingclient.GradleDistribution
import com.gradleware.tooling.toolingmodel.OmniGradleBuild
import com.gradleware.tooling.toolingmodel.OmniGradleProject
import com.gradleware.tooling.toolingmodel.Path
import com.gradleware.tooling.toolingmodel.repository.*
import org.gradle.api.specs.Spec
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProgressListener

import java.util.concurrent.atomic.AtomicReference

@VerboseUnroll(formatter = GradleDistributionFormatter.class)
class GradleBuildModelRepositoryTest extends ModelRepositorySpec {

    def "send event after cache update"(GradleDistribution distribution, Environment environment) {
        given:
        def fixedRequestAttributes = new FixedRequestAttributes(directoryProvider.testDirectory, null, distribution, null, ImmutableList.of(), ImmutableList.of())
        def transientRequestAttributes = new TransientRequestAttributes(true, null, null, null, ImmutableList.of(Mock(ProgressListener)), ImmutableList.of(Mock(org.gradle.tooling.events.ProgressListener)), GradleConnector.newCancellationTokenSource().token())
        def repository = new DefaultSingleBuildModelRepository(fixedRequestAttributes, toolingClient, new EventBus(), environment)

        AtomicReference<GradleBuildUpdateEvent> publishedEvent = new AtomicReference<>();
        AtomicReference<OmniGradleBuild> modelInRepository = new AtomicReference<>();
        repository.register(new Object() {

            @SuppressWarnings("GroovyUnusedDeclaration")
            @Subscribe
            public void listen(GradleBuildUpdateEvent event) {
                publishedEvent.set(event)
                modelInRepository.set(repository.fetchGradleBuild(transientRequestAttributes, FetchStrategy.FROM_CACHE_ONLY))
            }
        })

        when:
        OmniGradleBuild gradleBuild = repository.fetchGradleBuild(transientRequestAttributes, FetchStrategy.LOAD_IF_NOT_CACHED)

        then:
        gradleBuild != null
        gradleBuild.rootProject != null
        gradleBuild.rootProject.name == 'my root project'
        gradleBuild.rootProject.description == 'a sample root project'
        gradleBuild.rootProject.path == Path.from(':')
        gradleBuild.rootProject.projectIdentifier
        if (higherOrEqual('2.4', distribution)) {
            assert gradleBuild.rootProject.projectDirectory.get().absolutePath == directoryProvider.testDirectory.absolutePath
        } else {
            assert !gradleBuild.rootProject.projectDirectory.isPresent()
        }
        if (higherOrEqual('2.0', distribution)) {
            assert gradleBuild.rootProject.buildDirectory.get().absolutePath == directoryProvider.file('build').absolutePath
        } else {
            assert !gradleBuild.rootProject.buildDirectory.isPresent()
        }
        if (higherOrEqual('1.8', distribution)) {
            assert gradleBuild.rootProject.buildScript.get().sourceFile.absolutePath == directoryProvider.file('build.gradle').absolutePath
        } else {
            assert !gradleBuild.rootProject.buildScript.isPresent()
        }
        gradleBuild.rootProject.projectTasks.findAll { !ImplicitTasks.ALL.contains(it.name) }.size() == 1
        gradleBuild.rootProject.root == gradleBuild.rootProject
        gradleBuild.rootProject.parent == null
        gradleBuild.rootProject.children.size() == 2
        gradleBuild.rootProject.children*.name == ['sub1', 'sub2']
        gradleBuild.rootProject.children*.description == ['sub project 1', 'sub project 2']
        gradleBuild.rootProject.children*.path.path == [':sub1', ':sub2']
        gradleBuild.rootProject.children*.root == [gradleBuild.rootProject, gradleBuild.rootProject]
        gradleBuild.rootProject.children*.parent == [gradleBuild.rootProject, gradleBuild.rootProject]
        gradleBuild.rootProject.all.size() == 4
        gradleBuild.rootProject.all*.name == ['my root project', 'sub1', 'sub2', 'subSub1']

        def projectSub1 = gradleBuild.rootProject.tryFind({ OmniGradleProject input ->
            input.path.path == ':sub1'
        } as Spec).get()
        projectSub1.projectTasks.findAll { !ImplicitTasks.ALL.contains(it.name) }.size() == 2

        def myFirstTaskOfSub1 = projectSub1.projectTasks.findAll { !ImplicitTasks.ALL.contains(it.name) }[0]
        myFirstTaskOfSub1.name == 'myFirstTaskOfSub1'
        myFirstTaskOfSub1.description == '1st task of sub1'
        myFirstTaskOfSub1.path.path == ':sub1:myFirstTaskOfSub1'
        myFirstTaskOfSub1.isPublic() == (!higherOrEqual('2.1', distribution) || higherOrEqual('2.3', distribution))
        if (higherOrEqual('2.5', distribution)) {
            assert myFirstTaskOfSub1.group.get() == 'build'
        } else {
            assert !myFirstTaskOfSub1.group.present
        }

        def mySecondTaskOfSub1 = projectSub1.projectTasks.findAll { !ImplicitTasks.ALL.contains(it.name) }[1]
        mySecondTaskOfSub1.name == 'mySecondTaskOfSub1'
        mySecondTaskOfSub1.description == '2nd task of sub1'
        mySecondTaskOfSub1.path.path == ':sub1:mySecondTaskOfSub1'
        mySecondTaskOfSub1.isPublic() == !higherOrEqual('2.1', distribution)
        if (higherOrEqual('2.5', distribution)) {
            assert mySecondTaskOfSub1.group.get() == null
        } else {
            assert !mySecondTaskOfSub1.group.present
        }

        def projectSub2 = gradleBuild.rootProject.tryFind({ OmniGradleProject input ->
            input.path.path == ':sub2'
        } as Spec).get()
        projectSub2.taskSelectors.findAll { !ImplicitTasks.ALL.contains(it.name) }.size() == 5

        def myTaskSelector = projectSub2.taskSelectors.find { it.name == 'myTask' }
        myTaskSelector.name == 'myTask'
        myTaskSelector.description == 'another task of sub2'
        myTaskSelector.projectPath.path == ':sub2'
        myTaskSelector.isPublic() == (!higherOrEqual('2.1', distribution) || higherOrEqual('2.3', distribution))
        myTaskSelector.selectedTaskPaths*.path as List == [':sub2:myTask', ':sub2:subSub1:myTask']

        def event = publishedEvent.get()
        event != null
        event.gradleBuild == gradleBuild

        def model = modelInRepository.get()
        model == gradleBuild

        where:
        [distribution, environment] << runInAllEnvironmentsForGradleTargetVersions(">=1.2")
    }

    def "when exception is thrown"(GradleDistribution distribution, Environment environment) {
        given:
        def fixedRequestAttributes = new FixedRequestAttributes(directoryProviderErroneousBuildFile.testDirectory, null, distribution, null, ImmutableList.of(), ImmutableList.of())
        def transientRequestAttributes = new TransientRequestAttributes(true, null, null, null, ImmutableList.of(Mock(ProgressListener)), ImmutableList.of(Mock(org.gradle.tooling.events.ProgressListener)), GradleConnector.newCancellationTokenSource().token())
        def repository = new DefaultSingleBuildModelRepository(fixedRequestAttributes, toolingClient, new EventBus(), environment)

        AtomicReference<GradleBuildUpdateEvent> publishedEvent = new AtomicReference<>();
        repository.register(new Object() {

            @SuppressWarnings("GroovyUnusedDeclaration")
            @Subscribe
            public void listen(GradleBuildUpdateEvent event) {
                publishedEvent.set(event)
            }
        })

        when:
        repository.fetchGradleBuild(transientRequestAttributes, FetchStrategy.LOAD_IF_NOT_CACHED)

        then:
        thrown(GradleConnectionException)

        publishedEvent.get() == null

        where:
        [distribution, environment] << runInAllEnvironmentsForGradleTargetVersions(">=1.2")
    }
}