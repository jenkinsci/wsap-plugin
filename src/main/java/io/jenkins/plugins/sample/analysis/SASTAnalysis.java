package io.jenkins.plugins.sample.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

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
    public static class DescriptorImpl extends Descriptor<Entry> {
        public String TARGET;

        public DescriptorImpl() {
            load();
        }

        @Override
        public synchronized void load() {
            TARGET = "/home/jenkins/vulnado";
            super.load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            save();
            return super.configure( req, json );
        }
    }
}