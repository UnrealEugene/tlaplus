package tlc2.diploma.graph;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.BooleanArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
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

    public int addNode(TLCState state) {
        ensureNotShutDown();
        int id;
        synchronized (adjList) {
            id = adjList.size();
            adjList.add(new IntArrayList(4));
        }
        if (state != null) {
            long stateFp = state.fingerPrint();
            synchronized (fpToId) {
                fpToId.put(stateFp, id);
                fpToId.notifyAll();
            }
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

    public int addEdge(TLCState fromState, TLCState toState, ConcreteAction action) {
        long fromFp = fromState.fingerPrint(), toFp = toState.fingerPrint();
        int from, to;
        try {
            synchronized (fpToId) {
                while (true) {
                    from = fpToId.getIfAbsent(fromFp, -1);
                    if (from != -1) {
                        break;
                    }
                    fpToId.wait();
                }
                while (true) {
                    to = fpToId.getIfAbsent(toFp, -1);
                    if (to != -1) {
                        break;
                    }
                    fpToId.wait();
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return addEdge(from, to, INF, true, action.fingerPrint());
    }

    public int addEdge(int from, int to, int cap) {
        return addEdge(from, to, cap, false, 0L);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private int addEdge(int from, int to, int cap, boolean hasAction, long actionHash) {
        MutableIntList adjListFrom, adjListTo;
        synchronized (adjList) {
            adjListFrom = adjList.get(from);
            adjListTo = adjList.get(to);
        }

        int id;
        synchronized (edges) {
            id = edges.size();
            edges.add(from, to, cap, hasAction, actionHash);
        }
        synchronized (adjListFrom) {
            adjListFrom.add(id);
        }
        synchronized (adjListTo) {
            adjListTo.add(id + 1);
        }
        return id;
    }

    public Edge getEdge(int i) {
        return edges.get(i);
    }

    public void ensureEdgeCapacity(int capacity) {
        edges.ensureCapacity(capacity);
    }

    public IntList getAdjacentEdgeIds(int i) {
        return adjList.get(i).asUnmodifiable();
    }

    public void incFlow(int index, int flow) {
        int newIndex = index / 2;
        int df = index % 2 == 0 ? flow : -flow;
        edges.flowList.set(newIndex, edges.flowList.get(newIndex) + df);
    }

    public void markAsRedundant(int index) {
        int newIndex = index / 2;
        assert edges.flowList.get(newIndex) == 0;
        edges.capacityList.set(newIndex, 0);
        edges.capacityList.set(newIndex + 1, 0);
        edges.hasActionList.set(newIndex, false); // TODO: make sure other code doesnt confuse redundant edges with root edges
    }

    public interface Edge {
        int getFrom();

        int getTo();

        int getFlow();

        int getCapacity();

        boolean hasAction();

        long getActionHash();

        boolean isForward();

        Edge getTwin();
    }

    public static class EdgeArrayList extends AbstractList<Edge> {
        private final IntArrayList fromList;
        private final IntArrayList toList;
        private final IntArrayList flowList;
        private final IntArrayList capacityList;

        private final BooleanArrayList hasActionList;
        private final LongArrayList actionHashList;

        public EdgeArrayList() {
            this.fromList = new IntArrayList();
            this.toList = new IntArrayList();
            this.flowList = new IntArrayList();
            this.capacityList = new IntArrayList();
            this.hasActionList = new BooleanArrayList();
            this.actionHashList = new LongArrayList();
        }

        public boolean add(int from, int to, int capacity, boolean hasAction, long actionHash) {
            hasActionList.add(hasAction);
            return fromList.add(from)
                    & toList.add(to)
                    & flowList.add(0)
                    & capacityList.add(capacity)
                    & actionHashList.add(actionHash);
        }

        @Override
        public Edge get(int i) {
            return i % 2 == 0 ? new ForwardEdgeView(i / 2) : new BackwardEdgeView(i / 2);
        }

        @Override
        public int size() {
            return 2 * hasActionList.size();
        }

        public void ensureCapacity(int capacity) {
            int newCapacity = (capacity + 1) / 2;
            fromList.ensureCapacity(newCapacity);
            toList.ensureCapacity(newCapacity);
            flowList.ensureCapacity(newCapacity);
            capacityList.ensureCapacity(newCapacity);
//            hasActionList.ensureCapacity(newCapacity);
            actionHashList.ensureCapacity(newCapacity);
        }

        private class ForwardEdgeView implements Edge {
            private final int id;

            private ForwardEdgeView(int id) {
                this.id = id;
            }

            @Override
            public int getFrom() {
                return EdgeArrayList.this.fromList.get(id);
            }

            @Override
            public int getTo() {
                return EdgeArrayList.this.toList.get(id);
            }

            @Override
            public int getFlow() {
                return EdgeArrayList.this.flowList.get(id);
            }

            @Override
            public int getCapacity() {
                return EdgeArrayList.this.capacityList.get(id);
            }

            @Override
            public boolean hasAction() {
                return EdgeArrayList.this.hasActionList.get(id);
            }

            @Override
            public long getActionHash() {
                return EdgeArrayList.this.actionHashList.get(id);
            }

            @Override
            public boolean isForward() {
                return true;
            }

            @Override
            public Edge getTwin() {
                return new BackwardEdgeView(id);
            }
        }

        private class BackwardEdgeView implements Edge {
            private final int id;

            private BackwardEdgeView(int id) {
                this.id = id;
            }

            @Override
            public int getFrom() {
                return EdgeArrayList.this.toList.get(id);
            }

            @Override
            public int getTo() {
                return EdgeArrayList.this.fromList.get(id);
            }

            @Override
            public int getFlow() {
                return getCapacity() - EdgeArrayList.this.flowList.get(id);
            }

            @Override
            public int getCapacity() {
                return EdgeArrayList.this.capacityList.get(id);
            }

            @Override
            public boolean hasAction() {
                return false;
            }

            @Override
            public long getActionHash() {
                return 0;
            }

            @Override
            public boolean isForward() {
                return false;
            }

            @Override
            public Edge getTwin() {
                return new ForwardEdgeView(id);
            }
        }
    }
}
