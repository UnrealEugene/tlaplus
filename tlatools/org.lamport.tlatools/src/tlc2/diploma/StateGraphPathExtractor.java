package tlc2.diploma;

import tlc2.tool.Action;
import tlc2.tool.TLCState;

import java.util.*;

public class StateGraphPathExtractor {
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
    private final List<Integer> d = new ArrayList<>();
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

    private boolean bfs() {
        Collections.fill(d, INF);
        d.set(getSource(), 0);

        Queue<Integer> q = new ArrayDeque<>();
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

        return d.get(getSink()) != INF;
    }

    private boolean dfs(int v) {
        if (v == getSink()) {
            return true;
        }
        for (; pt.get(v) < g.get(v).size(); pt.set(v, pt.get(v) + 1)) {
            int eId = g.get(v).get(pt.get(v));
            NetworkEdge fwd = e.get(eId);
            NetworkEdge bck = e.get(eId ^ 1);
            int to = fwd.b;

            if (d.get(to) == d.get(v) + 1 && fwd.c - fwd.f > 0) {
                boolean pushed = dfs(to);
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
        int flow = 0;
        while (bfs()) {
            Collections.fill(pt, 0);
            while (true) {
                if (!dfs(getSource())) {
                    break;
                }
            }
        }

        int paths = 0;
        for (int i = 0; i < e.size(); i += 2) {
            NetworkEdge fwd = e.get(i);
            if (fwd.b == getRoot()) {
                paths += fwd.f;
            }
        }
        System.out.println("paths = " + paths);
    }

    private boolean findCycle(int v, List<Edge> result) {
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
                if (to == getRoot()) {
                    return true;
                }
                result.add(new Edge(v - 1, to - 1, fwd.action));
                return findCycle(to, result);
            }
        }
        return false;
    }

    public List<TLCState> getStates() {
        return new ArrayList<>(states.subList(1, states.size() - 1));
    }

    public List<List<Edge>> extractPaths() {
        this.constructNetwork();
        this.findMaxFlow();

        // transform flow to circulation
        for (int i = 0; i < e.size(); i += 2) {
            NetworkEdge fwd = e.get(i);
            NetworkEdge bck = e.get(i ^ 1);
            if (fwd.action != null) {
                fwd.f += 1;
                bck.f -= 1;
            }
        }

        List<List<Edge>> result = new ArrayList<>();
        Collections.fill(pt, 0);
//        int i = 1;
        while (true) {
            List<Edge> path = new ArrayList<>();
            if (!findCycle(getRoot(), path)) {
                break;
            }
            result.add(path);
        }

        g.clear();
        e.clear();
        d.clear();
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
