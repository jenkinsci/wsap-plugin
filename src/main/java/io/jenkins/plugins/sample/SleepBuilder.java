package io.jenkins.plugins.sample;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.tasks.Builder;

import java.io.IOException;

public class SleepBuilder extends Builder{
    private long time;

    @DataBoundConstructor
    public SleepBuilder(long time){
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        listener.getLogger().println("Sleeping time "+time+"ms.");
        Thread.sleep(time);
        return true;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder>{

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return "SleepBuild";
        }
    }
}
