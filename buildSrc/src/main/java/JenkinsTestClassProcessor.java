import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.worker.RemoteTestClassProcessor;
import org.gradle.internal.Factory;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.WorkerProcess;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;

/**
 * {@link TestClassProcessor} that lives inside Gradle JVM and passes execution to remote JVMs over remoting.
 */
public class JenkinsTestClassProcessor implements TestClassProcessor {
    private String jenkinsUrl;
    private Factory<WorkerProcessBuilder> workerFactory;
    private final WorkerTestClassProcessorFactory processorFactory;
    private final JavaForkOptions options;
    private final Iterable<File> classPath;
    private final Action<WorkerProcessBuilder> buildConfigAction;
    /**
     * Remoting proxy to a worker JVM that runs on the client machine.
     */
    private RemoteTestClassProcessor remoteProcessor;
    private WorkerProcess workerProcess;
    private TestResultProcessor resultProcessor;

    public JenkinsTestClassProcessor(String jenkinsUrl, Factory<WorkerProcessBuilder> workerFactory, WorkerTestClassProcessorFactory processorFactory, JavaForkOptions options, Iterable<File> classPath, Action<WorkerProcessBuilder> buildConfigAction) {
        this.jenkinsUrl = jenkinsUrl;
        this.workerFactory = workerFactory;
        this.processorFactory = processorFactory;
        this.options = options;
        this.classPath = classPath;
        this.buildConfigAction = buildConfigAction;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
    }

    public void processTestClass(TestClassRunInfo testClass) {
        if (remoteProcessor == null) {
            WorkerProcessBuilder builder = workerFactory.create();
            builder.applicationClasspath(classPath);
            builder.setLoadApplicationInSystemClassLoader(true);
            builder.worker(new JenkinsTestWorker(jenkinsUrl, processorFactory, options.getAllJvmArgs()));
            // TODO: What are the Java-Options to start the JVM with?
            options.copyTo(builder.getJavaCommand());
            buildConfigAction.execute(builder);

            workerProcess = builder.build();
            workerProcess.start();

            workerProcess.getConnection().addIncoming(TestResultProcessor.class, resultProcessor);
            remoteProcessor = workerProcess.getConnection().addOutgoing(RemoteTestClassProcessor.class);

            remoteProcessor.startProcessing();
        }

        remoteProcessor.processTestClass(testClass);
    }

    public void stop() {
        if (remoteProcessor != null) {
            remoteProcessor.stop();
            workerProcess.waitForStop();
        }
    }
}
