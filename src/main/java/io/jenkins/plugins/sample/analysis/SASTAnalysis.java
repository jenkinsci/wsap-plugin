package io.jenkins.plugins.sample.analysis;

import io.jenkins.plugins.sample.ConsoleSupport;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

public class SASTAnalysis implements ConsoleSupport {
    @Getter @Setter private String target;

    @DataBoundConstructor
    public SASTAnalysis(String target){
        this.target = target;
    }

    public String generateCMD() {
        String cmd = String.format("--target %s ",target);
        return cmd;
    }
}
