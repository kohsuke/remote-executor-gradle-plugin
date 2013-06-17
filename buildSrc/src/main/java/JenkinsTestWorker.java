import hudson.remoting.Channel;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ContextClassLoaderProxy;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.actor.internal.DefaultActorFactory;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

public class JenkinsTestWorker implements OurRemoteTestClassProcessor, Serializable {
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    private static final Logger LOGGER = LoggerFactory.getLogger(JenkinsTestWorker.class);
    private final WorkerTestClassProcessorFactory factory;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;
    private DefaultServiceRegistry testServices;

    public JenkinsTestWorker(WorkerTestClassProcessorFactory factory) {
        this.factory = factory;
    }

    public void execute(final JenkinsTestClassProcessor.RemoteTestClassProcessorCreator workerProcessContext) {
        LOGGER.info("{} executing tests.", Channel.current().getName());

        System.setProperty(WORKER_ID_SYS_PROPERTY, Channel.current().getName());

        testServices = new TestFrameworkServiceRegistry();
        startReceivingTests(workerProcessContext, testServices);
        System.out.println("Finished executing");
    }

    private void startReceivingTests(JenkinsTestClassProcessor.RemoteTestClassProcessorCreator remoteTestClassProcessorCreator, ServiceRegistry testServices) {
        TestClassProcessor targetProcessor = factory.create(testServices);
        IdGenerator<Object> idGenerator = testServices.get(IdGenerator.class);

        targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(),
                Channel.current().getName(), new TrueTimeProvider());
        ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(
                TestClassProcessor.class, targetProcessor, remoteTestClassProcessorCreator.testClassLoader.get());
        processor = targetProcessor;

        this.resultProcessor = new OurToGradleTestResultProcessorAdaptor(remoteTestClassProcessorCreator.testResultProcessor);
        processor.startProcessing(resultProcessor);
    }

    public void startProcessing() {
        Thread.currentThread().setName("Test worker");
        processor.startProcessing(resultProcessor);
    }

    public void processTestClass(final TestClassRunInfo testClass) {
        Thread.currentThread().setName("Test worker");
        try {
            if (processor == null) {
                System.out.println("Processor is null!");
            }
            System.out.println("Processing Test Class: " + testClass.getTestClassName());
            processor.processTestClass(testClass);
            System.out.println("Processed Test Class: " + testClass.getTestClassName());
        } finally {
            // Clean the interrupted status
            Thread.interrupted();
        }
    }

    public void stop() {
        Thread.currentThread().setName("Test worker");
        try {
//            processor.stop();
        } finally {
            LOGGER.info("{} finished executing tests.", Channel.current().getName());
            // Clean out any security manager the tests might have installed
            System.setSecurityManager(null);
            testServices.close();

        }
    }

    private static class TestFrameworkServiceRegistry extends DefaultServiceRegistry {
        public TestFrameworkServiceRegistry() {
        }

        protected IdGenerator<Object> createIdGenerator() {
            return new CompositeIdGenerator(Channel.current().getName(), new LongIdGenerator());
        }

        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        protected ActorFactory createActorFactory() {
            return new DefaultActorFactory(get(ExecutorFactory.class));
        }
    }
}
