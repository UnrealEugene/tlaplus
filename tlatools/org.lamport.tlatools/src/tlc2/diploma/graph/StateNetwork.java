package tlc2.diploma.graph;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableBooleanList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.BooleanArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;
import tlc2.tool.TLCState;

import java.util.AbstractList;
import java.util.List;

public class StateNetwork {
    public static final int INF = Integer.MAX_VALUE;
    private final List<MutableIntList> adjList;
    private final EdgeArrayList edges;
    private final MutableLongIntMap fpToId = new LongIntHashMap();
    private boolean shutDown = false;

    private int source;
    private int sink;
    private int root;

    public StateNetwork() {
        this.adjList = new FastList<>();
        this.edges = new EdgeArrayList();
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
        return mod(source, adjList.size());
    }

    public void setSource(int source) {
        ensureNotShutDown();
        this.source = source;
    }

    public int getSink() {
        return mod(sink, adjList.size());
    }

    public void setSink(int sink) {
        ensureNotShutDown();
        this.sink = sink;
    }

    public int getRoot() {
        return root;
    }

    public void setRoot(int root) {
        ensureNotShutDown();
        this.root = root;
    }

    public synchronized int addNode(TLCState state) {
        ensureNotShutDown();
        int id = adjList.size();
        adjList.add(new IntArrayList(4));
        if (state != null) {
            fpToId.put(state.fingerPrint(), id);
            this.notifyAll();
        }
        return id;
    }

    private void ensureNotShutDown() {
        if (shutDown) {
            throw new IllegalStateException("network is already shut down");
        }
    }

    public void shutdown() {
        ensureNotShutDown();
        System.gc();
        shutDown = true;
        fpToId.clear();
    }

    public int getNodeCount() {
        return adjList.size();
    }

    public int getEdgeCount() {
        return edges.size();
    }

    public synchronized int addEdge(TLCState fromState, TLCState toState, int cap) {
        while (true) {
            try {
                int from = fpToId.getOrThrow(fromState.fingerPrint());
                int to = fpToId.getOrThrow(toState.fingerPrint());
                return addEdge(from, to, cap, true);
            } catch (IllegalStateException e) { // needed for case when toState is not in fpToId yet
                try {
                    this.wait();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    public int addEdge(int from, int to, int cap) {
        return addEdge(from, to, cap, false);
    }

    private int addEdge(int from, int to, int cap, boolean hasAction) {
        // e[i] - direct edge
        Edge fwd = new Edge(from, to, 0, cap, hasAction);
        // e[i ^ 1] - back edge
        Edge bck = new Edge(to, from, cap, cap);

        int id;
        synchronized (this) {
            id = edges.size();
            adjList.get(from).add(id);
            edges.add(fwd);
            adjList.get(to).add(id + 1);
            edges.add(bck);
        }

        return id;
    }

    public Edge getEdge(int i) {
        return edges.get(i);
    }

    public IntList getAdjacentEdgeIds(int i) {
        return adjList.get(i).asUnmodifiable();
    }

    public void incFlow(int index, int flow) {
        edges.flowList.set(index, edges.flowList.get(index) + flow);
    }

    public static class EdgeArrayList extends AbstractList<Edge> {
        private final MutableIntList fromList;
        private final MutableIntList toList;
        private final MutableIntList flowList;
        private final MutableIntList capacityList;
        private final MutableBooleanList hasActionList;

        public EdgeArrayList() {
            this.fromList = new IntArrayList();
            this.toList = new IntArrayList();
            this.flowList = new IntArrayList();
            this.capacityList = new IntArrayList();
            this.hasActionList = new BooleanArrayList();
        }

        @Override
        public boolean add(Edge edge) {
            return fromList.add(edge.getFrom())
                    & toList.add(edge.getTo())
                    & flowList.add(edge.getFlow())
                    & capacityList.add(edge.getCapacity())
                    & hasActionList.add(edge.hasAction());
        }

        @Override
        public Edge get(int i) {
            return new Edge(
                    fromList.get(i),
                    toList.get(i),
                    flowList.get(i),
                    capacityList.get(i),
                    hasActionList.get(i)
            );
        }

        @Override
        public int size() {
            return hasActionList.size();
        }
    }

    public static class Edge {
        private final int from;
        private final int to;
        private final int flow;
        private final int capacity;
        private final boolean hasAction;

        public Edge(int from, int to, int flow, int capacity, boolean hasAction) {
            this.from = from;
            this.to = to;
            this.flow = flow;
            this.capacity = capacity;
            this.hasAction = hasAction;
        }

        public Edge(int from, int to, int flow, int capacity) {
            this(from, to, flow, capacity, false);
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

        public int getCapacity() {
            return capacity;
        }

        public boolean hasAction() {
            return hasAction;
        }
    }
}
