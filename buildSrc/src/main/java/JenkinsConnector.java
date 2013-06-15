import events.Event;
import events.TestRunEndedEvent;
import events.TestRunStartedEvent;
import hudson.cli.CLI;
import hudson.remoting.Channel;
import hudson.remoting.Pipe;
import junit.runner.TestRunListener;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Future;

public class JenkinsConnector implements Serializable
{
  private static interface TestRunListenerWorker
  {
    public void work(TestRunListener listener);
  }

  private Collection<TestRunListener> listeners;

  public JenkinsConnector()
  {
    listeners = new ArrayList<>();
    listeners.add(new TestRunListenerImpl());
  }

  public Channel executeTests(String url) throws Exception
  {
    CLI cli = new CLI(new URL(url));
    cli.upgrade();

    Channel channel = cli.getChannel();
    Pipe pipe = Pipe.createRemoteToLocal();

    Future<Void> future = channel.callAsync(new JenkinsExecutor(pipe));

    readOutput(pipe, future);

    channel.close();
    return cli.getChannel();
  }

  private void readOutput(Pipe pipe, Future<Void> future) throws Exception
  {
    InputStream inputStream = pipe.getIn();
    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
    while (!future.isDone() || inputStream.available() > 0)
    {
      if (inputStream.available() > 0)
      {
        Event event = (Event) objectInputStream.readObject();
        sendEvent(event);
      } else
      {
        Thread.sleep(30);
      }
    }
  }

  private void sendEvent(final Event event) throws Exception
  {
    if (event instanceof TestRunStartedEvent)
    {
      final TestRunStartedEvent testRunStartedEvent = (TestRunStartedEvent) event;
      workOnListeners(new TestRunListenerWorker()
      {
        @Override
        public void work(TestRunListener listener)
        {
          listener.testRunStarted(testRunStartedEvent.getTestSuiteName(), testRunStartedEvent.getTestCount());
        }
      });
    } else if (event instanceof TestRunEndedEvent)
    {
      workOnListeners(new TestRunListenerWorker()
      {
        @Override
        public void work(TestRunListener listener)
        {
          listener.testRunEnded(0);
        }
      });
    }
  }

  private void workOnListeners(TestRunListenerWorker testRunListenerWorker)
  {
    for (TestRunListener listener : listeners)
    {
      testRunListenerWorker.work(listener);
    }
  }
}