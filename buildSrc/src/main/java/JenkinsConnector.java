import hudson.cli.CLI;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.ClassLoaderHolder;
import hudson.remoting.DelegatingCallable;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecuter;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

public class JenkinsConnector {

    public Channel connectToJenkins(String url) throws Exception {

        CLI cli = new CLI(new URL(url));
        try {
            // this is the private key that authenticates ourselves to the server
//            KeyPair key = cli.loadKey(new File("./id_rsa_cli"));

            // perform authentication, and in the end obtain the public key that identifies the server
            // (the equivalent of SSH host key.) In this demo, I'm not verifying that we are talking who
            // we are supposed to be talking to, but you can do so by comparing the public key to the record.
//            PublicKey server = cli.authenticate(Collections.singleton(key));
//            System.out.println("Server key is " + server);

            // by default, CLI connections are restricted capability-wise, to protect servers from clients.
            // But now we want to start using the channel directly with its full capability, so we try
            // to upgrade the connection. This requires the administer access to the system.
            cli.upgrade();

            // with that, we can now directly use Channel and do all the operations that it can do.
            Channel channel = cli.getChannel();

            // execute a closure on the server, send the return value (or exception) back.
            // note that Jenkins server doesn't have this code on its JVM, but the remoting layer is transparently
            // sending that for you.
//            int r = channel.call(new Callable<Integer, RuntimeException>() {
//                public Integer call() {
//                    // this portion executes inside the Jenkins server JVM.
//                    return 3;
//                }
//            });
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
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
            JUnitTestClassExecuter testClassExecuter = new JUnitTestClassExecuter(Thread.currentThread().getContextClassLoader(), new DummyJUnitSpec(), new MyRunListener(), new DummyTestClassExecutionListener());
            testClassExecuter.execute(testName);
            System.out.println(Channel.current().getName());
            testResultProcessor.failure(null, new Exception());
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
                System.out.println("completed!: "+ o.toString());
            }

            @Override
            public void output(Object o, TestOutputEvent testOutputEvent) {
                //To change body of implemented methods use File | Settings | File Templates.
            }

            @Override
            public void failure(Object o, Throwable throwable) {
                System.out.println("We got called");
            }
        };
    }


}
