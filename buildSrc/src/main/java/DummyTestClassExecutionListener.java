import org.gradle.api.internal.tasks.testing.junit.TestClassExecutionListener;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DummyTestClassExecutionListener implements TestClassExecutionListener {
    @Override
    public void testClassStarted(String s) {
        System.out.println("Started " + s);
    }

    @Override
    public void testClassFinished(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (throwable != null) {
            throwable.printStackTrace(pw);
        }
        System.out.println("Finished " + sw.toString());
    }
}
