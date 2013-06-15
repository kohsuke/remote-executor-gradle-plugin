import org.gradle.api.internal.tasks.testing.junit.JUnitSpec;
import org.gradle.api.tasks.testing.junit.JUnitOptions;

public class DummyJUnitSpec extends JUnitSpec {

    public DummyJUnitSpec() {
        this(new JUnitOptions());
    }

    public DummyJUnitSpec(JUnitOptions options) {
        super(options);
    }

    @Override
    public boolean hasCategoryConfiguration() {
        return false;
    }
}
