package com.redhat.jenkins.nodesharing;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.queue.ScheduleResult;
import hudson.util.StreamTaskListener;
import jenkins.model.queue.AsynchronousExecution;
import jenkins.util.Timer;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.gitclient.Git;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.redhat.jenkins.nodesharing.Pool.CONFIG_REPO_PROPERTY_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PoolTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void readConfigFromRepo() throws Exception {
        injectDummyConfigRepo();
        Pool pool = Pool.getInstance();
        Map<String, String> config = pool.getConfig();
        assertEquals("https://dummy.test", config.get("orchestrator.url"));

        Iterator<ExecutorJenkins> jenkinses = pool.getJenkinses().iterator();
        ExecutorJenkins j1 = jenkinses.next();
        ExecutorJenkins j2 = jenkinses.next();
        assertEquals("https://jenkins1.acme.com", j1.getUrl().toExternalForm());
        assertEquals("https://jenkins2.acme.com", j2.getUrl().toExternalForm());
    }

    @Test
    public void testPopulateComputers() throws Exception {
        GitClient git = injectDummyConfigRepo();
        Node win1 = getNode("win1.acme.com");
        assertEquals("windows w2k12", win1.getLabelString());
        assertTrue(win1.toComputer().isOnline());

        assertThat(j.jenkins.getComputers(), arrayWithSize(4));

        // Same changes re-applied with no inventory change
        git.getWorkTree().child("fake_change").touch(0);
        git.add("*");
        git.commit("Update"); // New commit is needed to force computer update
        Pool.Updater.getInstance().doRun();

        assertThat(j.jenkins.getComputers(), arrayWithSize(4));
        assertSame(win1, getNode("win1.acme.com"));
        assertSame(win1.toComputer(), getNode("win1.acme.com").toComputer());
    }

    @Test
    public void updateComputers() throws Exception {
        GitClient git = injectDummyConfigRepo();

        assertEquals("windows w2k16", getNode("win2.acme.com").getLabelString());
        assertEquals("solaris11 sparc", getNode("solaris1.acme.com").getLabelString());
        assertNull(j.jenkins.getNode("windows.acme.com"));

        Node nodeW1 = getNode("win1.acme.com");
        Computer computerW1 = nodeW1.toComputer();

        // Update
        FilePath workTree = git.getWorkTree().child("nodes");
        workTree.child("win2.acme.com.xml").renameTo(workTree.child("windows.acme.com.xml")); // Technically, we should rename the attribute as well
        FilePath solarisXml = workTree.child("solaris1.acme.com.xml");
        String newConfig = solarisXml.readToString().replace("solaris11", "solaris12");
        solarisXml.write(newConfig, Charset.defaultCharset().name());
        git.add("nodes/*");
        git.commit("Update");
        Pool.Updater.getInstance().doRun();

        assertEquals("windows w2k16", getNode("windows.acme.com").getLabelString());
        assertEquals("solaris12 sparc", getNode("solaris1.acme.com").getLabelString());
        assertNull(j.jenkins.getNode("win2.acme.com"));
        assertSame(nodeW1, getNode("win1.acme.com"));
        assertSame(computerW1, getNode("win1.acme.com").toComputer());
    }

    @Test
    public void workloadMapping() throws Exception {
        injectDummyConfigRepo();
        ExecutorJenkins owner = new ExecutorJenkins("https://jenkins42.acme.com");

        MockTask task = new MockTask(owner, Label.get("solaris11"));
        Queue.Item item = j.jenkins.getQueue().schedule2(task, 0).getItem();
        assertEquals("jenkins42.acme.com", item.task.getFullDisplayName());
        item.getFuture().get();
        assertEquals(getNode("solaris1.acme.com").toComputer(), task.actuallyRunOn[0]);


        task = new MockTask(owner, Label.get("windows"));
        j.jenkins.getQueue().schedule2(task, 0).getItem().getFuture().get();
        assertThat(task.actuallyRunOn[0].getName(), startsWith("win"));

        // Never schedule labels we do not serve - including empty one
        task = new MockTask(owner, Label.get(""));
        ScheduleResult scheduleResult = j.jenkins.getQueue().schedule2(task, 0);
        assertTrue(scheduleResult.isAccepted());
        assertFalse(scheduleResult.isRefused());
        Future<Queue.Executable> startCondition = scheduleResult.getItem().getFuture().getStartCondition();
        assertFalse(startCondition.isDone());
        Thread.sleep(1000);
        assertFalse(startCondition.isDone());
    }

    @Test @Ignore
    public void ui() throws Exception {
        injectDummyConfigRepo();
        Timer.get().schedule(new Runnable() {
            private final Random rand = new Random();
            @Override public void run() {
                List<String> owners = Arrays.asList("https://a.com", "https://b.org", "http://10.8.0.14");
                List<String> labels = Arrays.asList("soalris11", "windows", "sparc", "w2k16");
                for (;;) {
                    new ReservationTask(
                            new ExecutorJenkins(
                                    owners.get(rand.nextInt(owners.size()))
                            ),
                            Label.get(
                                    labels.get(rand.nextInt(labels.size()))
                            )
                    ).schedule();
                    System.out.println('.');
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }, 0, TimeUnit.SECONDS);
        j.interactiveBreak();
    }

    private static class MockTask extends ReservationTask {
        final FakeComputer actuallyRunOn[] = new FakeComputer[1];
        public MockTask(@Nonnull ExecutorJenkins owner, @Nonnull Label label) {
            super(owner, label);
        }

        @Override
        public @CheckForNull Queue.Executable createExecutable() throws IOException {
            return new ReservationExecutable(this) {
                @Override
                public void run() throws AsynchronousExecution {
                    actuallyRunOn[0] = (FakeComputer) Executor.currentExecutor().getOwner();
                }
            };
        }
    }

    private @Nonnull SharedNode getNode(String name) {
        Node node = j.jenkins.getNode(name);
        assertNotNull("No such node " + name, node);
        return (SharedNode) node;
    }

    private GitClient injectDummyConfigRepo() throws Exception {
        File orig = new File(getClass().getResource("dummy_config_repo").toURI());
        assertTrue(orig.isDirectory());
        File repo = tmp.newFolder();
        FileUtils.copyDirectory(orig, repo);

        StreamTaskListener listener = new StreamTaskListener(System.err, Charset.defaultCharset());
        GitClient git = Git.with(listener, new EnvVars()).in(repo).using("git").getClient();
        git.init();
        git.add("*");
        git.commit("Init");

        System.setProperty(CONFIG_REPO_PROPERTY_NAME, repo.getAbsolutePath());
        Pool.Updater.getInstance().doRun();

        return git;
    }
}