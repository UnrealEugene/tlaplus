package tlc2.diploma;

import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.TLCState;

import java.text.SimpleDateFormat;
import java.util.*;

public class StateGraphPathExtractor {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static class NetworkEdge {
        int from, to, flow, capacity;
        Action action;

        public NetworkEdge(int from, int to, int flow, int capacity, Action action) {
            this(from, to, flow, capacity);
            this.action = action;
        }

        public NetworkEdge(int from, int to, int flow, int capacity) {
            this.from = from;
            this.to = to;
            this.flow = flow;
            this.capacity = capacity;
        }
    }

    private static final int INF = 1000000000;

    private final List<List<Integer>> adjList = new ArrayList<>();
    private final List<NetworkEdge> edges = new ArrayList<>();
    private final Queue<Integer> queue = new ArrayDeque<>();
    private final List<Integer> distance = new ArrayList<>();
    private final List<Integer> parent = new ArrayList<>();
    private final List<Integer> adjListPt = new ArrayList<>();

    private final Map<Long, Integer> fpToId = new HashMap<>();
    private final List<TLCState> states = new ArrayList<>();

    public StateGraphPathExtractor() {
        addNode(null); // source
    }

    private void addNode(TLCState state) {
        states.add(state);
        adjList.add(new ArrayList<>());
        distance.add(INF);
        parent.add(-1);
        adjListPt.add(0);
    }

    private int tryAddNode(TLCState state) {
        long fp = state.fingerPrint();
        if (!fpToId.containsKey(fp)) {
            addNode(state);
            fpToId.put(fp, states.size() - 1);
        }
        return fpToId.get(fp);
    }

    private void addEdge(int from, int to, int cap) {
        addEdge(from, to, cap, null);
    }

    private void addEdge(int from, int to, int cap, Action action) {
        // e[i] - direct edge, e[i ^ 1] - back edge

        NetworkEdge fwd = new NetworkEdge(from, to, 0, cap, action);
        adjList.get(from).add(edges.size());
        edges.add(fwd);

        NetworkEdge bck = new NetworkEdge(to, from, cap, cap);
        adjList.get(to).add(edges.size());
        edges.add(bck);
    }

    public synchronized void addTransition(TLCState from, TLCState to, Action action) {
        int fromId = tryAddNode(from);
        int toId = tryAddNode(to);
        addEdge(fromId, toId, INF / 2, action);
    }

    private int getSource() {
        return 0;
    }

    private int getSink() {
        return states.size() - 1;
    }

    private int getRoot() {
        return 1;
    }

    private void constructNetwork() {
        addNode(null); // sink

        List<Integer> degInOutDiffs = new ArrayList<>(Collections.nCopies(states.size(), 0));
        for (int i = 0; i < edges.size(); i += 2) {
            NetworkEdge edge = edges.get(i);
            degInOutDiffs.set(edge.from, degInOutDiffs.get(edge.from) - 1);
            degInOutDiffs.set(edge.to, degInOutDiffs.get(edge.to) + 1);
        }

        for (int i = 0; i < states.size(); i++) {
            if (i == getSource() || i == getSink()) {
                continue;
            }
            int degInOutDiff = degInOutDiffs.get(i);
            if (degInOutDiff > 0) {
                addEdge(getSource(), i, degInOutDiff);
            } else if (degInOutDiff < 0) {
                addEdge(i, getSink(), -degInOutDiff);
            }

            if (i == getRoot()) {
                continue;
            }
            addEdge(i, getRoot(), INF / 2);
        }
    }

    private boolean dinicBfs() {
        Collections.fill(distance, INF);
        distance.set(getSource(), 0);

        queue.add(getSource());
        while (!queue.isEmpty() && distance.get(getSink()) == INF) {
            int cur = queue.poll();
            List<Integer> gCur = adjList.get(cur);
            for (int eId : gCur) {
                NetworkEdge edge = edges.get(eId);
                int to = edge.to;
                if (distance.get(to) == INF && edge.capacity - edge.flow > 0) {
                    distance.set(to, distance.get(cur) + 1);
                    queue.add(to);
                }
            }
        }
        queue.clear();

        return distance.get(getSink()) != INF;
    }

    private boolean dinicDfs(int v) {
        if (v == getSink()) {
            return true;
        }
        for (; adjListPt.get(v) < adjList.get(v).size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
            int eId = adjList.get(v).get(adjListPt.get(v));
            NetworkEdge fwd = edges.get(eId);
            NetworkEdge bck = edges.get(eId ^ 1);
            int to = fwd.to;

            if (distance.get(to) == distance.get(v) + 1 && fwd.capacity - fwd.flow > 0) {
                boolean pushed = dinicDfs(to);
                if (pushed) {
                    fwd.flow += 1;
                    bck.flow -= 1;
                    return true;
                }
            }
        }
        return false;
    }

    private void findMaxFlow() {
        while (dinicBfs()) {
            Collections.fill(adjListPt, 0);
            while (true) {
                if (!dinicDfs(getSource())) {
                    break;
                }
            }
        }
    }

    private int computePathCount() {
        int paths = 0;
        for (int i = 0; i < edges.size(); i += 2) {
            NetworkEdge fwd = edges.get(i);
            if (fwd.to == getRoot()) {
                paths += fwd.flow;
            }
        }
        return paths;
    }

    public List<TLCState> getStates() {
        return new ArrayList<>(states.subList(1, fpToId.size() + 1));
    }

