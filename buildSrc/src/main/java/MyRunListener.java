import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.Exception;
import java.lang.Override;

public class MyRunListener extends RunListener{

    @Override
    public void testStarted(Description description) throws Exception {
        System.out.println("started");
    }

    @Override
    public void testFinished(Description description) throws Exception {
        System.out.println("finished");
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        System.out.println("failure");
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        System.out.println("ignored");
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        System.out.println("assumptionFailure");
    }
}
