package tlc2.diploma.graph;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.stack.primitive.MutableIntStack;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.stack.mutable.primitive.IntArrayStack;
import tlc2.TLCGlobals;
import tlc2.diploma.graph.algo.*;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.ModelChecker;
import tlc2.tool.TLCState;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class StateGraphPathExtractor {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final int INF = Integer.MAX_VALUE;

    private final StateNetwork network = new StateNetwork();
    private final MutableIntList adjListPt = new IntArrayList();

    private int stateCount = 0;

    private int actionCount = 0;
    private int pathCount = 0;
    public StateGraphPathExtractor() {
        network.addNode(null); // source
    }

    public int addState(TLCState state) {
        return network.addNode(state) - 1;
    }

    public int addAction(TLCState from, TLCState to) {
        return network.addEdge(from, to, INF) / 2;
    }

    private int getRoot() {
        return 1;
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

            if (i == getRoot()) {
                continue;
            }
            network.addEdge(i, getRoot(), INF / 2);
        }
    }

    private int calculatePathCount() {
        pathCount = 0;
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge fwd = network.getEdge(i);
            if (fwd.getFrom() != network.getSource() && fwd.getTo() == getRoot()) {
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
            if ((to == getRoot() && !edge.hasAction()) || to == network.getSink() || to == v) {
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
        return checkAcyclicDfs(getRoot(), IntArrayList.newWithNValues(network.getNodeCount(), 0));
    }

    private IntList findEulerCycle(int root) {
        MutableIntList edges = new IntArrayList(this.network.getEdgeCount());
        MutableIntStack edgeStack = new IntArrayStack();

        int firstEdge = this.network.getAdjacentEdgeIds(root).get(0);
        this.network.incFlow(firstEdge, -1);
        edgeStack.push(firstEdge);

        while (!edgeStack.isEmpty()) {
            StateNetwork.Edge edge = this.network.getEdge(edgeStack.peek());
            int v = edge.getTo();

            IntList adjListV = this.network.getAdjacentEdgeIds(v);
            for (; adjListPt.get(v) < adjListV.size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
                int eId = adjListV.get(adjListPt.get(v));
                StateNetwork.Edge fwd = this.network.getEdge(eId);
                if (!fwd.isForward()) {
                    continue;
                }

                int to = fwd.getTo();
                if (to == this.network.getSink()) {
                    continue;
                }

                if (fwd.getFlow() > 0) {
                    this.network.incFlow(eId, -1);
                    edgeStack.push(eId);
                    break;
                }
            }
            if (adjListPt.get(v) == adjListV.size()) {
                edges.add(edgeStack.pop());
            }
        }
        edges.reverseThis();
        return edges;
    }

    private void extractPathAcyclicDfs(int v, MutableList<Edge> path) {
        IntList adjListV = network.getAdjacentEdgeIds(v);
        for (; adjListPt.get(v) < adjListV.size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
            int eId = adjListV.get(adjListPt.get(v));
            StateNetwork.Edge fwd = network.getEdge(eId);
            if (!fwd.isForward()) {
                continue;
            }

            int to = fwd.getTo();
            if (to == network.getSink()) {
                continue;
            }

            if (fwd.getFlow() > 0) {
                network.incFlow(eId, -1);
                if (to != getRoot()) {
                    extractPathAcyclicDfs(to, path);
                    path.add(new Edge(eId / 2, fwd.getFrom() - 1, fwd.getTo() - 1));
                }
                break;
            }
        }
    }

    private List<Edge> extractPathAcyclic(int root) {
        MutableList<Edge> path = new FastList<>();
        extractPathAcyclicDfs(root, path);
        return path.reverseThis();
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
        adjListPt.addAll(IntArrayList.newWithNValues(network.getNodeCount(), 0));
        if (graphAcyclic) {
            int finalPathCount = pathCount;

            return new Iterable<>() {
                private int i = 0;

                @Override
                public Iterator<List<Edge>> iterator() {
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return i < finalPathCount;
                        }

                        @Override
                        public List<Edge> next() {
                            i++;
                            return extractPathAcyclic(getRoot());
                        }
                    };
                }
            };
        } else {
            MP.printMessage(EC.GENERAL, "Preparing to export path cover...");
            IntList eulerCycle = this.findEulerCycle(getRoot());
            return new Iterable<>() {
                private int i = 0;

                @Override
                public Iterator<List<Edge>> iterator() {
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return i < eulerCycle.size();
                        }

                        @Override
                        public List<Edge> next() {
                            List<Edge> path = new FastList<>();
                            while (true) {
                                int eId = eulerCycle.get(i++);
                                StateNetwork.Edge edge = network.getEdge(eId);
                                if (edge.hasAction()) {
                                    path.add(new Edge(eId / 2, edge.getFrom() - 1, edge.getTo() - 1));
                                }
                                if (edge.getTo() == getRoot()) {
                                    return path;
                                }
                            }
                        }
                    };
                }
            };
        }

    }

    public void cleanup() {
        adjListPt.clear();
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
