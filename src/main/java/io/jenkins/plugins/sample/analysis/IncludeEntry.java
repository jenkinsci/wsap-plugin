package io.jenkins.plugins.sample.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

public class IncludeEntry extends Entry implements ConsoleSupport {
    @Getter @Setter
    private String url;

    @DataBoundConstructor
    public IncludeEntry(String url) {
        this.url = url;
    }

    @Override
    public String generateCMD() {
        return String.format("--include.url %s ", url);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Override public String getDisplayName() { return "Include Url"; }
    }
}

