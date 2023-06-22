package tlc2.diploma.graph;

import tlc2.tool.Action;
import tlc2.tool.TLCState;

import java.util.*;
import java.util.stream.Collectors;

public class StateNetwork {
    public static final int INF = Integer.MAX_VALUE;

    private final List<TLCState> states = new ArrayList<>();
    private final List<List<Integer>> adjList;
    private final List<Edge> edges;
    private final Map<Long, Integer> fpToId = new HashMap<>();

    private int source;
    private int sink;
    private int root;

    public StateNetwork() {
        this.adjList = new ArrayList<>();
        this.edges = new ArrayList<>();
        this.source = 0;
        this.sink = -1;
        this.root = 1;
    }

    private static int mod(int a, int b) {
        int res = a % b;
        if (a < 0) {
            res += b;
        }
        return res;
    }

    public int getSource() {
        return mod(source, states.size());
    }

    public void setSource(int source) {
        this.source = source;
    }

    public int getSink() {
        return mod(sink, states.size());
    }

    public void setSink(int sink) {
        this.sink = sink;
    }

    public int getRoot() {
        return root;
    }

    public void setRoot(int root) {
        this.root = root;
    }

    private int addNode(TLCState state) {
        states.add(state);
        adjList.add(new ArrayList<>());
        return states.size() - 1;
    }

    public int tryAddNode(TLCState state) {
        if (state == null) {
            return addNode(null);
        }
        long fp = state.fingerPrint();
        if (!fpToId.containsKey(fp)) {
            addNode(state);
            fpToId.put(fp, states.size() - 1);
        }
        return fpToId.get(fp);
    }

    public List<TLCState> getStates() {
        return states.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public int getNodeCount() {
        return states.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public synchronized void addEdge(int from, int to, int cap, Action action) {
        // e[i] - direct edge
        Edge fwd = new Edge(from, to, 0, cap, action);
        adjList.get(from).add(edges.size());
        edges.add(fwd);

        // e[i ^ 1] - back edge
        Edge bck = new Edge(to, from, cap, cap);
        adjList.get(to).add(edges.size());
        edges.add(bck);

        fwd.twin = bck;
        bck.twin = fwd;
    }

    public Edge getEdge(int i) {
        return edges.get(i);
    }

    public List<Integer> getAdjacentEdgeIds(int i) {
        return Collections.unmodifiableList(adjList.get(i));
    }

    public static class Edge {
        private final int from;
        private final int to;
        private int flow;
        private final int capacity;
        private Edge twin;
        private final Action action;

        public Edge(int from, int to, int flow, int capacity, Action action) {
            this.from = from;
            this.to = to;
            this.flow = flow;
            this.capacity = capacity;
            this.action = action;
        }

        public Edge(int from, int to, int flow, int capacity) {
            this(from, to, flow, capacity, null);
        }

        public int getFrom() {
            return from;
        }

        public int getTo() {
            return to;
        }

        public int getFlow() {
            return flow;
        }

        public void incFlow(int flow) {
            this.flow += flow;
        }

        public int getCapacity() {
            return capacity;
        }

        public Edge getTwin() {
            return twin;
        }

        public Action getAction() {
            return action;
        }
    }
}
