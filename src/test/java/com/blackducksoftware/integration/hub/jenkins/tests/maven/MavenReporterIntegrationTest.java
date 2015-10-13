package com.blackducksoftware.integration.hub.jenkins.tests.maven;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.tasks.BuildWrapper;
import hudson.tasks.Publisher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import jenkins.model.Jenkins;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.jvnet.hudson.test.JenkinsRule;

import com.blackducksoftware.integration.hub.jenkins.HubServerInfo;
import com.blackducksoftware.integration.hub.jenkins.PostBuildScanDescriptor;
import com.blackducksoftware.integration.hub.jenkins.maven.BuildInfoAction;
import com.blackducksoftware.integration.hub.jenkins.maven.MavenBuildWrapperDescriptor;
import com.blackducksoftware.integration.hub.jenkins.maven.HubMavenReporter;
import com.blackducksoftware.integration.hub.jenkins.tests.utils.MavenSupport;
import com.blackducksoftware.integration.hub.response.DistributionEnum;
import com.blackducksoftware.integration.hub.response.PhaseEnum;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider.UserFacingAction;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

public class MavenReporterIntegrationTest {

    private static String basePath;

    private static String testWorkspace;

    private static Properties testProperties;

    @Rule
    public static JenkinsRule j = new JenkinsRule();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    public void addHubServerInfo(HubServerInfo hubServerInfo) {
        resetPublisherDescriptors();

        PostBuildScanDescriptor descriptor = new PostBuildScanDescriptor();
        descriptor.setHubServerInfo(hubServerInfo);
        j.getInstance().getDescriptorList(Publisher.class).add(descriptor);
    }

    public void resetPublisherDescriptors() {
        while (j.getInstance().getDescriptorList(Publisher.class).size() != 0) {
            j.getInstance().getDescriptorList(Publisher.class).remove(0);
        }
    }

    public void addHubMavenReporterDescriptor() {
        MavenBuildWrapperDescriptor descriptor = new MavenBuildWrapperDescriptor();
        j.getInstance().getDescriptorList(BuildWrapper.class).add(descriptor);
    }

    public UsernamePasswordCredentialsImpl addCredentialToGlobalStore(String username, String password) {
        UsernamePasswordCredentialsImpl credential = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, null, null,
                username, password);
        UserFacingAction store = new UserFacingAction();
        try {
            store.getStore().addCredentials(Domain.global(), credential);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return credential;
    }

    @BeforeClass
    public static void init() throws Exception {
        basePath = MavenReporterIntegrationTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        basePath = basePath.substring(0, basePath.indexOf(File.separator + "target"));
        basePath = basePath + File.separator + "test-workspace";
        testWorkspace = basePath + File.separator + "mavenWorkspace";

        testProperties = new Properties();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream is = classLoader.getResourceAsStream("test.properties");
        try {
            testProperties.load(is);
        } catch (IOException e) {
            System.err.println("reading test.properties failed!");
        }
        // p.load(new FileReader(new File("test.properties")));
        System.out.println(testProperties.getProperty("TEST_HUB_SERVER_URL"));
        System.out.println(testProperties.getProperty("TEST_USERNAME"));
        System.out.println(testProperties.getProperty("TEST_PASSWORD"));
    }