    public boolean tryRemoveNegativeCycleAcyclic() {
        Collections.fill(distance, INF);
        Collections.fill(parent, -1);

        for (int eId : adjList.get(getRoot())) {
            if (eId % 2 == 0) {
                continue;
            }
            NetworkEdge edge = edges.get(eId);
            int to = edge.to;
            if (edge.capacity - edge.flow > 0) {
                distance.set(to, 0);
                parent.set(to, eId);
                queue.add(to);
            }
        }

        while (!queue.isEmpty() && distance.get(getRoot()) == INF) {
            int cur = queue.poll();
            for (int eId : adjList.get(cur)) {
                NetworkEdge edge = edges.get(eId);
                int to = edge.to;
                if (to == getRoot() && eId % 2 == 0) {
                    continue;
                }
                if (distance.get(to) == INF && edge.capacity - edge.flow > 0) {
                    distance.set(to, distance.get(cur) + 1);
                    parent.set(to, eId);
                    queue.add(to);
                }
            }
        }
        queue.clear();

        if (distance.get(getRoot()) != INF) {
            int cur = getRoot();
            do {
                int eId = parent.get(cur);
                NetworkEdge fwd = edges.get(eId);
                NetworkEdge bck = edges.get(eId ^ 1);
                fwd.flow += 1;
                bck.flow -= 1;
                cur = fwd.from;
            } while (cur != getRoot());
            return true;
        }
        return false;
    }

    private boolean checkAcyclicDfs(int v, List<Integer> color) {
        color.set(v, 1);
        for (int eId : adjList.get(v)) {
            if (eId % 2 != 0) {
                continue;
            }
            NetworkEdge edge = edges.get(eId);
            int to = edge.to;
            if ((to == getRoot() && edge.action == null) || to == getSink() || to == v) {
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
        return checkAcyclicDfs(getRoot(), new ArrayList<>(Collections.nCopies(states.size(), 0)));
    }

    private List<NetworkEdge> findEulerCycle(int root) {
        List<NetworkEdge> edges = new ArrayList<>();
        Deque<NetworkEdge> edgeStack = new ArrayDeque<>();

        NetworkEdge firstEdge = this.edges.get(adjList.get(root).get(0));
        edgeStack.addLast(firstEdge);
        while (!edgeStack.isEmpty()) {
            NetworkEdge edge = edgeStack.peekLast();
            int v = edge.to;
            for (; adjListPt.get(v) < adjList.get(v).size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
                int eId = adjList.get(v).get(adjListPt.get(v));
                if (eId % 2 == 1) {
                    continue;
                }

                NetworkEdge fwd = this.edges.get(eId);
                NetworkEdge bck = this.edges.get(eId ^ 1);
                int to = fwd.to;
                if (to == getSink()) {
                    continue;
                }

                if (fwd.flow > 0) {
                    fwd.flow -= 1;
                    bck.flow += 1;
                    edgeStack.addLast(fwd);
                    break;
                }
            }
            if (adjListPt.get(v) == adjList.get(v).size()) {
                edgeStack.pollLast();
                edges.add(edge);
            }
        }
        Collections.reverse(edges);
        return edges;
    }

    private String now() {
        return SDF.format(new Date());
    }

    public List<List<Edge>> extractPaths() {
        this.constructNetwork();

        boolean graphAcyclic = this.isGraphAcyclic();
        if (!graphAcyclic) {
            MP.printMessage(EC.GENERAL, "  Warning: model state graph contains cycles!");
        }

        this.findMaxFlow();
        int pathCount = this.computePathCount();
        MP.printMessage(EC.GENERAL, "  Found maximum flow in constructed network (" + pathCount + " paths, " + now() + ").");

        // remove negative cycles from network
        if (graphAcyclic) {
            int negativeCyclesCount = 0;
            while (this.tryRemoveNegativeCycleAcyclic()) {
                negativeCyclesCount++;
            }
            if (negativeCyclesCount > 0) {
                MP.printMessage(EC.GENERAL, "  Found " + negativeCyclesCount + " negative cycles in the flow ("
                        + (pathCount - negativeCyclesCount) + " paths, " + now() + ").");
            } else {
                MP.printMessage(EC.GENERAL, "  No negative cycles were found in resulting flow (" + now() + ").");
            }
        }

        // transform flow to circulation
        for (int i = 0; i < edges.size(); i += 2) {
            NetworkEdge fwd = edges.get(i);
            NetworkEdge bck = edges.get(i ^ 1);
            if (fwd.action != null) {
                fwd.flow += 1;
                bck.flow -= 1;
            }
        }

        // find paths
        Collections.fill(adjListPt, 0);
        List<NetworkEdge> eulerCycle = this.findEulerCycle(getRoot());

        List<List<Edge>> result = new ArrayList<>();
        List<Edge> path = new ArrayList<>();
        for (NetworkEdge edge : eulerCycle) {
            if (edge.to == getRoot()) {
                result.add(path);
                path = new ArrayList<>();
            } else {
                path.add(new Edge(edge.from - 1, edge.to - 1, edge.action));
            }
        }

        MP.printMessage(EC.GENERAL, "  Successfully found path cover of model state graph (" + now() + ").");

        adjList.clear();
        edges.clear();
        distance.clear();
        parent.clear();
        adjListPt.clear();
        fpToId.clear();

        return result;
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
