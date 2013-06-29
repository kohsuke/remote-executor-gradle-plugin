import hudson.remoting.Channel;
import hudson.remoting.ClassLoaderHolder;
import hudson.remoting.DelegatingCallable;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.worker.RemoteTestClassProcessor;
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.UncheckedException;
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

import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class JenkinsTestWorker implements Action<WorkerProcessContext>, RemoteTestClassProcessor, Serializable {
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    private static final Logger LOGGER = LoggerFactory.getLogger(JenkinsTestWorker.class);
    private String jenkinsUrl;
    private final WorkerTestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;
    private Channel channel;

    public JenkinsTestWorker(String jenkinsUrl, WorkerTestClassProcessorFactory factory) {
        this.jenkinsUrl = jenkinsUrl;
        this.factory = factory;
    }

    private String getChannelName() {
        return Channel.current().getName();
    }

    private void startReceivingTests(WorkerProcessContext workerProcessContext, ServiceRegistry testServices) {
        try {
            ClassLoader applicationClassLoader = workerProcessContext.getApplicationClassLoader();
            applicationClassLoader.loadClass("com.tngtech.test.java.junit.dataprovider.DataProviderSimpleAcceptanceTest");
            processor = channel.call(new CreateRemoteTestClassProcessor(factory, applicationClassLoader));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ObjectConnection serverConnection = workerProcessContext.getServerConnection();
        TestResultProcessor localResultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);
        resultProcessor = channel.export(TestResultProcessor.class, localResultProcessor);
        serverConnection.addIncoming(RemoteTestClassProcessor.class, this);
    }

    private static class CreateRemoteTestClassProcessor implements DelegatingCallable<TestClassProcessor, RuntimeException> {

        private final WorkerTestClassProcessorFactory targetProcessor;
        private final ClassLoaderHolder classLoaderHolder;

        CreateRemoteTestClassProcessor(WorkerTestClassProcessorFactory targetProcessor, ClassLoader applicationClassLoader) {
            this.targetProcessor = targetProcessor;
            this.classLoaderHolder = new ClassLoaderHolder(applicationClassLoader);
        }

        @Override
        public TestClassProcessor call() throws RuntimeException {
            RemoteTestFrameworkServiceRegistry testServices = new RemoteTestFrameworkServiceRegistry(CreateRemoteTestClassProcessor.this);
            IdGenerator<Object> idGenerator = testServices.get(IdGenerator.class);
            try {
                this.getClassLoader().loadClass("com.tngtech.test.java.junit.dataprovider.DataProviderSimpleAcceptanceTest");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }

            TestClassProcessor targetProcessor = this.targetProcessor.create(testServices);
            targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(),
                    "Test", new TrueTimeProvider());
            ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(
                    TestClassProcessor.class, targetProcessor, classLoaderHolder.get());
            TestClassProcessor remoteProcessor = proxy.getSource();

            Channel currentChannel = Channel.current();
            currentChannel.pin(remoteProcessor);
            return currentChannel.export(TestClassProcessor.class, remoteProcessor);
//            serverConnection.addIncoming(RemoteTestClassProcessor.class, this);
        }

        public Object getWorkerId() {
            return Channel.current().getName();  //To change body of created methods use File | Settings | File Templates.
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoaderHolder.get();
        }
    }

    public void startProcessing() {
        Thread.currentThread().setName("Test worker");
        processor.startProcessing(resultProcessor);
    }

    public void processTestClass(final TestClassRunInfo testClass) {
        Thread.currentThread().setName("Test worker");
        try {
            processor.processTestClass(testClass);
        } finally {
            // Clean the interrupted status
            Thread.interrupted();
        }
    }

    public void stop() {
        Thread.currentThread().setName("Test worker");
        try {
            processor.stop();
        } finally {
            completed.countDown();
        }
    }

    @Override
    public void execute(WorkerProcessContext workerProcessContext) {
        LOGGER.info("{} executing tests.", workerProcessContext.getDisplayName());

        completed = new CountDownLatch(1);

        System.setProperty(WORKER_ID_SYS_PROPERTY, workerProcessContext.getWorkerId().toString());

        DefaultServiceRegistry testServices = new TestFrameworkServiceRegistry(workerProcessContext);

        try {
            channel = new JenkinsConnector().connectToJenkins(jenkinsUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        startReceivingTests(workerProcessContext, testServices);

        try {
            try {
                completed.await();
            } catch (InterruptedException e) {
                throw new UncheckedException(e);
            }
        } finally {
            LOGGER.info("{} finished executing tests.", workerProcessContext.getDisplayName());
            // Clean out any security manager the tests might have installed
            System.setSecurityManager(null);
            testServices.close();
            try {
                if (channel != null) {
                    channel.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class TestFrameworkServiceRegistry extends DefaultServiceRegistry {
        private final WorkerProcessContext workerProcessContext;

        public TestFrameworkServiceRegistry(WorkerProcessContext workerProcessContext) {
            this.workerProcessContext = workerProcessContext;
        }

        protected IdGenerator<Object> createIdGenerator() {
            return new CompositeIdGenerator(workerProcessContext.getWorkerId(), new LongIdGenerator());
        }

        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        protected ActorFactory createActorFactory() {
            return new DefaultActorFactory(get(ExecutorFactory.class));
        }
    }

    private static class RemoteTestFrameworkServiceRegistry extends DefaultServiceRegistry {
        private final CreateRemoteTestClassProcessor createRemoteTestClassProcessor;

        public RemoteTestFrameworkServiceRegistry(CreateRemoteTestClassProcessor createRemoteTestClassProcessor) {
            this.createRemoteTestClassProcessor = createRemoteTestClassProcessor;
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
