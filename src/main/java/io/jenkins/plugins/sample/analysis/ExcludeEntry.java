package io.jenkins.plugins.sample.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

public class ExcludeEntry extends Entry implements ConsoleSupport {
    @Getter @Setter
    private String url;

    @DataBoundConstructor
    public ExcludeEntry(String url) {
        this.url = url;
    }

    @Override
    public String generateCMD() {
        return String.format("--exclude.url  %s ", url);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Override public String getDisplayName() { return "Exclude Url"; }
    }
}

