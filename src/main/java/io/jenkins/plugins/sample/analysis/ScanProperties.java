package io.jenkins.plugins.sample.analysis;

import io.jenkins.plugins.sample.ConsoleSupport;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

public class ScanProperties implements ConsoleSupport {
    @Getter @Setter
    private String value;
    @Getter @Setter
    private String apiUrl;
    @Getter @Setter
    private String apiDefinition;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public ScanProperties(String value, String apiUrl, String apiDefinition){
       this.value = value;
       this.apiUrl = apiUrl;
       this.apiDefinition = apiDefinition;
    }

    @Override
    public String generateCMD() {
        String cmd ="";
        switch (value.toUpperCase()){
            case "FULL":  case "APIONLY":
                cmd += "--scan.mode "+value.toUpperCase()+" ";
                cmd += String.format("--scan.apiUrl %s --scan.apiDefinition %s ", apiUrl, apiDefinition);
                break;
            case "TRADITIONAL":
                cmd += "--scan.mode TRADITIONAL ";
                break;
            case "AJAX":
                cmd += "--scan.mode AJAX ";
                break;
        }

        return cmd;
    }
}