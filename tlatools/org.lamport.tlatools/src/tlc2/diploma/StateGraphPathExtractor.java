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
        int a, b, f, c;
        Action action;

        public NetworkEdge(int a, int b, int f, int c, Action action) {
            this(a, b, f, c);
            this.action = action;
        }

        public NetworkEdge(int a, int b, int f, int c) {
            this.a = a;
            this.b = b;
            this.f = f;
            this.c = c;
        }
    }

    private static final int INF = 1000000000;

    private final List<List<Integer>> g = new ArrayList<>();
    private final List<NetworkEdge> e = new ArrayList<>();
    private final Queue<Integer> q = new ArrayDeque<>();
    private final List<Integer> d = new ArrayList<>();
    private final List<Integer> p = new ArrayList<>();
    private final List<Integer> pt = new ArrayList<>();

    private final Map<Long, Integer> fpToId = new HashMap<>();
    private final List<TLCState> states = new ArrayList<>();

    public StateGraphPathExtractor() {
        addNode(null); // source
    }

    private void addNode(TLCState state) {
        states.add(state);
        g.add(new ArrayList<>());
        d.add(INF);
        p.add(-1);
        pt.add(0);
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
        g.get(from).add(e.size());
        e.add(fwd);

        NetworkEdge bck = new NetworkEdge(to, from, cap, cap);
        g.get(to).add(e.size());
        e.add(bck);
    }

    public void addTransition(TLCState from, TLCState to, Action action) {
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
        for (int i = 0; i < e.size(); i += 2) {
            NetworkEdge edge = e.get(i);
            degInOutDiffs.set(edge.a, degInOutDiffs.get(edge.a) - 1);
            degInOutDiffs.set(edge.b, degInOutDiffs.get(edge.b) + 1);
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
        Collections.fill(d, INF);
        d.set(getSource(), 0);

        q.add(getSource());
        while (!q.isEmpty() && d.get(getSink()) == INF) {
            int cur = q.poll();
            List<Integer> gCur = g.get(cur);
            for (int eId : gCur) {
                NetworkEdge edge = e.get(eId);
                int to = edge.b;
                if (d.get(to) == INF && edge.c - edge.f > 0) {
                    d.set(to, d.get(cur) + 1);
                    q.add(to);
                }
            }
        }
        q.clear();

        return d.get(getSink()) != INF;
    }

    private boolean dinicDfs(int v) {
        if (v == getSink()) {
            return true;
        }
        for (; pt.get(v) < g.get(v).size(); pt.set(v, pt.get(v) + 1)) {
            int eId = g.get(v).get(pt.get(v));
            NetworkEdge fwd = e.get(eId);
            NetworkEdge bck = e.get(eId ^ 1);
            int to = fwd.b;

            if (d.get(to) == d.get(v) + 1 && fwd.c - fwd.f > 0) {
                boolean pushed = dinicDfs(to);
                if (pushed) {
                    fwd.f += 1;
                    bck.f -= 1;
                    return true;
                }
            }
        }
        return false;
    }

    private void findMaxFlow() {
        while (dinicBfs()) {
            Collections.fill(pt, 0);
            while (true) {
                if (!dinicDfs(getSource())) {
                    break;
                }
            }
        }
    }

    private int computePathCount() {
        int paths = 0;
        for (int i = 0; i < e.size(); i += 2) {
            NetworkEdge fwd = e.get(i);
            if (fwd.b == getRoot()) {
                paths += fwd.f;
            }
        }
        return paths;
    }

    public List<TLCState> getStates() {
        return new ArrayList<>(states.subList(1, fpToId.size() + 1));
    }

    public boolean tryRemoveNegativeCycleAcyclic() {
        Collections.fill(d, INF);
        Collections.fill(p, -1);

        for (int eId : g.get(getRoot())) {
            if (eId % 2 == 0) {
                continue;
            }
            NetworkEdge edge = e.get(eId);
            int to = edge.b;
            if (edge.c - edge.f > 0) {
                d.set(to, 0);
                p.set(to, eId);
                q.add(to);
            }
        }

        while (!q.isEmpty() && d.get(getRoot()) == INF) {
            int cur = q.poll();
            for (int eId : g.get(cur)) {
                NetworkEdge edge = e.get(eId);
                int to = edge.b;
                if (to == getRoot() && eId % 2 == 0) {
                    continue;
                }
                if (d.get(to) == INF && edge.c - edge.f > 0) {
                    d.set(to, d.get(cur) + 1);
                    p.set(to, eId);
                    q.add(to);
                }
            }
        }
        q.clear();

        if (d.get(getRoot()) != INF) {
            int cur = getRoot();
            do {
                int eId = p.get(cur);
                NetworkEdge fwd = e.get(eId);
                NetworkEdge bck = e.get(eId ^ 1);
                fwd.f += 1;
                bck.f -= 1;
                cur = fwd.a;
            } while (cur != getRoot());
            return true;
        }
        return false;
    }

    private boolean checkAcyclicDfs(int v, List<Integer> color) {
        if (color.get(v) == 1) {
            return false;
        }

        color.set(v, 1);
        for (int eId : g.get(v)) {
            if (eId % 2 != 0) {
                continue;
            }
            NetworkEdge edge = e.get(eId);
            int to = edge.b;
            if ((to == getRoot() && edge.action == null) || to == getSink() || to == v) {
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

        NetworkEdge firstEdge = e.get(g.get(root).get(0));
        edgeStack.addLast(firstEdge);
        while (!edgeStack.isEmpty()) {
            NetworkEdge edge = edgeStack.peekLast();
            int v = edge.b;
            for (; pt.get(v) < g.get(v).size(); pt.set(v, pt.get(v) + 1)) {
                int eId = g.get(v).get(pt.get(v));
                if (eId % 2 == 1) {
                    continue;
                }

                NetworkEdge fwd = e.get(eId);
                NetworkEdge bck = e.get(eId ^ 1);
                int to = fwd.b;
                if (to == getSink()) {
                    continue;
                }

                if (fwd.f > 0) {
                    fwd.f -= 1;
                    bck.f += 1;
                    edgeStack.addLast(fwd);
                    break;
                }
            }
            if (pt.get(v) == g.get(v).size()) {
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
        for (int i = 0; i < e.size(); i += 2) {
            NetworkEdge fwd = e.get(i);
            NetworkEdge bck = e.get(i ^ 1);
            if (fwd.action != null) {
                fwd.f += 1;
                bck.f -= 1;
            }
        }

        // find paths
        Collections.fill(pt, 0);
        List<NetworkEdge> eulerCycle = this.findEulerCycle(getRoot());

        List<List<Edge>> result = new ArrayList<>();
        List<Edge> path = new ArrayList<>();
        for (NetworkEdge edge : eulerCycle) {
            if (edge.b == getRoot()) {
                result.add(path);
                path = new ArrayList<>();
            } else {
                path.add(new Edge(edge.a - 1, edge.b - 1, edge.action));
            }
        }

        MP.printMessage(EC.GENERAL, "  Successfully found path cover of model state graph (" + now() + ").");

        g.clear();
        e.clear();
        d.clear();
        p.clear();
        pt.clear();
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
