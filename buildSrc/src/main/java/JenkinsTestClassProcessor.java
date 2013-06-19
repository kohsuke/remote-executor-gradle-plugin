import hudson.cli.CLI;
import hudson.remoting.Channel;
import hudson.remoting.ClassLoaderHolder;
import hudson.remoting.DelegatingCallable;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.Factory;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.WorkerProcessBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class JenkinsTestClassProcessor implements TestClassProcessor {
    private String jenkinsUrl;
    private final WorkerTestClassProcessorFactory processorFactory;
    private final JavaForkOptions options;
    private final Iterable<File> classPath;
    private final Action<WorkerProcessBuilder> buildConfigAction;
    private OurRemoteTestClassProcessor remoteProcessor;
    private TestResultProcessor resultProcessor;
    private Channel channel;
    private URLClassLoader classLoader;
    private RemoteTestClassProcessorCreator remoteTestClassProcessorCreator;

    public JenkinsTestClassProcessor(String jenkinsUrl, WorkerTestClassProcessorFactory processorFactory, JavaForkOptions options, Iterable<File> classPath, Action<WorkerProcessBuilder> buildConfigAction) {
        this.jenkinsUrl = jenkinsUrl;
        this.processorFactory = processorFactory;
        this.options = options;
        this.classPath = classPath;
        this.buildConfigAction = buildConfigAction;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        this.resultProcessor = resultProcessor;
        System.out.println("Result processor set");
    }

    public void processTestClass(TestClassRunInfo testClass) {
        if (remoteProcessor == null) {
            try {
                JenkinsConnector jenkinsConnector = new JenkinsConnector();

                channel = jenkinsConnector.connectToJenkins(jenkinsUrl);
//                channel = jenkinsConnector.connectToJenkinsMasterViaUpgrade(jenkinsUrl);
                List<URL> urls = new ArrayList<URL>();
                for (File file : classPath) {
                    urls.add(file.toURI().toURL());
                }
                classLoader = new URLClassLoader(urls.toArray(new URL[0]));
                remoteTestClassProcessorCreator = new RemoteTestClassProcessorCreator(classLoader, channel.export(OurTestResultProcessor.class, new GradleToOurTestResultProcessorAdaptor(resultProcessor)), processorFactory);
                remoteProcessor = channel.call(remoteTestClassProcessorCreator);
                System.out.println("aquired remote Processor");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        remoteProcessor.processTestClass(testClass);
    }

    public static class RemoteTestClassProcessorCreator implements DelegatingCallable<OurRemoteTestClassProcessor, Exception> {
        public final ClassLoaderHolder testClassLoader;
        public final OurTestResultProcessor testResultProcessor;
        private final WorkerTestClassProcessorFactory processorFactory;

        public RemoteTestClassProcessorCreator(ClassLoader cl, OurTestResultProcessor testResultProcessor, WorkerTestClassProcessorFactory processorFactory) {
            this.processorFactory = processorFactory;
            this.testResultProcessor = testResultProcessor;
            testClassLoader = new ClassLoaderHolder(cl);
        }

        public OurRemoteTestClassProcessor call() throws Exception {
            JenkinsTestWorker jenkinsTestWorker = new JenkinsTestWorker(processorFactory);
            jenkinsTestWorker.execute(this);
            return Channel.current().export(OurRemoteTestClassProcessor.class, jenkinsTestWorker);
        }

        @Override
        public ClassLoader getClassLoader() {
            return testClassLoader.get();
        }
    }

    public void stop() {
        try {
        if (remoteProcessor != null) {
            remoteProcessor.stop();
        }
        } finally {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
    }
}
