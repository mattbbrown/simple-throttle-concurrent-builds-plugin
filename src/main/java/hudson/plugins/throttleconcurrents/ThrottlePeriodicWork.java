/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package hudson.plugins.throttleconcurrents;

import hudson.Extension;
import hudson.model.PeriodicWork;
import hudson.util.TimeUnit2;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 *
 * @author mbrown
 */
@Extension
public class ThrottlePeriodicWork extends PeriodicWork{

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit2.MINUTES.toMillis(10);
    }

    @Override
    protected void doRun() throws Exception {
        ThrottleJobProperty.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(ThrottleJobProperty.DescriptorImpl.class);
        for (String VHT_Installation : descriptor.getCategoryNames()) {
            List<String> list = new ArrayList<String>();
            Process p;
            try {
                Runtime R = Runtime.getRuntime();
                p = R.exec("rvm ruby-1.9.3-p547@knife do knife search tags:" + VHT_Installation + " -i");
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                p.waitFor();

                String line;
                reader.readLine();
                reader.readLine();
                while ((line = reader.readLine()) != null) {
                    list.add(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            descriptor.setServers(VHT_Installation, list);
        }
    }
    
    @Override
    public long getInitialDelay() {
        return 0;
    }
    
    private static final Logger LOGGER = Logger.getLogger(ThrottleQueueTaskDispatcher.class.getName());
}
