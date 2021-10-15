package io.jenkinsci.security.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

public class UserEntry extends Entry implements ConsoleSupport {
    @Getter @Setter
    private String username;
    @Getter @Setter
    private String password;

    @DataBoundConstructor
    public UserEntry(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        json.put("username",username);
        json.put("password",password);
        return json;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Getter @Setter private String DEFAULT_USERNAME;
        @Getter @Setter private String DEFAULT_PASSWORD;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            DEFAULT_USERNAME = "dummyUsername";
            DEFAULT_PASSWORD = "secret123";
            super.load();
        }


        @Override public String getDisplayName() { return "User Credentials"; }
    }
}

