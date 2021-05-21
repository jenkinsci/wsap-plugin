package io.jenkins.plugins.sample;

import hudson.Extension;
import hudson.model.Descriptor;
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
        return String.format("--login.user %s %s ", username, password);
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Override public String getDisplayName() { return "User Credentials"; }
    }
}
