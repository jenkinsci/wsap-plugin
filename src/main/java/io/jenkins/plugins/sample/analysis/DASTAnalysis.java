package io.jenkins.plugins.sample.analysis;

import io.jenkins.plugins.sample.ConsoleSupport;
import io.jenkins.plugins.sample.Entry;
import lombok.Getter;
import lombok.Setter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

public class DASTAnalysis implements ConsoleSupport {
    @Getter @Setter private List<Entry> includeUrls;
    @Getter @Setter private List<Entry> excludeUrls;
    @Getter @Setter private ScanProperties scanMethod;
    //Login Properties
    @Getter @Setter private LoginProperties useLogin;

    @DataBoundConstructor
    @SuppressWarnings("unused")
    public DASTAnalysis(List<Entry> includeUrls, List<Entry> excludeUrls, ScanProperties scanMethod, LoginProperties useLogin) {
        this.includeUrls=includeUrls;
        this.excludeUrls=excludeUrls;
        this.scanMethod = scanMethod;
        if (useLogin!=null){
            this.useLogin = useLogin;
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

        if (useLogin!=null){
            cmd += useLogin.generateCMD();
        }
        return cmd;
    }
}
