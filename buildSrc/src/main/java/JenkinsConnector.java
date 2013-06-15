import hudson.cli.CLI;
import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.concurrent.Future;

public class JenkinsConnector implements Serializable
{
  public Channel connectToJenkins(String url) throws Exception
  {
    CLI cli = new CLI(new URL(url));
    try
    {
      cli.upgrade();
      Channel channel = cli.getChannel();
      final Pipe pipe = Pipe.createRemoteToLocal();

      Future<Void> future = channel.callAsync(new Callable<Void, IOException>()
      {
        public Void call() throws IOException
        {
          ObjectOutputStream objectOutputStream = new ObjectOutputStream(pipe.getOut());
          objectOutputStream.writeUTF("blubb");
          objectOutputStream.flush();

          return null;
        }
      });

      ObjectInputStream inputStream = new ObjectInputStream(pipe.getIn());
      String text = inputStream.readUTF();
      System.out.println(text);

      channel.close();


    } catch (Exception e)
    {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    return cli.getChannel();
  }
}
