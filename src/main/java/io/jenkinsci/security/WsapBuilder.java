package io.jenkinsci.security;


import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.jcraft.jsch.*;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import io.jenkinsci.security.analysis.DASTAnalysis;
import io.jenkinsci.security.analysis.SASTAnalysis;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.jenkinsci.plugins.jsch.JSchConnector;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class WsapBuilder extends Builder implements SimpleBuildStep,ConsoleSupport {
    private final int SSH_PORT = 22;
    @Getter
    @Setter
    private  String targetUrl;
    @Getter @Setter private String envVar;
    @Getter @Setter private String credentialId;

    //Scan Properties
    @Getter @Setter private String wsapLocation;
    @Getter @Setter private String ipAddress;
    @Getter @Setter private int port;

    //Analysis Properties
    @Getter @Setter public SASTAnalysis sastAnalysis;
    @Getter @Setter public DASTAnalysis dastAnalysis;

    public static final SchemeRequirement SSH_SCHEME = new SchemeRequirement("ssh");

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public WsapBuilder(String wsapLocation, String envVar, String credentialId, String targetUrl, String ipAddress, int port, String apiKey,  SASTAnalysis sastAnalysis, DASTAnalysis dastAnalysis){
        this.wsapLocation = wsapLocation;
        this.targetUrl = targetUrl;
        this.credentialId = credentialId;
        this.envVar = envVar;
        this.ipAddress = ipAddress;
        this.port = port;
        this.sastAnalysis = sastAnalysis;
        this.dastAnalysis = dastAnalysis;
    }

    @Override
    public String generateCMD(){
        String cmd = String.format("--target.url %s ",targetUrl);
        cmd += String.format("--scanner.ip %s --scanner.port %s ",ipAddress,port);
        cmd += sastAnalysis.generateCMD();
        cmd += dastAnalysis.generateCMD();
        return cmd;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        StandardUsernameCredentials user =  CredentialsProvider
                .findCredentialById(credentialId, StandardUsernameCredentials.class, build, SSH_SCHEME);
        if (user == null) {
            String message = "Credentials with id '" + credentialId + "', no longer exist!";
            listener.getLogger().println(message);
            throw new InterruptedException(message);
        }

        String username = user.getUsername();
        final JSchConnector connector = new JSchConnector(username, ipAddress, SSH_PORT);
        listener.getLogger().println("Successfully created Connector");

        final SSHAuthenticator<JSchConnector, StandardUsernameCredentials> authenticator = SSHAuthenticator
                .newInstance(connector, user);
        authenticator.authenticate(new StreamTaskListener(listener.getLogger(), Charset.defaultCharset()));

        final Session session = connector.getSession();
        final Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "publickey");
        session.setConfig(config);

        try {
            session.connect();
            if (!session.isConnected())
                throw new RuntimeException("Not connected to an open session.  Call open() first!");

            String reportFilePath = performAnalysis(listener, session, username);
            listener.getLogger().println(String.format("Retrieved report file path as %s",reportFilePath));

            JSONObject jsonReport = retrieveReport(listener, session, username, reportFilePath);
            int criticalVul = processReport(jsonReport);
            createGlobalEnvironmentVariables(envVar, targetUrl);
            createGlobalEnvironmentVariables(envVar+"_RESULTS", String.valueOf(criticalVul));

            session.disconnect();
        } catch (JSchException | SftpException | IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        return true;
    }
    private int processReport(JSONObject jsonReport) {
        int criticalVul = 0;
        Iterator<String> keys = (Iterator<String>) jsonReport.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject value = jsonReport.getJSONObject(key);

            Iterator<String> levels = (Iterator<String>) value.keys();
            String level = levels.next();

            JSONArray vulnerabilities = value.getJSONArray(level);
            criticalVul += vulnerabilities.size();
            System.out.println(String.format("Found %s critical vulnerabilities with %s", key, vulnerabilities.size()));
            System.out.println(key);
        }
        System.out.println("Critical Vulnerabilities found: "+criticalVul);
        return criticalVul;
    }

    public void createGlobalEnvironmentVariables(String key, String value){
        Jenkins instance = Jenkins.getInstanceOrNull();
        try {
            DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance.getGlobalNodeProperties();
            List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList = globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class);

            EnvironmentVariablesNodeProperty newEnvVarsNodeProperty = null;
            EnvVars envVars = null;

            if ( envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0 ) {
                newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
                globalNodeProperties.add(newEnvVarsNodeProperty);
                envVars = newEnvVarsNodeProperty.getEnvVars();
            } else {
                envVars = envVarsNodePropertyList.get(0).getEnvVars();
            }
            envVars.put(key, value);

            instance.save();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String performAnalysis(BuildListener listener, Session session, String username) throws JSchException, IOException {
        listener.getLogger().println(String.format("Attempting to ssh as %s:",username));
        String command = String.format("python3 %s/main.py %s",wsapLocation,generateCMD());
        listener.getLogger().println(command);

        String report_location = "";

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        System.out.println("Connected");
        channel.setInputStream(null);
        channel.setErrStream(System.err);
        InputStream in=channel.getInputStream();
        channel.connect();

        //Display console output
        byte[] tmp=new byte[1024];
        while(true){
            while(in.available()>0){
                int i=in.read(tmp, 0, 1024);
                if(i<0)break;
                String textBlock = new String(tmp, 0, i);
                listener.getLogger().println(textBlock);

                String[] lines = textBlock.split("\n");
                boolean bufferHasLines = lines.length > 0;
                if (bufferHasLines){
                    report_location = lines[lines.length-1];
                }
            }
            if(channel.isClosed()){
                System.out.println("Current report_location: "+report_location);
                listener.getLogger().println("exit-status: "+channel.getExitStatus());
                break;
            }
            try{Thread.sleep(1000);}catch(Exception ee){}
        }
        channel.disconnect();

        return report_location;
    }

    private JSONObject retrieveReport(BuildListener listener, Session session, String username, String reportFilePath) throws JSchException, SftpException {
        listener.getLogger().println(String.format("Attempting to ssh as %s:",username));
        listener.getLogger().println("Retrieving vulnerability audit file: "+reportFilePath);

        JSONObject jsonReport = new JSONObject();

        Channel channel = session.openChannel("sftp");
        channel.connect();
        ChannelSftp sftpChannel = (ChannelSftp) channel;

        InputStream stream = sftpChannel.get(reportFilePath);
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            jsonReport = (JSONObject) JSONSerializer.toJSON(sb.toString());

        } catch (IOException io) {
            System.out.println("Exception occurred during reading file from SFTP server due to " + io.getMessage());
            io.getMessage();

        } catch (Exception e) {
            System.out.println("Exception occurred during reading file from SFTP server due to " + e.getMessage());
            e.getMessage();

        }
        sftpChannel.exit();

        return jsonReport;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public String WSAP_LOCATION;
        public String TARGET_URL;
        public String ENV_VAR;
        public String SCANNER_IP;
        public String SCANNER_PORT;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            WSAP_LOCATION = "WSAP_LOCATION_DIRECTORY/wsap";
            TARGET_URL = "http://TARGET_URL";
            ENV_VAR = "DEFINE_ME";
            SCANNER_IP = "127.0.0.1";
            SCANNER_PORT = "8010";
            super.load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Web Security Application Project (WSAP)";
        }

        public FormValidation doCheckPort(@QueryParameter String port){
            try{
                if (Long.valueOf(port)<0) {
                    return FormValidation.error("Please enter a positive number");
                }else{
                    return FormValidation.ok();
                }
            }catch (Exception ex){
                return FormValidation.error("Please enter a valid number");
            }
        }

        public FormValidation doCheckScanMethod(@QueryParameter String value){
            return FormValidation.error(value);
        }

        public FormValidation doCheckTargetUrl(@QueryParameter String targetUrl){
            try {
                new URL(targetUrl).toURI();
                return FormValidation.ok();
            } catch (MalformedURLException | URISyntaxException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckCredentialId(@QueryParameter String credentialId){
            if (credentialId == null || credentialId.isEmpty()){
                return FormValidation.error("Please provide a credentialsID");
            }
            return FormValidation.ok();
        }
    }
}