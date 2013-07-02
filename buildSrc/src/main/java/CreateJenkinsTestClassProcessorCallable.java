import hudson.remoting.Channel;
import hudson.remoting.ClassLoaderHolder;
import hudson.remoting.DelegatingCallable;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.worker.WorkerTestClassProcessor;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.CompositeIdGenerator;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.listener.ContextClassLoaderProxy;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.actor.internal.DefaultActorFactory;

public class CreateJenkinsTestClassProcessorCallable implements DelegatingCallable<TestClassProcessor, RuntimeException> {

    private final WorkerTestClassProcessorFactory targetProcessor;
    private final ClassLoaderHolder classLoaderHolder;
    private final String workerId;

    CreateJenkinsTestClassProcessorCallable(WorkerTestClassProcessorFactory targetProcessor, ClassLoader applicationClassLoader, String workerId) {
        this.targetProcessor = targetProcessor;
        this.workerId = workerId;
        this.classLoaderHolder = new ClassLoaderHolder(applicationClassLoader);
    }

    @Override
    public TestClassProcessor call() throws RuntimeException {
        Channel channel = Channel.current();

        System.err.println(channel.getName());
        System.err.println(getWorkerId());
        System.setProperty(JenkinsTestWorker.WORKER_ID_SYS_PROPERTY, channel.getRemoteProperty(JenkinsTestWorker.WORKER_ID_SYS_PROPERTY).toString());

        RemoteTestFrameworkServiceRegistry testServices = new RemoteTestFrameworkServiceRegistry(CreateJenkinsTestClassProcessorCallable.this);
        IdGenerator<Object> idGenerator = testServices.get(IdGenerator.class);

        TestClassProcessor targetProcessor = this.targetProcessor.create(testServices);
        targetProcessor = new WorkerTestClassProcessor(targetProcessor, idGenerator.generateId(),
                "Test", new TrueTimeProvider());
        ContextClassLoaderProxy<TestClassProcessor> proxy = new ContextClassLoaderProxy<TestClassProcessor>(
                TestClassProcessor.class, targetProcessor, classLoaderHolder.get());
        TestClassProcessor remoteProcessor = proxy.getSource();

        return channel.export(TestClassProcessor.class, remoteProcessor);
//            serverConnection.addIncoming(RemoteTestClassProcessor.class, this);
    }

    public Object getWorkerId() {
        return workerId;  //To change body of created methods use File | Settings | File Templates.
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoaderHolder.get();
    }

    private static class RemoteTestFrameworkServiceRegistry extends DefaultServiceRegistry {
        private final CreateJenkinsTestClassProcessorCallable createJenkinsTestClassProcessorCallable;

        public RemoteTestFrameworkServiceRegistry(CreateJenkinsTestClassProcessorCallable createJenkinsTestClassProcessorCallable) {
            this.createJenkinsTestClassProcessorCallable = createJenkinsTestClassProcessorCallable;
        }

        protected IdGenerator<Object> createIdGenerator() {
            return new CompositeIdGenerator(createJenkinsTestClassProcessorCallable.getWorkerId(), new LongIdGenerator());
        }

        protected ExecutorFactory createExecutorFactory() {
            return new DefaultExecutorFactory();
        }

        protected ActorFactory createActorFactory() {
            return new DefaultActorFactory(get(ExecutorFactory.class));
        }
    }
}
