package io.jenkins.plugins.sample;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collections;
import java.util.List;

public class LoginProperties
{
    private String loginURL;
    private String requestJson;
    private String usernameField;
    private String passwordField;
    private String loggedInRegex;
    private String loggedOutRegex;



    @DataBoundConstructor
    public LoginProperties(String loginUrl, String requestJson, String usernameField, String passwordField,
                           String loggedInRegex, String loggedOutRegex){
        this.loginURL = loginUrl;
        this.requestJson = requestJson;
        this.usernameField = usernameField;
        this.passwordField = passwordField;
        this.loggedInRegex = loggedInRegex;
        this.loggedOutRegex = loggedOutRegex;

    }

    public String getLoggedInRegex() {
        return loggedInRegex;
    }

    public void setLoggedInRegex(String loggedInRegex) {
        this.loggedInRegex = loggedInRegex;
    }

    public String getLoggedOutRegex() {
        return loggedOutRegex;
    }

    public void setLoggedOutRegex(String loggedOutRegex) {
        this.loggedOutRegex = loggedOutRegex;
    }

    public String getUsernameField() {
        return usernameField;
    }

    public void setUsernameField(String usernameField) {
        this.usernameField = usernameField;
    }

    public String getPasswordField() {
        return passwordField;
    }

    public void setPasswordField(String passwordField) {
        this.passwordField = passwordField;
    }

    public String getLoginURL() {
        return loginURL;
    }

    public void setLoginURL(String loginURL) {
        this.loginURL = loginURL;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(String requestJson) {
        this.requestJson = requestJson;
    }

    public String generateCMD() {
        String cmd = "";
        cmd +=  String.format(
                "--loginUrl %s --requestLoginJSON %s --usernameField %s --passwordField %s ",
                this.loginURL,
                this.requestJson.replaceAll("\\s+",""),
                this.usernameField,
                this.passwordField
        );
        cmd += (this.loggedInRegex!=null) ? "--loggedInRegex "+this.loggedInRegex : "";
        cmd += (this.loggedOutRegex!=null) ? "--loggedOutRegex "+this.loggedOutRegex : "";
        return cmd;
    }
}