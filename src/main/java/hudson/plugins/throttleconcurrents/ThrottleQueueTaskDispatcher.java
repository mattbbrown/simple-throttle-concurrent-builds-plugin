package hudson.plugins.throttleconcurrents;

import hudson.Extension;
import hudson.matrix.MatrixConfiguration;
import hudson.matrix.MatrixProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Queue.Task;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.plugins.throttleconcurrents.ThrottleJobProperty.DescriptorImpl;
import hudson.plugins.throttleconcurrents.ThrottleJobProperty.ThrottleCategory;
import java.util.ArrayList;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

@Extension
public class ThrottleQueueTaskDispatcher extends QueueTaskDispatcher {

    String paramCategoryName = "Parametrized_Lock";
    String paramName = "LOCK";

    @Override
    public CauseOfBlockage canRun(Queue.Item item) {
        ThrottleJobProperty tjp = getThrottleJobProperty(item.task);
        if (tjp != null && tjp.getThrottleEnabled()) {
            return canRun(item, tjp);
        }
        return null;
    }

    @Nonnull
    private ThrottleMatrixProjectOptions getMatrixOptions(Task task) {
        ThrottleJobProperty tjp = getThrottleJobProperty(task);
        if (tjp == null) {
            return ThrottleMatrixProjectOptions.DEFAULT;
        }
        ThrottleMatrixProjectOptions matrixOptions = tjp.getMatrixOptions();
        return matrixOptions != null ? matrixOptions : ThrottleMatrixProjectOptions.DEFAULT;
    }

    private boolean shouldBeThrottled(@Nonnull Task task, @CheckForNull ThrottleJobProperty tjp) {
        if (tjp == null) {
            return false;
        }
        if (!tjp.getThrottleEnabled()) {
            return false;
        }

        // Handle matrix options
        ThrottleMatrixProjectOptions matrixOptions = tjp.getMatrixOptions();
        if (matrixOptions == null) {
            matrixOptions = ThrottleMatrixProjectOptions.DEFAULT;
        }
        if (!matrixOptions.isThrottleMatrixConfigurations() && task instanceof MatrixConfiguration) {
            return false;
        }
        if (!matrixOptions.isThrottleMatrixBuilds() && task instanceof MatrixProject) {
            return false;
        }

        // Allow throttling by default
        return true;
    }

