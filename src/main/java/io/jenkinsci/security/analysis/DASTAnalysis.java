package io.jenkinsci.security.analysis;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DASTAnalysis extends Entry implements ConsoleSupport {
    @Getter @Setter private ScanMethod scanMethod;
    @Getter @Setter private List<Entry> includeUrls;
    @Getter @Setter private List<Entry> excludeUrls;
    @Getter @Setter private LoginProperties loginProperties;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public DASTAnalysis(ScanMethod scanMethod, List<Entry> includeUrls, List<Entry> excludeUrls, LoginProperties loginProperties) {
        this.scanMethod = scanMethod;
        this.includeUrls = includeUrls != null ? new ArrayList<>(includeUrls) : Collections.emptyList();
        this.excludeUrls = excludeUrls != null ? new ArrayList<>(excludeUrls) : Collections.emptyList();
        if (loginProperties !=null){
            this.loginProperties = loginProperties;
        }
    }

    @Override
    public JSONObject generateJSON() {
        JSONObject json = new JSONObject();
        if (includeUrls != null){
            JSONArray includes = new JSONArray();
            for (Entry entry: includeUrls) {
                includes.add(entry.generateJSON());
            }
            json.put("includes", includes);
        }
        if (excludeUrls != null) {
            JSONArray excludes = new JSONArray();
            for (Entry entry : excludeUrls) {
                excludes.add(entry.generateJSON());
            }
            json.put("excludes", excludes);
        }
        json.put("scan.properties",scanMethod.generateJSON());

        if (loginProperties !=null){
            json.put("loginProperties", loginProperties.generateJSON());
        }
        return json;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Entry> {
        public List<Descriptor> getIncludeDescriptors() {
            Jenkins jenkins=Jenkins.getInstanceOrNull();
            return ImmutableList.of(jenkins.getDescriptor(IncludeEntry.class));
        }

        public List<Descriptor> getExcludeDescriptors() {
            Jenkins jenkins=Jenkins.getInstanceOrNull();
            return ImmutableList.of(jenkins.getDescriptor(ExcludeEntry.class));
        }
    }
}
