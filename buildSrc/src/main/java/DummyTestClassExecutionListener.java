import org.gradle.api.internal.tasks.testing.junit.TestClassExecutionListener;

public class DummyTestClassExecutionListener implements TestClassExecutionListener {
    @Override
    public void testClassStarted(String s) {
    }

    @Override
    public void testClassFinished(Throwable throwable) {
    }
}
