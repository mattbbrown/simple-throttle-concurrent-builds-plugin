/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.plugins.throttleconcurrents;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildWrapper;

import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author mbrown
 */
public class ThrottleWrapper extends BuildWrapper {

    String target = "";

    @Override
    public Environment setUp(AbstractBuild abstractBuild, Launcher launcher, BuildListener buildListener) throws IOException, InterruptedException {
        AbstractProject p = abstractBuild.getProject();
        ThrottleJobProperty tjp = (ThrottleJobProperty) p.getProperty(ThrottleJobProperty.class);
        target = tjp.getTarget();
        EnvVars env = abstractBuild.getEnvironment(buildListener);
        env.put("TARGET", target);
        makeBuildVariables(abstractBuild, env);
        buildListener.getLogger().println("Target machine is set to " + target);
        buildListener.getLogger().println(abstractBuild.getBuildVariables());
        return new Environment() {
            @Override
            public boolean tearDown(AbstractBuild abstractBuild, BuildListener buildListener) throws IOException, InterruptedException {
                buildListener.getLogger().println("released " + target);
                return super.tearDown(abstractBuild, buildListener);
            }
        };
    }
    
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    public static final class DescriptorImpl extends Descriptor<BuildWrapper> {
        
        public DescriptorImpl() {
            super(ThrottleWrapper.class);
        }

        @Override
        public String getDisplayName() { return ""; }
    }
}
    
