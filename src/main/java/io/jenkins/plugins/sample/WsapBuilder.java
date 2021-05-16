package io.jenkins.plugins.sample;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.URI;
import java.net.URL;

public class WsapBuilder extends Builder {
    public String getMyString()
    {
        return "Hello Jenkins!";
    }

    private String ipAddress;
    private int port;
    private String apiKey;
    private String scanMethod;
    private boolean performAttack;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public WsapBuilder(String ipAddress, int port, String apiKey, String scanMethod, boolean performAttack){
        this.ipAddress = ipAddress;
        this.port = port;
        this.apiKey = "vcvicclkl5kegm34aba9dhroem";
        this.scanMethod = scanMethod;
        this.performAttack = performAttack;
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

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("ipAddress: "+this.ipAddress);
        listener.getLogger().println("port: "+this.port);
        listener.getLogger().println("key: "+this.apiKey);
        listener.getLogger().println("scanMethod: "+this.scanMethod);
        listener.getLogger().println("performAttack: "+this.performAttack);
        //Thread.sleep(time);
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "Web Security Application Project (WSAP)";
        }

        public FormValidation doCheckIpAddress(@QueryParameter String value){
            return FormValidation.error(value);
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

        public FormValidation doCheckApiUrl(@QueryParameter String url){
                try {
                    new URL(url);
                    return FormValidation.ok();
                } catch (Exception e) {
                    return FormValidation.error("Invalid URL");
                }
        }

        public FormValidation doCheckApiDefinition(@QueryParameter String uri){
            try {
                new URI(uri);
                return FormValidation.ok();
            } catch (Exception e) {
                return FormValidation.error("Invalid URI");
            }
        }

        public FormValidation doCheckScanMethod(@QueryParameter String value){
            return FormValidation.error(value);
        }
    }
}