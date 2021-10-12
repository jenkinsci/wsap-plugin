package io.jenkinsci.security.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import static io.jenkinsci.security.analysis.ScanMethod.DescriptorImpl.DEFAULT_SCAN;

public class ScanMethod extends Entry implements ConsoleSupport {
    @Getter @Setter
    private String scan;
    @Getter @Setter
    private String apiUrl;
    @Getter @Setter
    private String apiDefinition;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public ScanMethod(String scan, String apiUrl, String apiDefinition){
       this.scan = scan;
       this.apiUrl = apiUrl;
       this.apiDefinition = apiDefinition;
    }

    public boolean isScan(String scan){
        boolean isScanInvalid = this.scan == null || this.scan.isEmpty();
        boolean isNewScanDefault = scan.equals(DEFAULT_SCAN);
        if(isScanInvalid){
            return isNewScanDefault;
        }
        return scan.equals(this.scan);
    }

    @Override
    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        switch (scan.toUpperCase()){
            case "FULL":
                json.put("scan.mode", "FULL");
                json.put("scan.apiUrl", apiUrl);
                json.put("scan.apiDefinition", apiDefinition);
                break;
            case "TRADITIONAL":
                json.put("scan.mode", "TRADITIONAL");
                break;
            case "AJAX":
                json.put("scan.mode", "AJAX");
                break;
        }
        return json;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        public static final String DEFAULT_SCAN = "FULL";
        public String API_URL;
        public String API_DEFINITION;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            API_URL = "http://TARGET_URL_API";
            API_DEFINITION = "file:///API_DEFINITION_LOCATION/openapi.json";
            super.load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            save();
            return super.configure( req, json );
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