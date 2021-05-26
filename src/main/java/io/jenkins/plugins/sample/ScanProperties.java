package io.jenkins.plugins.sample;

import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScanProperties implements ConsoleSupport{
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
                cmd += "--scan.type "+value.toUpperCase()+" ";
                cmd += String.format("--scan.apiUrl %s --scan.apiDefinition %s ", apiUrl, apiDefinition);
                break;
            case "TRADITIONAL":
                cmd += "--scan.type TRADITIONAL ";
                break;
            case "AJAX":
                cmd += "--scan.type AJAX ";
                break;
        }

        return cmd;
    }
}