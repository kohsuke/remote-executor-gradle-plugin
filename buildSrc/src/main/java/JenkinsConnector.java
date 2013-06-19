import hudson.cli.CLI;
import hudson.remoting.*;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecuter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class JenkinsConnector implements Serializable {

    public Channel connectToJenkins(String url) throws Exception {

        final CLI cli = new CLI(new URL(url));
        final FastPipedInputStream p1i = new FastPipedInputStream();
        final FastPipedOutputStream p2o = new FastPipedOutputStream();

        final FastPipedInputStream p2i = new FastPipedInputStream(p2o);
        final FastPipedOutputStream p1o = new FastPipedOutputStream(p1i);

        new Thread() {
            @Override
            public void run() {
                int r = cli.execute(Arrays.asList("channel-process", "master"), p2i, p1o, System.err);
                System.out.println(r);
            }
        }.start();
        return new Channel("cli", Executors.newCachedThreadPool(), p1i, p2o);
    }

    public JenkinsConnector() {
    }

    public Channel connectToJenkinsMasterViaUpgrade(String url) throws Exception {
        CLI cli = new CLI(new URL(url));
        cli.upgrade();
        return cli.getChannel();
    }

    public void executeTestOnRemote(Channel channel, final String testName, List<URL> classPath) throws Exception {
        OurTestResultProcessor rp = getOurTestResportProcessor();
        URLClassLoader cl = new URLClassLoader(classPath.toArray(new URL[0]));
        OurTestResultProcessor processor = channel.call(new RuntimeExceptionCallable(cl, testName, channel.export(OurTestResultProcessor.class, rp), channel));
        processor.completed("Awesome", new TestCompleteEvent(34L, TestResult.ResultType.SUCCESS));
        System.out.println("And back");
    }

    private static class RuntimeExceptionCallable implements DelegatingCallable<OurTestResultProcessor, Exception> {
        private final String testName;
        private final ClassLoaderHolder testClassLoader;
        private final OurTestResultProcessor testResultProcessor;

        public RuntimeExceptionCallable(ClassLoader cl, String testName, OurTestResultProcessor testResultProcessor, Channel channel) {
            this.testName = testName;
            this.testResultProcessor = testResultProcessor;
            testClassLoader = new ClassLoaderHolder(cl);
        }

        public OurTestResultProcessor call() throws Exception {
            JUnitTestClassExecuter testClassExecuter = new JUnitTestClassExecuter(testClassLoader.get(), new DummyJUnitSpec(), new MyRunListener(), new DummyTestClassExecutionListener());
            testClassExecuter.execute(testName);
            System.out.println(Channel.current().getName());
            System.out.println("We ran the test");
            return Channel.current().export(OurTestResultProcessor.class, getOurTestResportProcessor());
        }

        @Override
        public ClassLoader getClassLoader() {
            return testClassLoader.get();
        }
    }

    private static OurTestResultProcessor getOurTestResportProcessor() {
        return new OurTestResultProcessor() {
            @Override
            public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
                System.out.println("Something happened");
            }

            @Override
            public void completed(Object o, TestCompleteEvent testCompleteEvent) {
                System.out.println("completed!: " + o.toString());
            }

            @Override
            public void output(Object o, TestOutputEvent testOutputEvent) {
                System.out.println("output!: " + o.toString());
            }

            @Override
            public void failure(Object o, Throwable throwable) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                System.out.println("failure: " + sw.toString());
            }
        };
    }


}
