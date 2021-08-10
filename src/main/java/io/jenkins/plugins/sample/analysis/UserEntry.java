package io.jenkins.plugins.sample.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import lombok.Getter;
import lombok.Setter;
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
    public String generateCMD() {
        return String.format("--login.user \"%s\" \"%s\" ", username, password);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        public String DEFAULT_USERNAME;
        public String DEFAULT_PASSWORD;

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

