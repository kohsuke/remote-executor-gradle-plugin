import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

public class MyRunListener extends RunListener{

    @Override
    public void testStarted(Description description) throws Exception {
        System.out.println("started");
    }


}
