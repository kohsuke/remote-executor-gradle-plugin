/**
 * @author Kohsuke Kawaguchi
 */
public interface OurTestResportProcessor {
    void started(org.gradle.api.internal.tasks.testing.TestDescriptorInternal testDescriptorInternal, org.gradle.api.internal.tasks.testing.TestStartEvent testStartEvent);

    void completed(java.lang.Object o, org.gradle.api.internal.tasks.testing.TestCompleteEvent testCompleteEvent);

    void output(java.lang.Object o, org.gradle.api.tasks.testing.TestOutputEvent testOutputEvent);

    void failure(java.lang.Object o, java.lang.Throwable throwable);
}