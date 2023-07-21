package tlc2.diploma.graph.algo;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import tlc2.diploma.graph.StateGraphPathExtractor.Edge;
import tlc2.diploma.graph.StateNetwork;

import java.util.Iterator;
import java.util.List;

public class AcyclicFlowPathExtractor implements FlowPathExtractor {
    private final StateNetwork network;
    private final int pathCount;
    private final MutableIntList adjListPt;
    private int i = 0;

    public AcyclicFlowPathExtractor(StateNetwork network, int pathCount) {
        this.network = network;
        this.pathCount = pathCount;
        this.adjListPt = IntArrayList.newWithNValues(network.getNodeCount(), 0);
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
                if (to != network.getRoot()) {
                    extractPathAcyclicDfs(to, path);
                    path.add(new Edge(eId / 2, fwd.getFrom() - 1, fwd.getTo() - 1));
                }
                break;
            }
        }
    }

    @Override
    public Iterator<List<Edge>> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return i < pathCount;
            }

            @Override
            public List<Edge> next() {
                i++;
                MutableList<Edge> path = new FastList<>();
                extractPathAcyclicDfs(network.getRoot(), path);
                return path.reverseThis();
            }
        };
    }
}
