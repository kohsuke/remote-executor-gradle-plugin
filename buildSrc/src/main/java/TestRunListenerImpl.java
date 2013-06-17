import junit.runner.TestRunListener;

public class TestRunListenerImpl implements TestRunListener
{
  @Override
  public void testRunStarted(String testSuite, int testCount)
  {
    System.out.println(String.format("Started '%s' with %d tests", testSuite, testCount));
  }

  @Override
  public void testRunEnded(long l)
  {
    System.out.println(String.format("Test run ended"));
  }

  @Override
  public void testRunStopped(long l)
  {
  }

  @Override
  public void testStarted(String s)
  {
  }

  @Override
  public void testEnded(String s)
  {
  }

  @Override
  public void testFailed(int i, String s, String s2)
  {
  }
}