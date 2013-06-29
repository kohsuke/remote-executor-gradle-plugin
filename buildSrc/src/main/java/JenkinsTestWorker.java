import hudson.remoting.Channel;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.worker.RemoteTestClassProcessor;
import org.gradle.internal.UncheckedException;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

public class JenkinsTestWorker implements Action<WorkerProcessContext>, RemoteTestClassProcessor, Serializable {
    public static final String WORKER_ID_SYS_PROPERTY = "org.gradle.test.worker";
    private static final Logger LOGGER = LoggerFactory.getLogger(JenkinsTestWorker.class);
    private final String jenkinsUrl;
    private final WorkerTestClassProcessorFactory factory;
    private CountDownLatch completed;
    private TestClassProcessor processor;
    private TestResultProcessor resultProcessor;
    private Channel channel;

    public JenkinsTestWorker(String jenkinsUrl, WorkerTestClassProcessorFactory factory) {
        this.jenkinsUrl = jenkinsUrl;
        this.factory = factory;
    }

    private void startReceivingTests(WorkerProcessContext workerProcessContext) {
        try {
            ClassLoader applicationClassLoader = workerProcessContext.getApplicationClassLoader();
            processor = channel.call(new CreateJenkinsTestClassProcessorCallable(factory, applicationClassLoader));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ObjectConnection serverConnection = workerProcessContext.getServerConnection();
        TestResultProcessor localResultProcessor = serverConnection.addOutgoing(TestResultProcessor.class);
        resultProcessor = channel.export(TestResultProcessor.class, localResultProcessor);
        serverConnection.addIncoming(RemoteTestClassProcessor.class, this);
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

        try {
            channel = new JenkinsConnector().connectToJenkins(jenkinsUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        startReceivingTests(workerProcessContext);

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
            try {
                if (channel != null) {
                    channel.close();
                    LOGGER.info("{} finished executing tests. - Waiting for Channel to close", workerProcessContext.getDisplayName());
                    Thread.sleep(2000);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
