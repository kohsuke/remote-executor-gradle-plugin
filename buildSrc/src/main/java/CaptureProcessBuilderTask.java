import org.gradle.api.DefaultTask;
import org.gradle.internal.Factory;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.process.internal.WorkerProcessBuilder;

import javax.inject.Inject;

public class CaptureProcessBuilderTask extends DefaultTask {
    private final Factory<WorkerProcessBuilder> processBuilderFactory;
    private final ActorFactory actorFactory;

    @Inject
    public CaptureProcessBuilderTask(Factory<WorkerProcessBuilder> processBuilderFactory, ActorFactory actorFactory) {
        this.processBuilderFactory = processBuilderFactory;
        this.actorFactory = actorFactory;
    }

    public Factory<WorkerProcessBuilder> getProcessBuilderFactory() {
        return processBuilderFactory;
    }

    public ActorFactory getActorFactory() {
        return actorFactory;
    }
}
