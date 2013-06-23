import hudson.remoting.Callable;
import hudson.remoting.Channel;
import hudson.remoting.RemoteInputStream;
import hudson.remoting.Which;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestClassExecuter;
import org.gradle.util.FilteringClassLoader;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Gradle's {@link FilteringClassLoader} hides JUnit classes from the classloader loading {@link JenkinsConnector},
 * while letting classes like {@link JUnitTestClassExecuter} visible (which refers to JUnit classes in its signature.)
 *
 * <p>
 * This creates a classloader constraint violation. To avoid the problem, we'll inject JUnit jar into
 * the common ancestor classloader of all RemoteClassLoaders, that is the app classloader loading
 * remoting.jar in the newly launched JVM.
 *
 * TODO: take advantages of the jar file caching mechanism introduced in newer version of remoting.
 */
class JUnitInjector implements Callable<Void,IOException> {
    /**
     * Checksum of the JUnit jar file in the hex dump
     */
    private final String checksum;
    /**
     * If necessary, the content of the jar can be read from here.
     */
    private final InputStream content;

    /**
     * Inserts JUnit jar up in the classloader hierarchy on the other side of the channel.
     */
    static void insert(Channel ch) throws IOException, InterruptedException {
        File jar = Which.jarFile(Test.class);
        InputStream in = new RemoteInputStream(new FileInputStream(jar));
        try {
            ch.call(new JUnitInjector(jar,in));
        } finally {
            in.close();
        }
    }

    private JUnitInjector(File jar, InputStream content) throws IOException {
        this.checksum = checksumOf(jar);
        this.content = content;
    }

    private String checksumOf(File f) throws IOException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            DigestInputStream in =new DigestInputStream(new FileInputStream(f),md5);
            try {
                IOUtils.copy(in, new NullOutputStream());
            } finally {
                in.close();
            }
            return new String(Hex.encodeHex(md5.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);    // MD5 is a mandatory part of JCA.
        }
    }

    @Override
    public Void call() throws IOException {
        try {
            URLClassLoader cl = (URLClassLoader)Channel.class.getClassLoader();
            Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            m.setAccessible(true);
            m.invoke(cl,resolveJar().toURI().toURL());
            return null;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Locates the local copy of junit jar
     */
    private File resolveJar() throws IOException {
        File t = File.createTempFile("junit", "jar");
        try {
            File expected = new File(t.getParentFile(),checksum+".jar");
            if (expected.exists())  return expected;    // already there

            FileOutputStream tout = new FileOutputStream(t);
            try {
                IOUtils.copy(content,tout);
            } finally {
                tout.close();
            }
            t.renameTo(expected); // in a race condition this would fail, but doesn't matter. the file is still one way or the other
            return expected;
        } finally {
            t.delete();
        }
    }

    private static final long serialVersionUID = 1L;
}
