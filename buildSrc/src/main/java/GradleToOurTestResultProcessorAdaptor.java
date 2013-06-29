import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestOutputEvent;

public class GradleToOurTestResultProcessorAdaptor implements OurTestResultProcessor {

    private TestResultProcessor resultProcessor;

    GradleToOurTestResultProcessorAdaptor(TestResultProcessor resultProcessor) {
        if (resultProcessor == null) {
            throw new RuntimeException("No resultProcessor!");
        }
        this.resultProcessor = resultProcessor;
    }

    @Override
    public void started(TestDescriptorInternal testDescriptorInternal, TestStartEvent testStartEvent) {
        resultProcessor.started(testDescriptorInternal, testStartEvent);
    }

    @Override
    public void completed(Object o, TestCompleteEvent testCompleteEvent) {
        resultProcessor.completed(o, testCompleteEvent);
    }

    @Override
    public void output(Object o, TestOutputEvent testOutputEvent) {
        resultProcessor.output(o, testOutputEvent);
    }

    @Override
    public void failure(Object o, Throwable throwable) {
        resultProcessor.failure(o, throwable);
    }
}