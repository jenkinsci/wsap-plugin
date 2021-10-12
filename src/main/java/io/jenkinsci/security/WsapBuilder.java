package io.jenkinsci.security;


import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.jcraft.jsch.*;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
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
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.jenkinsci.security.Utils.isJSONValid;


public class WsapBuilder extends Builder implements SimpleBuildStep,ConsoleSupport {
    private final int SSH_PORT = 22;
    @Getter
    @Setter
    private  String targetUrl;
    @Getter @Setter private String envVar;
    @Getter @Setter private String credentialsId;

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
    public WsapBuilder(String wsapLocation, String envVar, String credentialsId, String targetUrl, String ipAddress, int port, String apiKey, SASTAnalysis sastAnalysis, DASTAnalysis dastAnalysis){
        this.wsapLocation = wsapLocation;
        this.targetUrl = targetUrl;
        this.credentialsId = credentialsId;
        this.envVar = envVar;
        this.ipAddress = ipAddress;
        this.port = port;
        this.sastAnalysis = sastAnalysis;
        this.dastAnalysis = dastAnalysis;
    }

    @Override
    public JSONObject generateJSON(){
        JSONObject json = new JSONObject();
        json.put("target.url", targetUrl);
        json.put("scanner.ip", ipAddress);
        json.put("scanner.port", port);
        json.put("sastAnalysis", sastAnalysis.generateJSON());
        json.put("dastAnalysis", dastAnalysis.generateJSON());
        return json;
    }

    /*@Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        StandardUsernameCredentials user =  CredentialsProvider
                .findCredentialById(credentialsId, SSHUserPrivateKey.class, build, SSH_SCHEME);
        if (user == null) {
            String message = "Credentials with id '" + credentialsId + "', no longer exist!";
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
    }*/
    private int processReport(JSONObject jsonReport) {
        int highVul = 0;
        Iterator<String> keys = (Iterator<String>) jsonReport.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject value = jsonReport.getJSONObject(key);

            Iterator<String> levels = (Iterator<String>) value.keys();
            String level = levels.next();

            JSONArray vulnerabilities = value.getJSONArray(level);
            highVul += vulnerabilities.size();
            System.out.println(String.format("Found %s high vulnerabilities with %s", key, vulnerabilities.size()));
            System.out.println(key);
        }
        System.out.println("High Vulnerabilities found: "+highVul);
        return highVul;
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        //Retrieve valid username
        StandardUsernameCredentials user =  CredentialsProvider
                .findCredentialById(credentialsId, SSHUserPrivateKey.class, build, SSH_SCHEME);
        if (user == null) {
            String message = "Credentials with id '" + credentialsId + "', no longer exist!";
            listener.getLogger().println(message);
            throw new InterruptedException(message);
        }
        String username = user.getUsername();

        //Create SSH Session
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

        //Connect
        try {
            session.connect();
            if (!session.isConnected())
                throw new JSchException("Not connected to an open session");

            //Actions
            launchWASPServer(listener, session, username);
            try {
                listener.getLogger().println("Waiting for server to be available");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendingWSAPParams(listener);

        } catch (JSchException e) {
           throw new InterruptedException(e.getMessage());
        }

        return true;
    }

    public void launchWASPServer(BuildListener listener, Session session, String username) throws JSchException {
        listener.getLogger().println(String.format("Attempting to ssh as: %s",username));
        String command = String.format("python3 %s/main.py --server %s",wsapLocation,9999);
        listener.getLogger().println(command);

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setPty(true);
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(System.err);

        //Creating channel
        channel.connect();
        listener.getLogger().println("WASP instance was successfully initialized");

        //Closing channel
        channel.disconnect();
    }

    public void sendingWSAPParams(BuildListener listener) throws IOException, JSchException {
        listener.getLogger().println("Trying to connect on ip: " + ipAddress + ":" + 9999);
        Socket s = new Socket(ipAddress, 9999);
        listener.getLogger().println("Connected");
        JSONObject sendData = generateJSON();

        try {
            listener.getLogger().println("Waiting for server to be available");
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Sending form data
        listener.getLogger().println("Sending defined parameters to WSAP instance");
        OutputStreamWriter out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
        out.write(sendData.toString());
        out.flush();

        listener.getLogger().println("Waiting for server response... May take a few hours");
        String message = null;
        DataInputStream in = new DataInputStream(s.getInputStream());
        BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));

        boolean interrupted = false;
        while(!interrupted) {
            while (in.available()>0) {
                message = br.readLine();
            }
            if (Utils.isJSONValid(message)){
                interrupted = true;
            }
        }
        s.close();

        JSONObject jsonObject = JSONObject.fromObject(message);
        boolean hasError = jsonObject.get("error") != null;
        if (hasError) {
            throw new IOException(jsonObject.get("error").toString());
        } else {
            listener.getLogger().println("What?!? No error found?!?");
        }

    }


        public String performAnalysis(BuildListener listener, Session session, String username) throws JSchException, IOException {
        listener.getLogger().println(String.format("Attempting to ssh as %s:",username));
        String command = String.format("python3 %s/main.py %s",wsapLocation, generateJSON());
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

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup context, @QueryParameter String credentialsId) {
            StandardListBoxModel result = new StandardListBoxModel();
            return result
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, context,
                            SSHUserPrivateKey.class,
                            Collections.<DomainRequirement>singletonList(SSH_SCHEME))
                    .includeCurrentValue(credentialsId);
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