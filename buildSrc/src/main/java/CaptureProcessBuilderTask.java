import org.gradle.api.DefaultTask;
import org.gradle.internal.Factory;
import org.gradle.process.internal.WorkerProcessBuilder;

import javax.inject.Inject;

public class CaptureProcessBuilderTask extends DefaultTask {
    private Factory<WorkerProcessBuilder> processBuilderFactory;

    @Inject
    public CaptureProcessBuilderTask(Factory<WorkerProcessBuilder> processBuilderFactory) {
        this.processBuilderFactory = processBuilderFactory;
    }

    public Factory<WorkerProcessBuilder> getProcessBuilderFactory() {
        return processBuilderFactory;
    }
}
