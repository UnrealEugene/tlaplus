package tlc2.diploma.graph.algo;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableBooleanList;
import org.eclipse.collections.impl.list.mutable.primitive.BooleanArrayList;
import tlc2.diploma.graph.StateNetwork;

public class NaiveMaxFlowSolver implements MaxFlowSolver {
    private final StateNetwork network;

    private final MutableBooleanList used;

    public NaiveMaxFlowSolver(StateNetwork network) {
        this.network = network;
        this.used = BooleanArrayList.newWithNValues(network.getNodeCount(), false);
    }

    private int naiveDfs(int eId) {
        StateNetwork.Edge edge = network.getEdge(eId);
        int u = edge.getTo();
        IntList adjListU = network.getAdjacentEdgeIds(u);

        boolean deadEnd = true;
        int sum = 0;
        if (!used.get(u)) {
            used.set(u, true);
            for (int i = 0; i < adjListU.size(); i++) {
                int j = adjListU.get(i);
                StateNetwork.Edge e = network.getEdge(j);
                if (!e.isForward() || !e.hasAction()) {
                    continue;
                }
                deadEnd = false;
                sum += naiveDfs(j);
            }
        }
        if (deadEnd) {
            for (int i = 0; i < adjListU.size(); i++) {
                int j = adjListU.get(i);
                StateNetwork.Edge rootEdge = network.getEdge(j);
                if (rootEdge.isForward() && rootEdge.getTo() == network.getRoot()) {
                    network.incFlow(j, 1);
                    sum += 1;
                }
            }
        }
        network.incFlow(eId, sum);
        return sum;
    }

    @Override
    public void findMaxFlow() {
        used.set(network.getRoot(), true);
        IntList adjListRoot = network.getAdjacentEdgeIds(network.getRoot());
        for (int i = 0; i < adjListRoot.size(); i++) {
            int eId = adjListRoot.get(i);
            StateNetwork.Edge edge = network.getEdge(eId);
            if (edge.isForward() && edge.hasAction()) {
                naiveDfs(eId);
            }
        }

        for (int eId = 0; eId < network.getEdgeCount(); eId += 2) {
            StateNetwork.Edge edge = network.getEdge(eId);
            if (edge.hasAction()) {
                network.incFlow(eId, -1);
            }
            if (edge.getFrom() == network.getSource() || edge.getTo() == network.getSink()) {
                int cap = edge.getCapacity();
                network.incFlow(eId, cap);
            }
        }
    }
}