    public CauseOfBlockage canRun(Queue.Item item, ThrottleJobProperty tjp) {
        Task task = item.task;
        String paramLock = null;
        String params = item.getParams().concat("\n");
        if (params.contains(paramName)) {
            int indOfLock = params.indexOf(paramName) + paramName.length() + 1;
            paramLock = params.substring(indOfLock, params.indexOf("\n", indOfLock));
        }

        LOGGER.log(Level.SEVERE, "paramTarget: {0}", paramLock);
        if (!shouldBeThrottled(task, tjp)) {
            return null;
        }
        if (Hudson.getInstance().getQueue().isPending(task)) {
            return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_BuildPending());
        }
        if (tjp.getThrottleOption().equals("project")) {
            if (tjp.getMaxConcurrentTotal().intValue() > 0) {
                int maxConcurrentTotal = tjp.getMaxConcurrentTotal().intValue();
                int totalRunCount = buildsOfProjectOnAllNodes(task, paramLock, null);

                if (totalRunCount >= maxConcurrentTotal) {
                    return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_MaxCapacityTotal(totalRunCount));
                }
            }
        } // If the project is in one or more categories...
        else if (tjp.getThrottleOption().equals("category")) {
            if (tjp.getCategories() != null && !tjp.getCategories().isEmpty()) {
                for (String catNm : tjp.getCategories()) {
                    // Quick check that catNm itself is a real string.
                    if (catNm != null && !catNm.equals("")) {
                        List<AbstractProject<?, ?>> categoryProjects = ThrottleJobProperty.getCategoryProjects(catNm);
                        if (catNm.equals(paramCategoryName)) {
                            categoryProjects.addAll(ThrottleJobProperty.getCategoryProjects(paramLock));
                            LOGGER.log(Level.SEVERE, "task: {0}\nallProjectsWithCategories: {1}", new Object[]{task, categoryProjects});
                        } else {
                            categoryProjects.addAll(ThrottleJobProperty.getCategoryProjects(paramCategoryName));
                        }

                        ThrottleJobProperty.ThrottleCategory category = ((ThrottleJobProperty.DescriptorImpl) tjp.getDescriptor()).getCategoryByName(catNm);

                        // Double check category itself isn't null
                        if (category != null) {
                            if (category.getMaxConcurrentTotal().intValue() > 0) {
                                int maxConcurrentTotal = category.getMaxConcurrentTotal().intValue();
                                int totalRunCount = 0;
                                for (AbstractProject<?, ?> catProj : categoryProjects) {
                                    if (Hudson.getInstance().getQueue().isPending(catProj)) {
                                        return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_BuildPending());
                                    }
                                    totalRunCount += buildsOfProjectOnAllNodes(catProj, paramLock, catNm);
                                }

                                if (totalRunCount >= maxConcurrentTotal) {
                                    return CauseOfBlockage.fromMessage(Messages._ThrottleQueueTaskDispatcher_MaxCapacityTotal(totalRunCount));
                                }
                            }

                        }
                    }
                }
            }
        }
        return null;
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

    private int buildsOfProjectOnNode(Node node, Task task, String paramTarget, String catNm) {
        int runCount = 0;
        LOGGER.log(Level.FINE, "Checking for builds of {0} on node {1}", new Object[]{task.getName(), node.getDisplayName()});

        // I think this'll be more reliable than job.getBuilds(), which seemed to not always get
        // a build right after it was launched, for some reason.
        Computer computer = node.toComputer();
        if (computer != null) { //Not all nodes are certain to become computers, like nodes with 0 executors.
            for (Executor e : computer.getExecutors()) {
                runCount += buildsOnExecutor(task, e, paramTarget, catNm);
            }

            ThrottleMatrixProjectOptions matrixOptions = getMatrixOptions(task);
            if (matrixOptions.isThrottleMatrixBuilds() && task instanceof MatrixProject) {
                for (Executor e : computer.getOneOffExecutors()) {
                    runCount += buildsOnExecutor(task, e, paramTarget, catNm);
                }
            }
            if (matrixOptions.isThrottleMatrixConfigurations() && task instanceof MatrixConfiguration) {
                for (Executor e : computer.getOneOffExecutors()) {
                    runCount += buildsOnExecutor(task, e, paramTarget, catNm);
                }
            }
        }

        return runCount;
    }

    private int buildsOfProjectOnAllNodes(Task task, String paramTarget, String catNm) {
        int totalRunCount = buildsOfProjectOnNode(Hudson.getInstance(), task, paramTarget, catNm);

        for (Node node : Hudson.getInstance().getNodes()) {
            totalRunCount += buildsOfProjectOnNode(node, task, paramTarget, catNm);
        }
        return totalRunCount;
    }

    private int buildsOnExecutor(Task task, Executor exec, String paramTarget, String catNm) {
        int runCount = 0;
        if (exec.getCurrentExecutable() != null && exec.getCurrentExecutable().getParent() == task) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) exec.getCurrentExecutable();
            String buildParam = build != null ? build.getBuildVariables().get(paramName) : null;
            if (buildParam != null) {
                LOGGER.log(Level.SEVERE, "buildParam: {0}", buildParam);
                LOGGER.log(Level.SEVERE, "paramTarget: {0}", paramTarget);
                LOGGER.log(Level.SEVERE, "buildParam.equals(paramTarget): {0}", buildParam.equals(paramTarget));
            }
            if (buildParam == null || buildParam.equals(paramTarget) || buildParam.equals(catNm)) {
                runCount++;
            }
        }
        return runCount;
    }

    /**
     * @param node to compare labels with.
     * @param category to compare labels with.
     * @param maxConcurrentPerNode to return if node labels mismatch.
     * @return maximum concurrent number of builds per node based on matching
     * labels, as an int.
     * @author marco.miller@ericsson.com
     */
    private int getMaxConcurrentPerNodeBasedOnMatchingLabels(
            Node node, ThrottleJobProperty.ThrottleCategory category, int maxConcurrentPerNode) {
        List<ThrottleJobProperty.NodeLabeledPair> nodeLabeledPairs = category.getNodeLabeledPairs();
        int maxConcurrentPerNodeLabeledIfMatch = maxConcurrentPerNode;
        boolean nodeLabelsMatch = false;
        Set<LabelAtom> nodeLabels = node.getAssignedLabels();

        for (ThrottleJobProperty.NodeLabeledPair nodeLabeledPair : nodeLabeledPairs) {
            String throttledNodeLabel = nodeLabeledPair.getThrottledNodeLabel();
            if (!nodeLabelsMatch && !throttledNodeLabel.isEmpty()) {
                for (LabelAtom aNodeLabel : nodeLabels) {
                    String nodeLabel = aNodeLabel.getDisplayName();
                    if (nodeLabel.equals(throttledNodeLabel)) {
                        maxConcurrentPerNodeLabeledIfMatch = nodeLabeledPair.getMaxConcurrentPerNodeLabeled().intValue();
                        LOGGER.log(Level.FINE, "node labels match; => maxConcurrentPerNode'' = {0}", maxConcurrentPerNodeLabeledIfMatch);
                        nodeLabelsMatch = true;
                        break;
                    }
                }
            }
        }
        if (!nodeLabelsMatch) {
            LOGGER.fine("node labels mismatch");
        }
        return maxConcurrentPerNodeLabeledIfMatch;
    }

    private static final Logger LOGGER = Logger.getLogger(ThrottleQueueTaskDispatcher.class.getName());
}
