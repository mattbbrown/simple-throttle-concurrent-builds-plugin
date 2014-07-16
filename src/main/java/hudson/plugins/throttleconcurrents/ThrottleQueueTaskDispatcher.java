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

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

@Extension
public class ThrottleQueueTaskDispatcher extends QueueTaskDispatcher {

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        ThrottleJobProperty tjp = getThrottleJobProperty(item.task);
        if (tjp == null) {
            return null;
        }
        String VHT_Installation = tjp.getCategories().get(0);
        List<String> servers;
        ThrottleJobProperty.DescriptorImpl descriptor =  (ThrottleJobProperty.DescriptorImpl)tjp.getDescriptor();
        servers = descriptor.getServersFromTJP(VHT_Installation);
        
        boolean foundFreeServer = false;
        for (String server : servers) {
            boolean serverFree = serverIsFree(server);
            if (serverFree) {
                foundFreeServer = true;
                tjp.setTarget(server);
                break;
            }
        }
        if (!foundFreeServer) {
            return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_NoFreeServers(VHT_Installation));
        }
        return null;
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
