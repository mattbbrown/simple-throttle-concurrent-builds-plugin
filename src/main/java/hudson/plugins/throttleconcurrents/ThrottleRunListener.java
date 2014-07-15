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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

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
    public Environment setUpEnvironment(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException, Run.RunnerAbortedException {
        AbstractProject project = build.getProject();
        ThrottleJobProperty tjp = (ThrottleJobProperty) project.getProperty(ThrottleJobProperty.class);
        target = tjp.getTarget();
        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                env.putIfAbsent("TARGET", target);
            }
        };
    }

    @Override
    public void onStarted(AbstractBuild build, TaskListener listener) {
        LOGGER.log(Level.SEVERE, "onStarted ran! target: {0}", target);
        listener.getLogger().println("[VHT Installation] Target machine is set to " + target);
        try {
            listener.getLogger().println("[VHT Installation] " + build.getEnvironment(listener));
        } catch (IOException ex) {
            Logger.getLogger(ThrottleRunListener.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(ThrottleRunListener.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ThrottleQueueTaskDispatcher.class.getName());
}
