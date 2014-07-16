/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.throttleconcurrents;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.util.Map;

/**
 *
 * @author mbrown
 */
@Extension
public final class ThrottleRunListener extends RunListener<AbstractBuild> {

    String target = "";

    public ThrottleRunListener() {
        super(AbstractBuild.class);
    }

    @Override
    public Environment setUpEnvironment(final AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        AbstractProject project = build.getProject();
        ThrottleJobProperty tjp = (ThrottleJobProperty) project.getProperty(ThrottleJobProperty.class);
        if (tjp == null) {
            return new Environment() {};
        }
        target = tjp.getTarget();
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.put("TARGET", target);
            }
        };
    }

    @Override
    public void onStarted(AbstractBuild build, TaskListener listener) {
        AbstractProject project = build.getProject();
        ThrottleJobProperty tjp = (ThrottleJobProperty) project.getProperty(ThrottleJobProperty.class);
        if(tjp != null){
        target = tjp.getTarget();
        listener.getLogger().println("[VHT Installation] Target set to " + target);
        }
    }
}
