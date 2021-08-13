package io.jenkins.plugins.sample;

import com.jcraft.jsch.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import io.jenkins.plugins.sample.analysis.*;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class WsapBuilder extends Builder implements SimpleBuildStep,ConsoleSupport {
    private final int SSH_PORT = 22;
    @Getter @Setter private  String targetUrl;
    @Getter @Setter private String envVar;
    @Getter @Setter private String privateKeyPath;

    //Scan Properties
    @Getter @Setter private String wsapLocation;
    @Getter @Setter private String userSSH;
    @Getter @Setter private String ipAddress;
    @Getter @Setter private int port;

    //Analysis Properties
    @Getter @Setter public SASTAnalysis sastAnalysis;
    @Getter @Setter public DASTAnalysis dastAnalysis;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public WsapBuilder(String wsapLocation, String envVar, String privateKeyPath, String targetUrl, String userSSH, String ipAddress, int port, String apiKey,  SASTAnalysis sastAnalysis, DASTAnalysis dastAnalysis){
        this.wsapLocation = wsapLocation;
        this.targetUrl = targetUrl;
        this.privateKeyPath = privateKeyPath;
        this.envVar = envVar;
        this.userSSH = userSSH;
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
        listener.getLogger().println("Attempting to create session!");

        String reportFilePath = performAnalysis(listener);
        System.out.println(reportFilePath);

        JSONObject jsonReport = retreiveReport(listener, reportFilePath);

        int criticalVul = processReport(jsonReport);
        createGlobalEnvironmentVariables(envVar, targetUrl);
        createGlobalEnvironmentVariables(envVar+"_RESULTS", String.valueOf(criticalVul));

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


    public String performAnalysis(BuildListener listener){
        JSch jsch = new JSch();
        Session session = null;
        String report_location = "";

        try {
            jsch.addIdentity(privateKeyPath);
            session = jsch.getSession(userSSH, ipAddress, SSH_PORT);
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            listener.getLogger().println("Session created with success!!!!!!");
        } catch (JSchException e) {
            throw new RuntimeException("Failed to create Jsch Session object.", e);
        }
        listener.getLogger().println("Attempting to ssh:");
        listener.getLogger().println(String.format("ssh -i %s %s@%s",privateKeyPath,userSSH,ipAddress));
        String command = String.format("python3 %s/main.py %s",wsapLocation,generateCMD());
        listener.getLogger().println(command);

        try {
            session.connect();
            if (!session.isConnected())
                throw new RuntimeException("Not connected to an open session.  Call open() first!");

            ChannelExec  channel = (ChannelExec) session.openChannel("exec");
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
                    report_location = lines[lines.length-1];
                }
                if(channel.isClosed()){
                    System.out.println("Current report_location: "+report_location);
                    listener.getLogger().println("exit-status: "+channel.getExitStatus());
                    break;
                }
                try{Thread.sleep(1000);}catch(Exception ee){}
            }

            channel.disconnect();
            session.disconnect();
        } catch (JSchException | IOException e) {
            throw new RuntimeException(e.getMessage());
            //throw new RuntimeException("Error durring SSH command execution. Command: " + command);
        }
        return report_location;
    }



    private JSONObject retreiveReport(BuildListener listener, String reportFilePath) {
        JSch jsch = new JSch();
        Session session = null;
        JSONObject jsonReport = new JSONObject();
        try {
            jsch.addIdentity(privateKeyPath);
            session = jsch.getSession(userSSH, ipAddress, SSH_PORT);
            session.setConfig("PreferredAuthentications", "publickey,keyboard-interactive,password");
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            listener.getLogger().println("Session created with success!!!!!!");
        } catch (JSchException e) {
            throw new RuntimeException("Failed to create Jsch Session object.", e);
        }
        listener.getLogger().println("Attempting to ssh:");
        listener.getLogger().println("Retrieving vulnerability audit file: "+reportFilePath);

        try {
            session.connect();
            if (!session.isConnected())
                throw new RuntimeException("Not connected to an open session.  Call open() first!");

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
            session.disconnect();
        } catch (JSchException | SftpException e) {
            throw new RuntimeException(e.getMessage());
            //throw new RuntimeException("Error durring SSH command execution. Command: " + command);
        }
        return jsonReport;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public String WSAP_LOCATION;
        public String TARGET_URL;
        public String ENV_VAR;
        public String PRIVATE_KEY_PATH;
        public String SSH_USER;
        public String SCANNER_IP;
        public String SCANNER_PORT;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            WSAP_LOCATION = "/home/marquez/Desktop/wsap";
            TARGET_URL = "http://127.0.0.1";
            ENV_VAR = "DEFINE_ME";
            PRIVATE_KEY_PATH = "~/.ssh/id_rsa";
            SSH_USER = "marquez";
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

        public FormValidation doCheckTargetUrl(@QueryParameter String targetUrl){
            try {
                new URL(targetUrl).toURI();
                return FormValidation.ok();
            } catch (MalformedURLException | URISyntaxException e) {
                return FormValidation.error(e.getMessage());
            }
        }

        public FormValidation doCheckScanMethod(@QueryParameter String value){
            return FormValidation.error(value);
        }

        public FormValidation doCheckApiUrl(@QueryParameter String apiUrl){
                try {
                    new URL(apiUrl).toURI();
                    return FormValidation.ok();
                } catch (MalformedURLException | URISyntaxException e) {
                    return FormValidation.error(e.getMessage());
                }
        }

        public FormValidation doCheckApiDefinition(@QueryParameter String apiDefinition){
            try {
                new URI(apiDefinition);
                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }
}