package io.jenkinsci.security.analysis;

import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

public class ExcludeEntry extends Entry implements ConsoleSupport {
    @Getter @Setter
    private String url;

    @DataBoundConstructor
    public ExcludeEntry(String url) {
        this.url = url;
    }

    @Override
    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        json.put("exclude.url", url);
        return json;

    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        @Override public String getDisplayName() { return "Exclude Url"; }
    }
}

