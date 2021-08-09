package io.jenkins.plugins.sample.analysis;

import com.google.common.collect.ImmutableList;
import hudson.Extension;
import hudson.model.Descriptor;
import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

public class DASTAnalysis extends Entry implements ConsoleSupport {
    @Getter @Setter private ScanMethod scanMethod;
    //@Getter @Setter private List<Entry> includeUrls;
    //@Getter @Setter private List<Entry> excludeUrls;
    //Login Properties
    //@Getter @Setter private LoginProperties useLogin;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public DASTAnalysis(ScanMethod scanMethod) { //, List<Entry> includeUrls, List<Entry> excludeUrls, LoginProperties useLogin
        this.scanMethod = scanMethod;
        //this.includeUrls = includeUrls;
        //this.excludeUrls = excludeUrls;
        /*if (useLogin!=null){
            this.useLogin = useLogin;
        }*/
    }

    @Override
    public String generateCMD() {
        String cmd = "";

        /*if (includeUrls != null){
            for (Entry entry: includeUrls) {
                cmd+=entry.generateCMD();
            }
        }
        if (excludeUrls != null) {
            for (Entry entry : excludeUrls) {
                cmd += entry.generateCMD();
            }
        }*/
        cmd += scanMethod.generateCMD();

        /*if (useLogin!=null){
            cmd += useLogin.generateCMD();
        }*/
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
