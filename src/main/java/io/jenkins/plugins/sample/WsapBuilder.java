package io.jenkins.plugins.sample;

import com.google.common.collect.ImmutableList;
import com.jcraft.jsch.*;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import io.jenkins.plugins.sample.analysis.*;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class WsapBuilder extends Builder implements SimpleBuildStep,ConsoleSupport {
    private final int SSH_PORT = 22;
    @Getter @Setter private  String targetUrl;

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
    public WsapBuilder(String wsapLocation, String targetUrl, String userSSH, String ipAddress, int port, String apiKey,  SASTAnalysis sastAnalysis, DASTAnalysis dastAnalysis){
        this.wsapLocation = wsapLocation;
        this.targetUrl = targetUrl;
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
        JSch jsch = new JSch();
        Session session = null;
        String privateKeyPath = "/home/jenkins/.ssh/id_rsa";
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
                    listener.getLogger().println(new String(tmp, 0, i));
                }
                if(channel.isClosed()){
                    listener.getLogger().println("exit-status: "+channel.getExitStatus());
                    break;
                }
                try{Thread.sleep(1000);}catch(Exception ee){}
            }

            channel.disconnect();
            session.disconnect();
        } catch (JSchException e) {
            throw new RuntimeException(e.getMessage());
            //throw new RuntimeException("Error durring SSH command execution. Command: " + command);
        }
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public String WSAP_LOCATION;
        public String TARGET_URL;
        public String SSH_USER;
        public String SCANNER_IP;
        public String SCANNER_PORT;

        //SAST
        public String TARGET;

        //DAST
        public String LOGIN_URL;
        public String JSON_LOGIN_REQUEST;
        public String JSON_LOGIN_USERNAME_FIELD;
        public String JSON_LOGIN_PASSWORD_FIELD;
        public String LOGGED_IN_REGGEX;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            WSAP_LOCATION = "/home/marquez/Desktop/wsap";
            TARGET_URL = "http://127.0.0.1";
            SSH_USER = "marquez";
            SCANNER_IP = "127.0.0.1";
            SCANNER_PORT = "8010";

            TARGET = "/home/jenkins/vulnado";

            LOGIN_URL = "http://target_url.com/authentication/login";
            JSON_LOGIN_REQUEST = generateJSONRequest();
            JSON_LOGIN_USERNAME_FIELD = "username";
            JSON_LOGIN_PASSWORD_FIELD = "password";
            LOGGED_IN_REGGEX = "r'\\Q<a href='logout.jsp'>Logout</a>\\E";
            super.load();
        }

        private static String generateJSONRequest(){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("name", "name");
            jsonObject.put("username", "username");
            jsonObject.put("password","password");

            return jsonObject.toString(4);
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

        /**
         * Return a describer containing all Child Descriptors
         * Used in order to add multiple user credentials
         * @return
         */
        public List<Descriptor> getUsersDescriptors() {
            Jenkins jenkins=Jenkins.getInstanceOrNull();
            return ImmutableList.of(jenkins.getDescriptor(UserEntry.class));
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