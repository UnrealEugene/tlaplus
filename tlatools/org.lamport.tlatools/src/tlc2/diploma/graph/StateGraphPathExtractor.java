package tlc2.diploma.graph;

import tlc2.TLCGlobals;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.ModelChecker;
import tlc2.tool.TLCState;

import java.text.SimpleDateFormat;
import java.util.*;

public class StateGraphPathExtractor {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
    private static final int INF = Integer.MAX_VALUE;

    private final StateNetwork network = new StateNetwork();
    private final List<Integer> adjListPt = new ArrayList<>();

    public StateGraphPathExtractor() {
        network.tryAddNode(null); // source
    }

    public synchronized void addTransition(TLCState from, TLCState to, Action action) {
        int fromId = network.tryAddNode(from);
        int toId = network.tryAddNode(to);
        network.addEdge(fromId, toId, INF, action);
    }

    private int getRoot() {
        return 1;
    }

    private void constructNetwork() {
        network.tryAddNode(null); // sink

        List<Integer> degInOutDiffs = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
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
                network.addEdge(network.getSource(), i, degInOutDiff, null);
            } else if (degInOutDiff < 0) {
                network.addEdge(i, network.getSink(), -degInOutDiff, null);
            }

            if (i == getRoot()) {
                continue;
            }
            network.addEdge(i, getRoot(), INF / 2, null);
        }
    }

    public int getPathCount() {
        int count = 0;
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge fwd = network.getEdge(i);
            if (fwd.getTo() == getRoot()) {
                count += fwd.getFlow();
            }
        }
        return count;
    }

    public List<TLCState> getStates() {
        return network.getStates();
    }

    private boolean checkAcyclicDfs(int v, List<Integer> color) {
        color.set(v, 1);
        for (int eId : network.getAdjacentEdgeIds(v)) {
            if (eId % 2 != 0) {
                continue;
            }
            StateNetwork.Edge edge = network.getEdge(eId);
            int to = edge.getTo();
            if ((to == getRoot() && edge.getAction() == null) || to == network.getSink() || to == v) {
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
        return checkAcyclicDfs(getRoot(), new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0)));
    }

    private List<StateNetwork.Edge> findEulerCycle(int root) {
        throw new UnsupportedOperationException();
//        List<NetworkEdge> edges = new ArrayList<>();
//        Deque<NetworkEdge> edgeStack = new ArrayDeque<>();
//
//        NetworkEdge firstEdge = this.edges.get(adjList.get(root).get(0));
//        edgeStack.addLast(firstEdge);
//        while (!edgeStack.isEmpty()) {
//            NetworkEdge edge = edgeStack.peekLast();
//            int v = edge.to;
//            for (; adjListPt.get(v) < adjList.get(v).size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
//                int eId = adjList.get(v).get(adjListPt.get(v));
//                if (eId % 2 == 1) {
//                    continue;
//                }
//
//                NetworkEdge fwd = this.edges.get(eId);
//                NetworkEdge bck = this.edges.get(eId ^ 1);
//                int to = fwd.to;
//                if (to == getSink()) {
//                    continue;
//                }
//
//                if (fwd.flow > 0) {
//                    fwd.flow -= 1;
//                    bck.flow += 1;
//                    edgeStack.addLast(fwd);
//                    break;
//                }
//            }
//            if (adjListPt.get(v) == adjList.get(v).size()) {
//                edgeStack.pollLast();
//                edges.add(edge);
//            }
//        }
//        Collections.reverse(edges);
//        return edges;
    }

    private void extractPathAcyclicDfs(int v, List<Edge> path) {
        List<Integer> adjListV = network.getAdjacentEdgeIds(v);
        for (; adjListPt.get(v) < adjListV.size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
            int eId = adjListV.get(adjListPt.get(v));
            if (eId % 2 == 1) {
                continue;
            }

            StateNetwork.Edge fwd = network.getEdge(eId);
            StateNetwork.Edge bck = fwd.getTwin();
            int to = fwd.getTo();
            if (to == network.getSink()) {
                continue;
            }

            if (fwd.getFlow() > 0) {
                fwd.incFlow(-1);
                bck.incFlow(1);
                if (to != getRoot()) {
                    extractPathAcyclicDfs(to, path);
                    path.add(new Edge(fwd.getFrom() - 1, fwd.getTo() - 1, fwd.getAction()));
                }
                break;
            }
        }
    }

    private List<Edge> extractPathAcyclic(int root) {
        List<Edge> path = new ArrayList<>();
        extractPathAcyclicDfs(root, path);
        Collections.reverse(path);
        return path;
    }

    private String now() {
        return SDF.format(new Date());
    }

    public Iterable<List<Edge>> extractPaths() {
        MP.printMessage(EC.GENERAL, "Path cover construction started ("
                + MP.format(network.getNodeCount() - 1) + " states, "
                + MP.format(network.getEdgeCount() / 2) + " transitions, " + now() + ")");

        this.constructNetwork();

        boolean graphAcyclic = this.isGraphAcyclic();
        if (!graphAcyclic) {
            MP.printMessage(EC.GENERAL, "  Warning: model state graph contains cycles!");
        }

        MaxFlowSolver maxFlowSolver = new NaiveMaxFlowSolver(this.network);
        maxFlowSolver.findMaxFlow();

        int pathCount = this.getPathCount();
        MP.printMessage(EC.GENERAL, "  Constructed initial path cover ("
                + MP.format(pathCount) + " paths, " + now() + ").");

        int depth = 8;
        try {
            depth = ((ModelChecker) TLCGlobals.mainChecker).trace.getLevel();
        } catch (Exception ignored) { }

        // remove negative cycles from network
        if (graphAcyclic) {
            new HeuristicNetworkPathOptimizer(this.network, Math.min(4, depth - 1)).optimizePaths();

            int newPathCount = this.getPathCount();
            if (newPathCount < pathCount) {
                MP.printMessage(EC.GENERAL, "  Removed " + MP.format(pathCount - newPathCount)
                        + " redundant paths (" + MP.format(newPathCount) + " paths, " + now() + ").");
                pathCount = newPathCount;
            } else {
                MP.printMessage(EC.GENERAL, "  No negative cycles were found in resulting flow (" + now() + ").");
            }
        }

        // transform flow to circulation
        for (int i = 0; i < network.getEdgeCount(); i += 2) {
            StateNetwork.Edge fwd = network.getEdge(i);
            StateNetwork.Edge bck = fwd.getTwin();
            if (fwd.getAction() != null) {
                fwd.incFlow(1);
                bck.incFlow(-1);
            }
        }

        // find paths
        adjListPt.addAll(Collections.nCopies(network.getNodeCount(), 0));
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
        }

        List<StateNetwork.Edge> eulerCycle = this.findEulerCycle(getRoot());
        List<List<Edge>> result = new ArrayList<>();
        List<Edge> path = new ArrayList<>();
        for (StateNetwork.Edge edge : eulerCycle) {
            if (edge.getTo() == getRoot()) {
                result.add(path);
                path = new ArrayList<>();
            } else {
                path.add(new Edge(edge.getFrom() - 1, edge.getTo() - 1, edge.getAction()));
            }
        }
        return result;
    }

    public void cleanup() {
        adjListPt.clear();
    }

    public static class Edge {
        private final int from;
        private final int to;
        private final Action action;

        public Edge(int from, int to, Action action) {
            this.from = from;
            this.to = to;
            this.action = action;
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        public Action getAction() {
            return action;
        }
    }
}
