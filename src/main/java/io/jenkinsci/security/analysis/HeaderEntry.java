package io.jenkinsci.security.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

public class HeaderEntry extends Entry implements ConsoleSupport {
    @Getter @Setter
    private String header;

    @DataBoundConstructor
    public HeaderEntry(String header) {
        this.header = header;
    }

    @Override
    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        json.put("header", header);
        return json;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Override public String getDisplayName() { return "Include Header"; }
    }
}

