package io.jenkins.plugins.sample;

import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoginProperties implements ConsoleSupport{
    @Getter @Setter
    private String loginURL;
    @Getter @Setter
    private String requestJson;
    @Getter @Setter
    private String usernameField;
    @Getter @Setter
    private String passwordField;
    @Getter @Setter
    private String loggedInRegex;
    @Getter @Setter
    private String loggedOutRegex;
    @Getter @Setter
    private List<Entry> entries;

    @DataBoundConstructor
    public LoginProperties(String loginUrl, String requestJson, String usernameField, String passwordField,
                           String loggedInRegex, String loggedOutRegex, List<Entry> entries){
        this.loginURL = loginUrl;
        this.requestJson = requestJson;
        this.usernameField = usernameField;
        this.passwordField = passwordField;
        this.loggedInRegex = loggedInRegex;
        this.loggedOutRegex = loggedOutRegex;
        this.entries = entries != null ? new ArrayList<>(entries) : Collections.emptyList();
    }

    @Override
    public String generateCMD() {
        String cmd = String.format(
                "--login.url %s --login.request %s --login.userField %s --login.passField %s ",
                this.loginURL,
                JSONObject.quote(this.requestJson.replaceAll("\\s+","")),
                this.usernameField,
                this.passwordField
        );
        if (loggedInRegex !=null && !loggedInRegex.isEmpty()){
            cmd+= String.format("--login.loggedInRegex %s ",loggedInRegex);
        }
        if (loggedOutRegex !=null && !loggedOutRegex.isEmpty()){
            cmd+= String.format("--login.loggedOutRegex %s ",loggedOutRegex);
        }

        for (Entry entry:entries) {
            cmd+=entry.generateCMD();
        }
        return cmd;
    }
}