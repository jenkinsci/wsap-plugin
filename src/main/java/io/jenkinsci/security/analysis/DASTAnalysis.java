package io.jenkinsci.security.analysis;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkinsci.security.ConsoleSupport;
import io.jenkinsci.security.Entry;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
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
    public String generateCMD() {
        String cmd = "";

        if (includeUrls != null){
            for (Entry entry: includeUrls) {
                cmd+=entry.generateCMD();
            }
        }
        if (excludeUrls != null) {
            for (Entry entry : excludeUrls) {
                cmd += entry.generateCMD();
            }
        }
        cmd += scanMethod.generateCMD();

        if (loginProperties !=null){
            cmd += loginProperties.generateCMD();
        }
        return cmd;
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
