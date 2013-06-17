import events.Event;
import events.TestRunEndedEvent;
import events.TestRunStartedEvent;
import hudson.cli.CLI;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import junit.runner.TestRunListener;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecuter;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

public class JenkinsConnector implements Serializable {

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

    private static interface TestRunListenerWorker {
        public void work(TestRunListener listener);
    }

    private Collection<TestRunListener> listeners;

    public JenkinsConnector() {
        listeners = new ArrayList<>();
        listeners.add(new TestRunListenerImpl());
    }

    public Channel executeTests(String url) throws Exception {
        CLI cli = new CLI(new URL(url));
        cli.upgrade();

        Channel channel = cli.getChannel();
        Pipe pipe = Pipe.createRemoteToLocal();

        Future<Void> future = channel.callAsync(new JenkinsExecutor(pipe));

        readOutput(pipe, future);

        channel.close();
        return cli.getChannel();
    }

    private void readOutput(Pipe pipe, Future<Void> future) throws Exception {
        InputStream inputStream = pipe.getIn();
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
        while (!future.isDone() || inputStream.available() > 0) {
            if (inputStream.available() > 0) {
                Event event = (Event) objectInputStream.readObject();
                sendEvent(event);
            } else {
                Thread.sleep(30);
            }
        }
    }

    private void sendEvent(final Event event) throws Exception {
        if (event instanceof TestRunStartedEvent) {
            final TestRunStartedEvent testRunStartedEvent = (TestRunStartedEvent) event;
            workOnListeners(new TestRunListenerWorker() {
                @Override
                public void work(TestRunListener listener) {
                    listener.testRunStarted(testRunStartedEvent.getTestSuiteName(), testRunStartedEvent.getTestCount());
                }
            });
        } else if (event instanceof TestRunEndedEvent) {
            workOnListeners(new TestRunListenerWorker() {
                @Override
                public void work(TestRunListener listener) {
                    listener.testRunEnded(0);
                }
            });
        }
    }

    private void workOnListeners(TestRunListenerWorker testRunListenerWorker) {
        for (TestRunListener listener : listeners) {
            testRunListenerWorker.work(listener);
        }
    }

    public void executeTestOnRemote(Channel channel, final String testName) throws Exception {
        channel.call(new RuntimeExceptionCallable(testName));
        System.out.println("And back");
    }

    private static class RuntimeExceptionCallable implements Callable<Object, Exception> {
        private final String testName;

        public RuntimeExceptionCallable(String testName) {
            this.testName = testName;
        }

        public Object call() throws Exception {
            JUnitTestClassExecuter testClassExecuter = new JUnitTestClassExecuter(this.getClass().getClassLoader(), new DummyJUnitSpec(), new MyRunListener(), new DummyTestClassExecutionListener());
            testClassExecuter.execute(testName);
            System.out.println("We ran the test");
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
