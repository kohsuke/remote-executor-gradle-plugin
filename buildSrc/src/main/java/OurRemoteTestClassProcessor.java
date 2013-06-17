public interface OurRemoteTestClassProcessor {
    void startProcessing();

    void processTestClass(org.gradle.api.internal.tasks.testing.TestClassRunInfo testClassRunInfo);

    void stop();
}
