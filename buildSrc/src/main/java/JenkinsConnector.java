import com.google.common.collect.Lists;
import hudson.cli.CLI;
import hudson.remoting.Channel;
import hudson.remoting.FastPipedInputStream;
import hudson.remoting.FastPipedOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public class JenkinsConnector implements Serializable {
    private static final Logger LOGGER = LoggerFactory.getLogger(JenkinsConnector.class);

    private URL url;
    private Thread cliThread;

    /**
     * @param url
     *      URL of Jenkins master.
     */
    public JenkinsConnector(String url) {
        try {
            this.url = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Uses the Jenkins remote terminal access plugin to launch a JVM on one of the nodes under the control of Jenkins,
     * then establish a {@link Channel} to it and returns it.
     *
     * @param channelName
     *      Name set to the channel. This is primarily used for diagnosis.
     * @param javaOpts
     *      Additional JVM options to be set on the newly launched JVM.
     */
    public Channel connectToJenkins(String channelName, final List<String> javaOpts) {

        try {
            final CLI cli = new CLI(url);
            final FastPipedInputStream p1i = new FastPipedInputStream();
            final FastPipedOutputStream p2o = new FastPipedOutputStream();

            final FastPipedInputStream p2i = new FastPipedInputStream(p2o);
            final FastPipedOutputStream p1o = new FastPipedOutputStream(p1i);

            cliThread = new Thread() {
                @Override
                public void run() {
                    List<String> args = Lists.newArrayList();
                    args.add("channel-process");
                    for (String javaOpt : javaOpts) {
                        args.addAll(Arrays.asList("-J", javaOpt));
                    }
                    args.add("master");
                    int r = cli.execute(args, p2i, p1o, System.err);
                    LOGGER.debug("CLI Command finished: " + r);
                    try {
                        cli.close();
                    } catch (Exception e) {
                        LOGGER.error("Error on closing CLI");
                    }
                }
            };
            cliThread.start();
            Channel ch = new Channel(channelName, Executors.newCachedThreadPool(), p1i, p2o);
            return ch;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void waitForCliConnectionToClose() throws InterruptedException {
        cliThread.join();
    }

}
