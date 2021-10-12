package io.jenkinsci.security.analysis;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LoginProperties extends Entry implements ConsoleSupport {
    @Getter @Setter
    private Boolean useLogin;
    @Getter @Setter
    private String loginUrl;
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
    private List<Entry> headers;
    @Getter @Setter
    private List<Entry> users;

    @DataBoundConstructor
    public LoginProperties(Boolean useLogin, String loginUrl, String requestJson, String usernameField, String passwordField,
                           String loggedInRegex, String loggedOutRegex, List<Entry> headers, List<Entry> users){
        this.useLogin = useLogin;
        this.loginUrl = loginUrl;
        this.requestJson = requestJson;
        this.usernameField = usernameField;
        this.passwordField = passwordField;
        this.loggedInRegex = loggedInRegex;
        this.loggedOutRegex = loggedOutRegex;
        this.headers = headers != null ? new ArrayList<>(headers) : Collections.emptyList();
        this.users = users != null ? new ArrayList<>(users) : Collections.emptyList();
    }

    public boolean isAlwaysTrue(){
        return true;
    }

    @Override
    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        json.put("login.url",this.loginUrl);
        json.put("login.request",requestJson.replaceAll("\\s+",""));
        json.put("login.userField",this.usernameField);
        json.put("login.passField",this.passwordField);

        JSONArray headersList = new JSONArray();
        for (Entry entry: headers) {
            headersList.add(entry.generateJSON());
        }
        json.put("login.headers",headersList);

        JSONArray usersList = new JSONArray();
        for (Entry entry: users) {
            usersList.add(entry.generateJSON());
        }
        json.put("users",usersList);

        /*if (loggedInRegex !=null && !loggedInRegex.isEmpty()){
            cmd+= String.format("--login.loggedInRegex %s ",loggedInRegex);
        }
        if (loggedOutRegex !=null && !loggedOutRegex.isEmpty()){
            cmd+= String.format("--login.loggedOutRegex %s ",loggedOutRegex);
        }*/
        return json;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        public String LOGIN_URL;
        public String JSON_LOGIN_REQUEST;
        public String JSON_LOGIN_USERNAME_FIELD;
        public String JSON_LOGIN_PASSWORD_FIELD;
        //public String LOGGED_IN_REGGEX;
        //public String LOGGED_OUT_REGGEX;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            LOGIN_URL = "http://target_url.com/authentication/login";
            JSON_LOGIN_REQUEST = generateJSONRequest();
            JSON_LOGIN_USERNAME_FIELD = "username";
            JSON_LOGIN_PASSWORD_FIELD = "password";
            //LOGGED_IN_REGGEX = "r'\\Q<a href='logout.jsp'>Logout</a>\\E";
            //LOGGED_OUT_REGGEX = "";
            super.load();
        }

        @Override
        public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
            save();
            return super.configure(req, json);
        }

        private static String generateJSONRequest(){
            net.sf.json.JSONObject jsonObject = new net.sf.json.JSONObject();
            jsonObject.put("name", "name");
            jsonObject.put("username", "username");
            jsonObject.put("password","password");

            return jsonObject.toString(4);
        }

        /**
         * Return a describer a list of Child Descriptors
         * Used in order to add multiple user credentials
         * @return
         */
        public List<Descriptor> getHeaderDescriptors() {
            Jenkins jenkins=Jenkins.getInstanceOrNull();
            return ImmutableList.of(jenkins.getDescriptor(HeaderEntry.class));
        }

        /**
         * Return a describer a list of Child Descriptors
         * Used in order to add multiple user credentials
         * @return
         */
        public List<Descriptor> getUsersDescriptors() {
            Jenkins jenkins=Jenkins.getInstanceOrNull();
            return ImmutableList.of(jenkins.getDescriptor(UserEntry.class));
        }

    }
}