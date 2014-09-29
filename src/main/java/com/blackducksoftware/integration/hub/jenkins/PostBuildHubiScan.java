package com.blackducksoftware.integration.hub.jenkins;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.ProxyConfiguration;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.JDK;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.tools.ToolDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import jenkins.model.Jenkins;

import org.codehaus.plexus.util.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import com.blackducksoftware.integration.hub.jenkins.exceptions.BDJenkinsHubPluginException;
import com.blackducksoftware.integration.hub.jenkins.exceptions.BDRestException;
import com.blackducksoftware.integration.hub.jenkins.exceptions.HubConfigurationException;
import com.blackducksoftware.integration.hub.jenkins.exceptions.IScanToolMissingException;

public class PostBuildHubiScan extends Recorder {

    public static final int DEFAULT_MEMORY = 256;

    private final IScanJobs[] scans;

    private final String iScanName;

    private final String hubProjectName;

    private final String hubProjectRelease;

    private int iScanMemory;

    private String workingDirectory;

    private JDK java;

    private Result result;

    // private JenkinsHubIntRestService service = null;

    private boolean test = false;

    @DataBoundConstructor
    public PostBuildHubiScan(IScanJobs[] scans, String iScanName, String hubProjectName, String hubProjectRelease, int iScanMemory) {
        this.scans = scans;
        this.iScanName = iScanName;
        this.hubProjectName = hubProjectName;
        this.hubProjectRelease = hubProjectRelease;
        if (iScanMemory == 0) {
            this.iScanMemory = DEFAULT_MEMORY;
        } else {
            this.iScanMemory = iScanMemory;
        }
    }

    public boolean isTEST() {
        return test;
    }

    // Set to true run the integration test without running the actual iScan.
    public void setTEST(boolean tEST) {
        test = tEST;
    }

    public Result getResult() {
        return result;
    }

    private void setResult(Result result) {
        this.result = result;
    }

    public int geDefaultIScanMemory() {
        return DEFAULT_MEMORY;
    }

    public int getIScanMemory() {
        if (iScanMemory == 0) {
            iScanMemory = DEFAULT_MEMORY;
        }
        return iScanMemory;
    }

    public String getHubProjectRelease() {
        return hubProjectRelease;
    }

    public String getHubProjectName() {
        return hubProjectName;
    }

    public IScanJobs[] getScans() {
        return scans;
    }

