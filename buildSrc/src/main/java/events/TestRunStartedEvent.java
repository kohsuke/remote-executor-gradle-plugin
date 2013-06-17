package events;

public class TestRunStartedEvent implements Event
{
  private final String testSuiteName;

  private final int testCount;

  public TestRunStartedEvent(String testSuiteName, int testCount)
  {
    this.testSuiteName = testSuiteName;
    this.testCount = testCount;
  }

  public String getTestSuiteName()
  {
    return testSuiteName;
  }

  public int getTestCount()
  {
    return testCount;
  }
}