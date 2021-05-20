package io.jenkins.plugins.sample;

import com.google.common.collect.ImmutableList;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.ExportedBean;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

public class WsapBuilder extends Builder implements SimpleBuildStep {

    //Scanner Properties
    private String ipAddress;
    private int port;
    private String apiKey;

    //Analysis Properties
    private String targetUrl;
    private String scanMethod;
    private String apiUrl;
    private String apiUrlDefinition;
    private boolean performAttack;

    //Login Properties
    private LoginProperties useLogin;
    private List<UserEntry> entries;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public WsapBuilder(String ipAddress, int port, String apiKey, String targetUrl, String scanMethod, String apiUrl,
                       String apiUrlDefinition, boolean performAttack, LoginProperties useLogin, Collection<UserEntry> entries){
        this.ipAddress = ipAddress;
        this.port = port;
        this.apiKey = "vcvicclkl5kegm34aba9dhroem";
        this.targetUrl = targetUrl;
        this.scanMethod = scanMethod;
        this.apiUrl = apiUrl;
        this.apiUrlDefinition = apiUrlDefinition;
        this.performAttack = performAttack;
        if (useLogin!=null){
            this.useLogin = useLogin;
        }
        this.entries = this.entries = entries != null ? new ArrayList(entries) : Collections.emptyList();
    }

    public List<UserEntry> getEntries(){
        return entries;
    }

    public static final class UserEntry extends Entry {
        private final String text;
        @DataBoundConstructor public UserEntry(String text) { this.text = text; }
        public String getText() { return text; }

        @Extension
        public static class DescriptorImpl extends Descriptor<Entry> {
            @Override public String getDisplayName() { return "User Credentials"; }
        }
    }
    public static abstract class Entry extends AbstractDescribableImpl<Entry> {}


    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String key) {
        this.apiKey = apiKey;
    }

    public String getScanMethod() {
        return scanMethod;
    }

    public void setScanMethod(String scanMethod) {
        this.scanMethod = scanMethod;
    }

    public boolean isPerformAttack() {
        return performAttack;
    }

    public void setPerformAttack(boolean performAttack) {
        this.performAttack = performAttack;
    }

    public LoginProperties isUseLogin() {
        return useLogin;
    }

    public void setUseLogin(LoginProperties useLogin) {
        this.useLogin = useLogin;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiUrlDefinition() {
        return apiUrlDefinition;
    }

    public void setApiUrlDefinition(String apiUrlDefinition) {
        this.apiUrlDefinition = apiUrlDefinition;
    }

    public String generateCMD(){
        String cmd = String.format("python3 --ipaddress %s --port %s -key %s ",ipAddress,port,apiKey);

        switch (scanMethod.toUpperCase()){
            case "FULL":
                cmd += "--scan FULL ";
                break;
            case "TRADITIONAL":
                cmd += "--scan TRADITIONAL ";
                break;
            case "AJAX":
                cmd += "--scan AJAX ";
                break;
        }
        cmd += (performAttack) ? "--performAttack " : "";
        if (useLogin!=null){
            cmd += useLogin.generateCMD();
        }
        return cmd;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("ipAddress: "+this.ipAddress);
        listener.getLogger().println("port: "+this.port);
        listener.getLogger().println("key: "+this.apiKey);
        listener.getLogger().println("scanMethod: "+this.scanMethod);
        listener.getLogger().println("performAttack: "+this.performAttack);
        listener.getLogger().println(generateCMD());
        //Thread.sleep(time);
        return true;
    }


    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public static final String SCANNER_IP;
        public static final String SCANNER_PORT;
        public static final String TARGET;
        public static final String API_URL;
        public static final String API_DEFINITION;
        public static final String JSON_LOGIN_REQUEST;
        public static final String JSON_LOGIN_USERNAME_FIELD;
        public static final String JSON_LOGIN_PASSWORD_FIELD;
        public static final String LOGGED_IN_REGGEX;

        static{
            SCANNER_IP = "http://127.0.0.1";
            SCANNER_PORT = "8080";
            TARGET = "http://target_url.com";
            API_URL = "http://target_url.com/api";
            API_DEFINITION = "file:///home/marquez/Desktop/openapi.json";
            JSON_LOGIN_REQUEST = generateJSONRequest();
            JSON_LOGIN_USERNAME_FIELD = "username";
            JSON_LOGIN_PASSWORD_FIELD = "password";
            LOGGED_IN_REGGEX = "r'\\Q<a href='logout.jsp'>Logout</a>\\E";
        }

        private static String generateJSONRequest(){
            JSONObject jsonObject = new JSONObject();
            jsonObject.accumulate("name", "name");
            jsonObject.accumulate("username", "username");
            jsonObject.accumulate("password","password");

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
        public List<Descriptor> getEntryDescriptors() {
            Jenkins jenkins=Jenkins.getInstance();
            return ImmutableList.of(jenkins.getDescriptor(UserEntry.class));
        }

        public FormValidation doCheckIpAddress(@QueryParameter String ipAddress){
            try {
                new URL(ipAddress).toURI();
                return FormValidation.ok();
            } catch (MalformedURLException | URISyntaxException e) {
                return FormValidation.error(e.getMessage());
            }
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