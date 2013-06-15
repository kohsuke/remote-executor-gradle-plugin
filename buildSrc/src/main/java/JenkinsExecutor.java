import events.TestRunEndedEvent;
import events.TestRunStartedEvent;
import hudson.remoting.Callable;
import hudson.remoting.Pipe;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class JenkinsExecutor implements Callable<Void, IOException>
{
  private final Pipe pipe;

  public JenkinsExecutor(Pipe pipe)
  {
    this.pipe = pipe;
  }

  public Void call() throws IOException
  {
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(pipe.getOut());
    objectOutputStream.writeObject(new TestRunStartedEvent("foobarSuite", 10));

    try
    {
      Thread.sleep(5000);
    } catch (InterruptedException e)
    {
    }

    objectOutputStream.writeObject(new TestRunEndedEvent());
    return null;
  }
}