import hudson.cli.CLI;
import hudson.remoting.Callable;
import hudson.remoting.Channel;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Collections;

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
}