    @Test
    public void testRunNotConfiguredNoGlobalConfig() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        HubMavenReporter mavenReporter = new HubMavenReporter(null, false, null, null, null, null);

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("ValidProject" + File.separator + "pom.xml");
        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, buildOutput.contains("Could not find the Hub global configuration!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub project name configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub project version configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Maven scopes configured!"));
    }

    @Test
    public void testRunNotConfiguredEmptyGlobalConfig() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        addHubServerInfo(new HubServerInfo("", ""));

        HubMavenReporter mavenReporter = new HubMavenReporter(null, false, null, null, null, null);

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("ValidProject" + File.separator + "pom.xml");
        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, !buildOutput.contains("Could not find the Hub global configuration!"));
        assertTrue(buildOutput, buildOutput.contains("The Hub server URL is not configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub credentials configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub project name configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub project version configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Maven scopes configured!"));
    }

    @Test
    public void testRunNotConfiguredEmptyCredentialsGlobalConfig() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        UsernamePasswordCredentialsImpl credential = addCredentialToGlobalStore("", "");
        addHubServerInfo(new HubServerInfo("http://server.com", credential.getId()));

        HubMavenReporter mavenReporter = new HubMavenReporter(null, false, null, null, null, null);

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("ValidProject" + File.separator + "pom.xml");
        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, !buildOutput.contains("Could not find the Hub global configuration!"));
        assertTrue(buildOutput, !buildOutput.contains("The Hub server URL is not configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub username configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub password configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub project name configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Hub project version configured!"));
        assertTrue(buildOutput, buildOutput.contains("No Maven scopes configured!"));
    }

    @Test
    public void testRunConfiguredNoPom() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        UsernamePasswordCredentialsImpl credential = addCredentialToGlobalStore(testProperties.getProperty("TEST_USERNAME"),
                testProperties.getProperty("TEST_PASSWORD"));
        addHubServerInfo(new HubServerInfo(testProperties.getProperty("TEST_HUB_SERVER_URL"), credential.getId()));

        HubMavenReporter mavenReporter = new HubMavenReporter("Compile", false, testProperties.getProperty("TEST_PROJECT"),
                PhaseEnum.DEVELOPMENT.name(),
                DistributionEnum.INTERNAL.name(), testProperties.getProperty("TEST_VERSION"));

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());

        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, buildOutput.contains("Perhaps you need to specify the correct POM file path in the project configuration"));
        assertTrue(buildOutput, buildOutput.contains("Finished: FAILURE"));
    }

    @Test
    public void testRunConfiguredEmptyPom() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        UsernamePasswordCredentialsImpl credential = addCredentialToGlobalStore(testProperties.getProperty("TEST_USERNAME"),
                testProperties.getProperty("TEST_PASSWORD"));
        addHubServerInfo(new HubServerInfo(testProperties.getProperty("TEST_HUB_SERVER_URL"), credential.getId()));

        HubMavenReporter mavenReporter = new HubMavenReporter("Compile", false, testProperties.getProperty("TEST_PROJECT"),
                PhaseEnum.DEVELOPMENT.name(),
                DistributionEnum.INTERNAL.name(), testProperties.getProperty("TEST_VERSION"));

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("EmptyPom" + File.separator + "pom.xml");

        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, buildOutput.contains("Failed to parse POMs"));
        assertTrue(buildOutput, buildOutput.contains("Finished: FAILURE"));
    }

    @Test
    public void testRunConfiguredEmptyProject() throws Exception {

        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        UsernamePasswordCredentialsImpl credential = addCredentialToGlobalStore(testProperties.getProperty("TEST_USERNAME"),
                testProperties.getProperty("TEST_PASSWORD"));
        addHubServerInfo(new HubServerInfo(testProperties.getProperty("TEST_HUB_SERVER_URL"), credential.getId()));

        HubMavenReporter mavenReporter = new HubMavenReporter("Compile", false, testProperties.getProperty("TEST_PROJECT"),
                PhaseEnum.DEVELOPMENT.name(),
                DistributionEnum.INTERNAL.name(), testProperties.getProperty("TEST_VERSION"));

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("EmptyProject" + File.separator + "pom.xml");

        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, buildOutput.contains("Failed to parse POMs"));
        assertTrue(buildOutput, buildOutput.contains("Finished: FAILURE"));
    }

    @Test
    public void testRunConfiguredNoDependencies() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        UsernamePasswordCredentialsImpl credential = addCredentialToGlobalStore(testProperties.getProperty("TEST_USERNAME"),
                testProperties.getProperty("TEST_PASSWORD"));
        addHubServerInfo(new HubServerInfo(testProperties.getProperty("TEST_HUB_SERVER_URL"), credential.getId()));

        HubMavenReporter mavenReporter = new HubMavenReporter("Compile", false, testProperties.getProperty("TEST_PROJECT"),
                PhaseEnum.DEVELOPMENT.name(),
                DistributionEnum.INTERNAL.name(), testProperties.getProperty("TEST_VERSION"));

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("NoDependencies" + File.separator + "pom.xml");

        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, buildOutput.contains("postBuild()"));
        assertTrue(buildOutput, buildOutput.contains("buildId: "));
        assertTrue(buildOutput, buildOutput.contains("Hub Jenkins Plugin version"));
        assertTrue(buildOutput, buildOutput.contains("BuildInfo :"));
        assertTrue(buildOutput, buildOutput.contains("Finished: SUCCESS"));

        BuildInfoAction biAction = project.getLastBuild().getRootBuild().getAction(BuildInfoAction.class);
        assertNotNull(biAction);
        assertNotNull(biAction.getBuildInfo());
    }

    @Test
    public void testRunConfiguredValidProject() throws Exception {
        Jenkins jenkins = j.jenkins;
        addHubMavenReporterDescriptor();

        UsernamePasswordCredentialsImpl credential = addCredentialToGlobalStore(testProperties.getProperty("TEST_USERNAME"),
                testProperties.getProperty("TEST_PASSWORD"));
        addHubServerInfo(new HubServerInfo(testProperties.getProperty("TEST_HUB_SERVER_URL"), credential.getId()));

        HubMavenReporter mavenReporter = new HubMavenReporter("Compile", false, testProperties.getProperty("TEST_PROJECT"),
                PhaseEnum.DEVELOPMENT.name(),
                DistributionEnum.INTERNAL.name(), testProperties.getProperty("TEST_VERSION"));

        MavenModuleSet project = jenkins.createProject(MavenModuleSet.class, "Test_job");
        project.setCustomWorkspace(testWorkspace);

        MavenSupport.addMavenToModuleSet(project, MavenSupport.getMavenInstallation());
        project.setRootPOM("ValidProject" + File.separator + "pom.xml");

        project.getReporters().add(mavenReporter);

        MavenModuleSetBuild build = project.scheduleBuild2(0).get();
        String buildOutput = IOUtils.toString(build.getLogInputStream(), "UTF-8");
        assertTrue(buildOutput, buildOutput.contains("postBuild()"));
        assertTrue(buildOutput, buildOutput.contains("buildId: "));
        assertTrue(buildOutput, buildOutput.contains("Hub Jenkins Plugin version"));
        assertTrue(buildOutput, buildOutput.contains("BuildInfo :"));
        assertTrue(buildOutput, buildOutput.contains("Finished: SUCCESS"));

        BuildInfoAction biAction = project.getLastBuild().getRootBuild().getAction(BuildInfoAction.class);
        assertNotNull(biAction);
        assertNotNull(biAction.getBuildInfo());
    }
}