    public String getiScanName() {
        return iScanName;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    private void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    // http://javadoc.jenkins-ci.org/hudson/tasks/Recorder.html
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public PostBuildScanDescriptor getDescriptor() {
        return (PostBuildScanDescriptor) super.getDescriptor();
    }

    /**
     * Overrides the Recorder perform method. This is the method that gets called by Jenkins to run as a Post Build
     * Action
     * 
     * @param build
     *            AbstractBuild
     * @param launcher
     *            Launcher
     * @param listener
     *            BuildListener
     * 
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        setResult(build.getResult());
        if (result.equals(Result.SUCCESS)) {
            try {
                listener.getLogger().println("Starting BlackDuck Scans...");

                IScanInstallation[] iScanTools = null;
                ToolDescriptor<IScanInstallation> iScanDescriptor = (ToolDescriptor<IScanInstallation>) build.getDescriptorByName(IScanInstallation.class
                        .getSimpleName());
                iScanTools = iScanDescriptor.getInstallations();
                if (validateConfiguration(iScanTools, getScans())) {
                    // This set the base of the scan Target, DO NOT remove this or the user will be able to specify any
                    // file even outside of the Jenkins directories
                    setWorkingDirectory(build.getWorkspace().getRemote()); // This should work on master and
                                                                           // slaves
                    setJava(build, listener);
                    FilePath iScanExec = getIScanCLI(iScanTools, listener, build);
                    List<String> scanTargets = new ArrayList<String>();
                    for (IScanJobs scanJob : getScans()) {
                        if (StringUtils.isEmpty(scanJob.getScanTarget())) {
                            scanTargets.add(getWorkingDirectory());
                        } else {
                            // trim the target so there are no false whitespaces at the beginning or end of the target
                            // path
                            String target = scanJob.getScanTarget().trim();
                            // make sure the target doesn't already begin with a slash or end in a slash
                            // removes the slash if the target begins or ends with one
                            if (target.startsWith("/") || target.startsWith("\\")) {
                                target = getWorkingDirectory() + target;
                            } else {
                                target = getWorkingDirectory() + "/" + target;

                            }
                            if (target.endsWith("/") || target.endsWith("\\")) {
                                target = target.substring(0, target.length() - 1);
                            }
                            scanTargets.add(target);
                        }
                    }
                    runScan(build, launcher, listener, iScanExec, scanTargets);

                    // Only map the scans to a Project Release if the Project name and Project Release have been
                    // configured
                    if (getResult().equals(Result.SUCCESS) && !StringUtils.isEmpty(getHubProjectName()) && !StringUtils.isEmpty(getHubProjectRelease())) {
                        // Wait 5 seconds for the scans to be recognized in the Hub server
                        listener.getLogger().println("Waiting a few seconds for the scans to be recognized by the Hub server.");
                        Thread.sleep(5000);

                        doScanMapping(listener, scanTargets);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(listener.getLogger());
                String message;
                if (e.getCause() != null && e.getCause().getCause() != null) {
                    message = e.getCause().getCause().toString();
                } else if (e.getCause() != null) {
                    message = e.getCause().toString();
                } else {
                    message = e.toString();
                }
                if (message.toLowerCase().contains("service unavailable")) {
                    message = Messages.HubBuildScan_getCanNotReachThisServer_0_(getDescriptor().getHubServerInfo().getServerUrl());
                } else if (message.toLowerCase().contains("precondition failed")) {
                    message = message + ", Check your configuration.";
                }
                listener.error(message);
                setResult(Result.UNSTABLE);
            }
        } else {
            listener.getLogger().println("Build was not successful. Will not run Black Duck Scans.");
        }
        listener.getLogger().println("Finished running Black Duck Scans.");
        build.setResult(getResult());
        return true;
    }

    private void doScanMapping(BuildListener listener, List<String> scanTargets) throws IOException, BDRestException, BDJenkinsHubPluginException {
        JenkinsHubIntRestService service = setJenkinsHubIntRestService(listener);

        ArrayList<String> projectId = null;
        String projectIdToUse = null;
        String releaseId = null;
        ArrayList<LinkedHashMap<String, Object>> projectMatchesResponse = service.getProjectMatches(getHubProjectName());
        projectId = service.getProjectIdsFromProjectMatches(projectMatchesResponse, getHubProjectName());
        if (projectId == null || projectId.isEmpty()) {
            throw new BDJenkinsHubPluginException("The specified Project could not be found.");
        } else if (projectId.size() > 1) {
            throw new BDJenkinsHubPluginException("More than one Project was found with the same name.");
        }
        listener.getLogger().println("[DEBUG] Project Id: '" + projectId.get(0) + "'");
        projectIdToUse = projectId.get(0);

        LinkedHashMap<String, Object> releaseMatchesResponse = service.getReleaseMatchesForProjectId(projectIdToUse);
        releaseId = service.getReleaseIdFromReleaseMatches(releaseMatchesResponse, getHubProjectRelease());
        listener.getLogger().println("[DEBUG] Release Id: '" + releaseId + "'");
        if (StringUtils.isEmpty(releaseId)) {
            throw new BDJenkinsHubPluginException("The specified Release could not be found in the Project.");
        }
        List<String> scanIds = service.getScanLocationIds(listener, scanTargets, releaseId);
        if (!scanIds.isEmpty()) {
            listener.getLogger().println("[DEBUG] These scan Id's were found for the scan targets.");
            for (String scanId : scanIds) {
                listener.getLogger().println(scanId);
            }
            listener.getLogger().println(
                    "[DEBUG] Linking the scan Id's to the Hub Project: '" + getHubProjectName() + "', and Release: '" + getHubProjectRelease()
                            + "'.");

            service.mapScansToProjectRelease(listener, scanIds, releaseId);
        } else {
            listener.getLogger()
                    .println(
                            "[DEBUG] These scans are already mapped to Project : '" + getHubProjectName() + "', Release : '"
                                    + getHubProjectRelease() + "'. OR there was an issue getting the Id's for the defined scan targets.");
        }

    }

    public JenkinsHubIntRestService setJenkinsHubIntRestService(BuildListener listener) throws MalformedURLException {
        JenkinsHubIntRestService service = new JenkinsHubIntRestService();
        service.setListener(listener);
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            ProxyConfiguration proxy = jenkins.proxy;
            if (proxy != null) {
                service.setNoProxyHosts(proxy.getNoProxyHostPatterns());
                service.setProxyHost(proxy.name);
                service.setProxyPort(proxy.port);
                if (!StringUtils.isEmpty(proxy.name) && proxy.port != 0) {
                    if (listener != null) {
                        listener.getLogger().println("[DEBUG] Using proxy: '" + proxy.name + "' at Port: '" + proxy.port + "'");
                    }
                }
            }
        }
        service.setBaseUrl(getDescriptor().getHubServerInfo().getServerUrl());
        service.setCookies(getDescriptor().getHubServerInfo().getUsername(),
                getDescriptor().getHubServerInfo().getPassword());
        return service;
    }

    /**
     * Validates that the target of the scanJob exists, creates a ProcessBuilder to run the shellscript and passes in
     * the necessarry arguments, sets the JAVA_HOME of the Process Builder to the one that the User chose, starts the
     * process and prints out all stderr and stdout to the Console Output.
     * 
     * @param build
     *            AbstractBuild
     * @param launcher
     *            Launcher
     * @param listener
     *            BuildListener
     * @param iScanExec
     *            FilePath
     * @param scanTargets
     *            List<String>
     * 
     * @throws IOException
     * @throws HubConfigurationException
     * @throws InterruptedException
     */
    private void runScan(AbstractBuild build, Launcher launcher, BuildListener listener, FilePath iScanExec, List<String> scanTargets)
            throws IOException,
            HubConfigurationException, InterruptedException {

        validateScanTargets(listener, build.getBuiltOn().getChannel(), scanTargets);
        URL url = new URL(getDescriptor().getHubServerUrl());
        PostBuildScanDescriptor desc = getDescriptor();
        List<String> cmd = new ArrayList<String>();
        cmd.add(getJava().getHome() + "/bin/java");
        cmd.add("-Done-jar.silent=true");
        cmd.add("-jar");

        // TODO add proxy configuration for the CLI as soon as the CLI has proxy support
        // Jenkins jenkins = Jenkins.getInstance();
        // if (jenkins != null) {
        // ProxyConfiguration proxy = jenkins.proxy;
        // if (proxy != null && proxy.getNoProxyHostPatterns() != null) {
        // if (!JenkinsHubIntRestService.getMatchingNoProxyHostPatterns(url.getHost(), proxy.getNoProxyHostPatterns()))
        // {
        // if (!StringUtils.isEmpty(proxy.name) && proxy.port != 0) {
        // // System.setProperty("http.proxyHost", proxy.name);
        // // System.setProperty("http.proxyPort", Integer.toString(proxy.port));
        // // cmd.add("-Dhttp.useProxy=true");
        // cmd.add("-Dblackduck.hub.proxy.host=" + proxy.name);
        // cmd.add("-Dblackduck.hub.proxy.port=" + proxy.port);
        // System.setProperty("blackduck.hub.proxy.host", proxy.name);
        // System.setProperty("blackduck.hub.proxy.port", Integer.toString(proxy.port));
        // }
        // }
        // }
        // }
        if (getIScanMemory() != 256) {
            cmd.add("-Xmx" + getIScanMemory() + "m");
        } else {
            cmd.add("-Xmx" + DEFAULT_MEMORY + "m");
        }
        cmd.add(iScanExec.getRemote());
        cmd.add("--host");
        cmd.add(url.getHost());
        listener.getLogger().println("[DEBUG] : Using this Hub Url : '" + url.getHost() + "'");
        cmd.add("--username");
        cmd.add(getDescriptor().getHubServerInfo().getUsername());
        cmd.add("--password");
        cmd.add(getDescriptor().getHubServerInfo().getPassword());
        if (url.getPort() != -1) {
            cmd.add("--port");
            cmd.add(Integer.toString(url.getPort()));
        }

        if (isTEST()) {
            cmd.add("--dryRun");
        }
        for (String target : scanTargets) {
            cmd.add(target);
        }
        listener.getLogger().println("[DEBUG] : Using this java installation : " + getJava().getName() + " : " +
                getJava().getHome());
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ProcStarter ps = launcher.launch();
        if (ps != null) {
            ps.envs(build.getEnvironment(listener));
            ps.cmds(cmd);
            ps.stdout(byteStream);
            ps.join();

            ByteArrayOutputStream byteStreamOutput = (ByteArrayOutputStream) ps.stdout();
            // DO NOT close this PrintStream or Jenkins will not be able to log any more messages. Jenkins will handle
            // closing it.
            String outputString = new String(byteStreamOutput.toByteArray(), "UTF-8");
            listener.getLogger().println(outputString);
            if (!outputString.contains("Finished in") || !outputString.contains("with status SUCCESS")) {
                setResult(Result.UNSTABLE);
            } else if (outputString.contains("ERROR")) {
                setResult(Result.UNSTABLE);
            } else {
                try {
                    for (String target : scanTargets) {
                        File scanTargetFile = new File(target);
                        String fileName = scanTargetFile.getName();

                        FilePath libFolder = iScanExec.getParent();
                        List<FilePath> files = libFolder.list();
                        FilePath logFolder = null;
                        for (FilePath file : files) {
                            if (file.getName().contains("log")) {
                                logFolder = file;
                            }
                        }
                        File latestLogFile = getLogFileForScan(fileName, logFolder);
                        if (latestLogFile != null) {
                            listener.getLogger().println(
                                    "For scan target : '" + target + "', you can view the BlackDuck Scan CLI logs at : '" + latestLogFile.getCanonicalPath()
                                            + "'");
                            listener.getLogger().println();
                        } else {
                            listener.getLogger().println(
                                    "For scan target : '" + target + "', could not find the log file!");
                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace(listener.getLogger());
                    String message;
                    if (e.getCause() != null && e.getCause().getCause() != null) {
                        message = e.getCause().getCause().toString();
                    } else if (e.getCause() != null) {
                        message = e.getCause().toString();
                    } else {
                        message = e.toString();
                    }
                    listener.error(message);
                    setResult(Result.UNSTABLE);
                }
            }
        } else {
            listener.getLogger().println("[ERROR] : Could not find a ProcStarter to run the process!");
        }
    }

    private File getLogFileForScan(String fileName, FilePath logFolder) throws IOException, InterruptedException {
        File latestLogFile = null;
        DateTime latestLogTime = null;
        List<FilePath> logFiles = logFolder.list();
        for (FilePath log : logFiles) {
            if (log.getName().contains(fileName)) {
                String hostName = InetAddress.getLocalHost().getHostName();
                String logName = log.getName();
                if (logName.contains(hostName)) {
                    // remove the host name
                    logName = logName.replace(hostName + "-", "");
                    int end = logName.length() - 31;
                    if (logName.substring(0, end).equals(fileName)) {
                        // remove the filename
                        logName = logName.replace(fileName + "-", "");

                        // log file name contains the scan target, and the host name. Get the latest one.
                        if (latestLogFile == null) {
                            String time = logName;
                            // remove the -0400.log from the log file name
                            time = time.substring(0, time.length() - 9);

                            DateTimeFormatter dateStringFormat = new
                                    DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HHmmss.SSS").toFormatter();
                            DateTime dateTime = dateStringFormat.parseDateTime(time);
                            latestLogTime = dateTime;
                            latestLogFile = new File(log.getRemote());
                        } else {
                            String time = logName;
                            time = time.substring(0, time.length() - 9);

                            DateTimeFormatter dateStringFormat = new
                                    DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HHmmss.SSS").toFormatter();
                            DateTime logTime = dateStringFormat.parseDateTime(time);

                            if (logTime.isAfter(latestLogTime)) {
                                latestLogTime = logTime;
                                latestLogFile = new File(log.getRemote());
                            }
                        }
                    }
                }
            }
        }
        return latestLogFile;
    }

    public JDK getJava() {
        return java;
    }

    /**
     * Sets the Java Home that is to be used for running the Shell script
     * 
     * @param build
     *            AbstractBuild
     * @param listener
     *            BuildListener
     * @throws IOException
     * @throws InterruptedException
     * @throws HubConfigurationException
     */
    private void setJava(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException, HubConfigurationException {
        EnvVars envVars = build.getEnvironment(listener);
        JDK javaHomeTemp = null;
        if (StringUtils.isEmpty(build.getBuiltOn().getNodeName())) {
            // Empty node name indicates master
            javaHomeTemp = build.getProject().getJDK();
        } else {
            javaHomeTemp = build.getProject().getJDK().forNode(build.getBuiltOn(), listener);
        }
        if (javaHomeTemp == null || StringUtils.isEmpty(javaHomeTemp.getHome())) {
            listener.getLogger().println("Could not find the specified Java installation, checking the JAVA_HOME variable.");
            if (envVars.get("JAVA_HOME") == null) {
                throw new HubConfigurationException("Need to define a JAVA_HOME or select an installed JDK.");
            }
            // In case the user did not select a java installation, set to the environment variable JAVA_HOME
            javaHomeTemp = new JDK("Default Java", envVars.get("JAVA_HOME"));
        }
        // FIXME look for the java executable and make sure it exists
        File javaExecFile = new File(javaHomeTemp.getHome());
        FilePath javaExec = new FilePath(build.getBuiltOn().getChannel(), javaExecFile.getCanonicalPath());
        if (!javaExec.exists()) {
            throw new HubConfigurationException("Could not find the specified Java installation at: " + javaExec.getRemote());
        }
        java = javaHomeTemp;
    }

    /**
     * Looks through the iScanInstallations to find the one that the User chose, then looks for the scan.cli.sh in the
     * bin folder of the directory defined by the Installation.
     * It then checks that the File exists.
     * 
     * @param iScanTools
     *            IScanInstallation[] User defined iScan installations
     * @param listener
     *            BuildListener
     * @param build
     *            AbstractBuild
     * 
     * @return File the scan.cli.sh
     * @throws IScanToolMissingException
     * @throws IOException
     * @throws InterruptedException
     * @throws HubConfigurationException
     */
    public FilePath getIScanCLI(IScanInstallation[] iScanTools, BuildListener listener, AbstractBuild build) throws IScanToolMissingException, IOException,
            InterruptedException, HubConfigurationException {
        FilePath iScanExec = null;
        for (IScanInstallation iScan : iScanTools) {
            Node node = build.getBuiltOn();
            if (StringUtils.isEmpty(node.getNodeName())) {
                // Empty node name indicates master
                listener.getLogger().println("[DEBUG] : Running on : master");
            } else {
                listener.getLogger().println("[DEBUG] : Running on : " + node.getNodeName());
                iScan = iScan.forNode(node, listener); // Need to get the Slave iScan
            }
            if (iScan.getName().equals(getiScanName())) {
                if (iScan.getExists(node.getChannel(), listener)) {
                    iScanExec = iScan.getCLI(node.getChannel());
                    listener.getLogger().println(
                            "[DEBUG] : Using this BlackDuck Scan CLI at : " + iScanExec.getRemote());
                } else {
                    listener.getLogger().println("[ERROR] : Could not find the CLI file in : " + iScan.getHome());
                    throw new IScanToolMissingException("Could not find the CLI file to execute at : '" + iScan.getHome() + "'");
                }
            }
        }
        if (iScanExec == null) {
            // Should not get here unless there are no iScan Installations defined
            // But we check this just in case
            throw new HubConfigurationException("You need to select which BlackDuck Scan installation to use.");
        }
        return iScanExec;
    }

    /**
     * Validates that the Plugin is configured correctly. Checks that the User has defined an iScan tool, a Hub server
     * URL, a Credential, and that there are at least one scan Target/Job defined in the Build
     * 
     * @param iScanTools
     *            IScanInstallation[] User defined iScan installations
     * @param scans
     *            IScanJobs[] the iScan jobs defined in the Job config
     * 
     * @return True if everything is configured correctly
     * 
     * @throws IScanToolMissingException
     * @throws HubConfigurationException
     */
    public boolean validateConfiguration(IScanInstallation[] iScanTools, IScanJobs[] scans) throws IScanToolMissingException, HubConfigurationException {
        if (iScanTools == null || iScanTools.length == 0 || iScanTools[0] == null) {
            throw new IScanToolMissingException("Could not find an iScan Installation to use.");
        }
        if (scans == null || scans.length == 0) {
            throw new HubConfigurationException("Could not find any targets to scan.");
        }
        if (!getDescriptor().getHubServerInfo().isPluginConfigured()) {
            // If plugin is not Configured, we try to find out what is missing.
            if (StringUtils.isEmpty(getDescriptor().getHubServerInfo().getServerUrl())) {
                throw new HubConfigurationException("No Hub URL was provided.");
            }
            if (StringUtils.isEmpty(getDescriptor().getHubServerInfo().getCredentialsId())) {
                throw new HubConfigurationException("No credentials could be found to connect to the Hub.");
            }
        }
        // No exceptions were thrown so return true
        return true;
    }

    /**
     * Validates that all scan targets exist
     * 
     * @param listener
     *            BuildListener
     * @param channel
     *            VirtualChannel
     * @param scanTargets
     *            List<String>
     * 
     * @return
     * @throws IOException
     * @throws HubConfigurationException
     * @throws InterruptedException
     */
    public boolean validateScanTargets(BuildListener listener, VirtualChannel channel, List<String> scanTargets) throws IOException, HubConfigurationException,
            InterruptedException {
        for (String currTarget : scanTargets) {
            File locationFile = new File(currTarget);
            FilePath target = null;
            if (channel != null) {
                target = new FilePath(channel, locationFile.getCanonicalPath());
            } else {
                target = new FilePath(locationFile);
            }
            if (target.length() <= getWorkingDirectory().length()
                    && !getWorkingDirectory().equals(target.getRemote()) && !target.getRemote().contains(getWorkingDirectory())) {
                throw new HubConfigurationException("Can not scan targets outside of the workspace.");
            }

            if (!target.exists()) {
                throw new IOException("Scan target could not be found : " + target.getRemote());
            } else {
                listener.getLogger().println(
                        "[DEBUG] : Scan target exists at : " + target.getRemote());
            }
        }
        return true;
    }

}
