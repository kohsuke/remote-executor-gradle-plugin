/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.CacheUsage;
import org.gradle.GradleLauncher;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestClassScanner;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.detection.TestFrameworkDetector;
import org.gradle.api.internal.tasks.testing.processors.MaxNParallelTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.RestartEveryNTestClassProcessor;
import org.gradle.api.internal.tasks.testing.processors.TestMainAction;
import org.gradle.api.internal.tasks.testing.worker.ForkingTestClassProcessor;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.testing.Test;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.*;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.nativeplatform.*;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.MessagingServices;
import org.gradle.process.internal.DefaultWorkerProcessFactory;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.process.internal.child.WorkerProcessClassPathProvider;

import java.lang.reflect.Field;

/**
 * The default test class scanner factory.
 *
 * @author Tom Eyckmans
 */
public class JenkinsTestClassExecutor implements TestExecuter {

    private Factory<WorkerProcessBuilder> workerProcessBuilderFactory;

    public JenkinsTestClassExecutor(Factory<WorkerProcessBuilder> workerProcessBuilderFactory) {
        this.workerProcessBuilderFactory = workerProcessBuilderFactory;
    }

    public void execute(final Test testTask, TestResultProcessor testResultProcessor) {
        final TestFramework testFramework = testTask.getTestFramework();
        final WorkerTestClassProcessorFactory testInstanceFactory = testFramework.getProcessorFactory();
        final Factory<TestClassProcessor> forkingProcessorFactory = new Factory<TestClassProcessor>() {
            public TestClassProcessor create() {
                return new JenkinsTestClassProcessor("http://localhost:8080/jenkins", workerProcessBuilderFactory, testInstanceFactory, testTask,
                        testTask.getClasspath(), testFramework.getWorkerConfigurationAction());
            }
        };
        TestClassProcessor processor = new RestartEveryNTestClassProcessor(forkingProcessorFactory, testTask.getForkEvery());


        final FileTree testClassFiles = testTask.getCandidateClassFiles();

        Runnable detector;
        if (testTask.isScanForTestClasses()) {
            TestFrameworkDetector testFrameworkDetector = testTask.getTestFramework().getDetector();
            testFrameworkDetector.setTestClassesDirectory(testTask.getTestClassesDir());
            testFrameworkDetector.setTestClasspath(testTask.getClasspath());
            detector = new DefaultTestClassScanner(testClassFiles, testFrameworkDetector, processor);
        } else {
            detector = new DefaultTestClassScanner(testClassFiles, null, processor);
        }
        new TestMainAction(detector, processor, testResultProcessor, new TrueTimeProvider()).run();
    }
}
