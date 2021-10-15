package io.jenkinsci.security.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
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

    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        json.put("target", target);
        return json;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Getter @Setter private String TARGET;

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