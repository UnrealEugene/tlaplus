package tlc2.diploma.graph;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import tlc2.TLCGlobals;
import tlc2.diploma.graph.algo.*;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.ModelChecker;
import tlc2.tool.TLCState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class StateGraphPathExtractor {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final int INF = Integer.MAX_VALUE;

    private final StateNetwork network = new StateNetwork();

    private int stateCount = 0;

    private int actionCount = 0;
    private int pathCount = 0;
    public StateGraphPathExtractor() {
        network.addNode(null); // source
    }

    public int addState(TLCState state) {
        return network.addNode(state) - 1;
    }

    public int addAction(TLCState from, TLCState to, ConcreteAction action) {
        return network.addEdge(from, to, action) / 2;
    }

    private void constructNetwork() {
        network.addNode(null); // sink
        network.shutdown();
        network.ensureEdgeCapacity(network.getEdgeCount() + 4 * network.getNodeCount());

        MutableIntList degInOutDiffs = IntArrayList.newWithNValues(network.getNodeCount(), 0);
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge edge = network.getEdge(i);
            degInOutDiffs.set(edge.getFrom(), degInOutDiffs.get(edge.getFrom()) - 1);
            degInOutDiffs.set(edge.getTo(), degInOutDiffs.get(edge.getTo()) + 1);
        }

        for (int i = 0; i < network.getNodeCount(); i++) {
            if (i == network.getSource() || i == network.getSink()) {
                continue;
            }
            int degInOutDiff = degInOutDiffs.get(i);
            if (degInOutDiff > 0) {
                network.addEdge(network.getSource(), i, degInOutDiff);
            } else if (degInOutDiff < 0) {
                network.addEdge(i, network.getSink(), -degInOutDiff);
            }

            if (i == network.getRoot()) {
                continue;
            }
            network.addEdge(i, network.getRoot(), INF / 2);
        }
    }

    private int calculatePathCount() {
        pathCount = 0;
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge fwd = network.getEdge(i);
            if (fwd.getFrom() != network.getSource() && fwd.getTo() == network.getRoot()) {
                pathCount += fwd.getFlow() + (fwd.hasAction() ? 1 : 0);
            }
        }
        return pathCount;
    }

    public int getStateCount() {
        return stateCount;
    }

    public int getActionCount() {
        return actionCount;
    }



    public int getPathCount() {
        return pathCount;
    }

    private int calculatePathCoverTotalLength() {
        int result = 0;
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge fwd = network.getEdge(i);
            if (fwd.hasAction()) {
                result += fwd.getFlow();
            }
        }
        return result;
    }

    private boolean checkAcyclicDfs(int v, MutableIntList color) {
        color.set(v, 1);
        IntList adjListV = network.getAdjacentEdgeIds(v);
        for (int i = 0; i < adjListV.size(); i++) {
            StateNetwork.Edge edge = network.getEdge(adjListV.get(i));
            if (!edge.isForward()) {
                continue;
            }
            int to = edge.getTo();
            if ((to == network.getRoot() && !edge.hasAction()) || to == network.getSink() || to == v) {
                continue;
            }
            if (color.get(to) == 1) {
                return false;
            }
            if (color.get(to) != 0) {
                continue;
            }
            if (!checkAcyclicDfs(to, color)) {
                return false;
            }
        }
        color.set(v, 2);
        return true;
    }

    private boolean isGraphAcyclic() {
        return checkAcyclicDfs(network.getRoot(), IntArrayList.newWithNValues(network.getNodeCount(), 0));
    }

    private String now() {
        return SDF.format(new Date());
    }

    public Iterable<List<Edge>> extractPaths() {
        stateCount = network.getNodeCount() - 1;
        actionCount = network.getEdgeCount() / 2;

        MP.printMessage(EC.GENERAL, "Path cover construction started ("
                + MP.format(stateCount) + " states, "
                + MP.format(actionCount) + " transitions, " + now() + ")");

        this.constructNetwork();

        boolean graphAcyclic = this.isGraphAcyclic();
        if (!graphAcyclic) {
            MP.printMessage(EC.GENERAL, "  WARNING: model state graph contains CYCLES!");
        }

        int reducedActionCount = new PartialOrderReducer(this.network).reduce();
        if (reducedActionCount > 0) {
            MP.printMessage(EC.GENERAL, "Optimized out " + MP.format(reducedActionCount) + " transitions " +
                    "using partial order reduction (" + MP.format(actionCount - reducedActionCount) + " transitions left).");
        }

        MaxFlowSolver maxFlowSolver = graphAcyclic
                ? new NaiveMaxFlowSolver(this.network)
                : new DinicMaxFlowSolver(this.network);
        maxFlowSolver.findMaxFlow();

        int pathCount = this.calculatePathCount();
        MP.printMessage(EC.GENERAL, "  Constructed initial path cover ("
                + MP.format(pathCount) + " paths, " + now() + ").");

        int depth = INF;
        try {
            depth = ((ModelChecker) TLCGlobals.mainChecker).trace.getLevel();
        } catch (Exception ignored) { }

        // remove negative cycles from network
        int newPathCount = pathCount;
        if (graphAcyclic) {
            NetworkPathOptimizer pathOptimizer = new HeuristicNetworkPathOptimizer(this.network, Math.min(8, depth - 1));
            pathOptimizer.optimizePaths();
            newPathCount = this.calculatePathCount();
        }
        if (newPathCount < pathCount) {
            MP.printMessage(EC.GENERAL, "  Removed " + MP.format(pathCount - newPathCount)
                    + " redundant paths (" + MP.format(newPathCount) + " paths, " + now() + ").");
            pathCount = newPathCount;
        } else {
            MP.printMessage(EC.GENERAL, "  No redundant paths were found (" + now() + ").");
        }

        // transform flow to circulation
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge fwd = network.getEdge(i);
            if (fwd.hasAction()) {
                network.incFlow(i, 1);
            }
        }

        int totalLength = this.calculatePathCoverTotalLength();
        int averageLength = (int) Math.round(1.0 * totalLength / pathCount);
        MP.printMessage(EC.GENERAL, "  Total path cover length is " + MP.format(this.calculatePathCoverTotalLength())
                + " (average path length is " + averageLength + ").");

        // find paths
        MP.printMessage(EC.GENERAL, "Preparing to export path cover...");
        return graphAcyclic 
                ? new AcyclicFlowPathExtractor(network, pathCount)
                : new EulerFlowPathExtractor(network);
    }

    public static class Edge {
        private final int id;
        private final int from;
        private final int to;

        public Edge(int id, int from, int to) {
            this.id = id;
            this.from = from;
            this.to = to;
        }

        public int getId() {
            return id;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }
    }
}
