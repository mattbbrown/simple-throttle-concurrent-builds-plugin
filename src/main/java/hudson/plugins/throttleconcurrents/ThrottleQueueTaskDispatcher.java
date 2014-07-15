package hudson.plugins.throttleconcurrents;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

@Extension
public class ThrottleQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        ThrottleJobProperty tjp = getThrottleJobProperty(item.task);
        String VHT_Installation = tjp.getCategories().get(0);
        //List<String> servers = getServersFromChef(VHT_Installation);
        List<String> servers = new ArrayList<String>();
        servers.add("ANDERSON.devlab.local");
        servers.add("TERMINUS.qalab.local");
        boolean foundFreeServer = false;
        for (String server : servers) {
            boolean serverFree = serverIsFree(server);
            if (serverFree) {
                foundFreeServer = true;
                LOGGER.log(Level.SEVERE, "A Free Server Was Found, that server was {0}", server);   
                tjp.setTarget(server);
                break;
            }
        }
        if (!foundFreeServer) {
            LOGGER.log(Level.SEVERE, "No Free Server Found");
            return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_NoFreeServers(VHT_Installation));
        }
        return null;
    }

    private List<String> getServersFromChef(String VHT_Installation) {
        List<String> list = new ArrayList<String>();
        Process p;
        String[] cmdarray = {"knife", "search", "tags:" + VHT_Installation, "-i"};
        try {
            p = Runtime.getRuntime().exec(cmdarray);
            p.waitFor();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            LOGGER.log(Level.SEVERE, "Line 1: {0}", reader.readLine());
            LOGGER.log(Level.SEVERE, "Line 2: {0}", reader.readLine());
            String line;
            while ((line = reader.readLine()) != null) {
                list.add(line);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
    
    private boolean serverIsFree(String serverName) {
        boolean serverFree = true;
        if (serverIsBeingUsedOnNode(Hudson.getInstance(), serverName)) {
            serverFree = false;
        } else {
            for (Node node : Hudson.getInstance().getNodes()) {
                if (serverIsBeingUsedOnNode(node, serverName)) {
                    serverFree = false;
                }
            }
        }
        return serverFree;
    }

    private boolean serverIsBeingUsedOnNode(Node node, String serverName) {
        boolean serverBeingUsedOnNode = false;
        Computer computer = node.toComputer();
        if (computer != null) {
            for (Executor e : computer.getExecutors()) {
                if (buildOnExecutorUsingServer(serverName, e)) {
                    serverBeingUsedOnNode = true;
                    break;
                }
            }
        }
        return serverBeingUsedOnNode;
    }

    private boolean buildOnExecutorUsingServer(String serverName, Executor exec) {
        if (exec.getCurrentExecutable() != null) {
            Task task = exec.getCurrentExecutable().getParent().getOwnerTask();
            ThrottleJobProperty tjp = getThrottleJobProperty(task);
            if (tjp.getTarget() != null) {
                return tjp.getTarget().equals(serverName);
            }
        }
        return false;
    }
    
    @CheckForNull
    private ThrottleJobProperty getThrottleJobProperty(Task task) {
        if (task instanceof AbstractProject) {
            AbstractProject<?, ?> p = (AbstractProject<?, ?>) task;
            if (task instanceof MatrixConfiguration) {
                p = (AbstractProject<?, ?>) ((MatrixConfiguration) task).getParent();
            }
            ThrottleJobProperty tjp = p.getProperty(ThrottleJobProperty.class);
            return tjp;
        }
        return null;
    }

    private static final Logger LOGGER = Logger.getLogger(ThrottleQueueTaskDispatcher.class.getName());
}