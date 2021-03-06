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
    private static final int SSH_PORT = 22;

    @Getter @Setter private String targetUrl;
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

    public void createGlobalEnvironmentVariables(String key, String value) throws IOException {
        Jenkins instance = Jenkins.getInstanceOrNull();
        if (instance == null) throw new IOException("Unable to load Jenkins instance");

        DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance.getGlobalNodeProperties();
        if (globalNodeProperties == null) throw new IOException("Unable to load global node properties");

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

            //Launch WSAP instance
            launchWASPServer(listener, session, username);
            try {
                listener.getLogger().println("Waiting for server to be available");
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            //Sends data by TCP Connection
            JSONObject vulnerabilityAudit = sendingWSAPParams(listener);
            if (vulnerabilityAudit != null){
                //Stores the highest criticality vulnerabilities in Jenkins by scanner
                generateCriticalEnvVariables(listener, vulnerabilityAudit);
            }

        } catch (JSchException e) {
           throw new InterruptedException(e.getMessage());
        }

        return true;
    }

    private void generateCriticalEnvVariables(BuildListener listener, JSONObject vulnerabilityAudit) throws IOException {
        createGlobalEnvironmentVariables(envVar.toUpperCase(), targetUrl);
        listener.getLogger().println(String.format("Created variable %s with the targetUrl",envVar.toUpperCase()));

        //Iterates through all tools names
        Iterator<String> keys = (Iterator<String>) vulnerabilityAudit.keys();
        while (keys.hasNext()) {
            String key = keys.next();

            //Gets an object with all security levels (High, Medium, Low, Info)
            JSONObject value = vulnerabilityAudit.getJSONObject(key);

            //Gets only the first security level (High)
            Iterator<String> levels = (Iterator<String>) value.keys();
            String jenkinsVar = envVar+"_"+key;

            if (levels.hasNext()){
                //Creates venv var containing the vulnerabilities present in High
                String level = levels.next();
                String vulnerabilities = value.getString(level);

                //Adds var to jenkins variable
                createGlobalEnvironmentVariables(jenkinsVar.toUpperCase(), vulnerabilities);
                listener.getLogger().println(String.format("Created variable %s with the amount of critical vulnerabilities found",jenkinsVar.toUpperCase()));
            }else{
                createGlobalEnvironmentVariables(jenkinsVar.toUpperCase(), "No data");
                listener.getLogger().println(String.format("Created variable %s, but no report was found",jenkinsVar.toUpperCase()));
            }

        }
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

    public JSONObject sendingWSAPParams(BuildListener listener) throws IOException, JSchException {
        JSONObject vulnerabilityAudit = null;

        listener.getLogger().println("Trying to connect on ip: " + ipAddress + ":" + 9999);
        Socket socket = new Socket(ipAddress, 9999);
        if (socket.isConnected()) {
            listener.getLogger().println("Connected");

            //Create stream
            DataInputStream in = new DataInputStream(socket.getInputStream());
            InputStreamReader stream = new InputStreamReader(socket.getInputStream(), Charset.forName("UTF-8"));
            BufferedReader br = new BufferedReader(stream);


            //Sending form data
            JSONObject sendData = generateJSON();
            listener.getLogger().println("Sending defined parameters to WSAP instance");
            OutputStreamWriter out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            out.write(sendData.toString());
            out.flush();

            //Receiving feedback
            listener.getLogger().println("Waiting for server response... May take a few hours");

            StringBuffer buffer = new StringBuffer();
            boolean interrupted = false;
            while (!interrupted) {
                int ch = br.read();
                buffer.append((char) ch);

                if (Utils.isJSONValid(buffer.toString())) {
                    JSONObject jsonObject = JSONObject.fromObject(buffer.toString());
                    if (jsonObject.containsKey("info")) {
                        listener.getLogger().println(jsonObject.get("info"));
                    } else {
                        interrupted = true;
                        if (jsonObject.containsKey("error")) {
                            throw new IOException(jsonObject.get("error").toString());
                        } else {
                            vulnerabilityAudit = jsonObject;
                        }
                    }
                    buffer.setLength(0);
                }
            }

            br.close();
            in.close();
            socket.close();

            //Process json report and present it
            Iterator<String> tools = (Iterator<String>) vulnerabilityAudit.keys();
            while (tools.hasNext()) {
                String tool = tools.next();
                listener.getLogger().println("\n" + tool);

                //Gets an object with all security levels (High, Medium, Low, Info)
                JSONObject securityLevels = vulnerabilityAudit.getJSONObject(tool);
                Iterator<String> levelsKeys = (Iterator<String>) securityLevels.keys();
                if (!levelsKeys.hasNext()) listener.getLogger().println(String.format("- No report found"));
                while (levelsKeys.hasNext()) {
                    String level = levelsKeys.next();
                    String value = String.valueOf(securityLevels.get(level));
                    listener.getLogger().println(String.format("- %s [%s]", level, value));
                }
            }
        }
        return vulnerabilityAudit;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Getter @Setter private String WSAP_LOCATION;
        @Getter @Setter private String TARGET_URL;
        @Getter @Setter private String ENV_VAR;
        @Getter @Setter private String SCANNER_IP;
        @Getter @Setter private String SCANNER_PORT;

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
                if (Long.parseLong(port)<0) {
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