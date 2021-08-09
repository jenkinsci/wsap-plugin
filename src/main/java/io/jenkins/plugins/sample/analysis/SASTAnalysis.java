package io.jenkins.plugins.sample.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

public class SASTAnalysis extends Entry implements ConsoleSupport {
    @Getter @Setter private String target;

    @DataBoundConstructor
    public SASTAnalysis(String target){
        this.target = target;
    }

    public String generateCMD() {
        String cmd = String.format("--target %s ",target);
        return cmd;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {}
}