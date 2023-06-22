package tlc2.diploma.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NaiveMaxFlowSolver implements MaxFlowSolver {
    private final StateNetwork network;

    private final List<Boolean> used;

    public NaiveMaxFlowSolver(StateNetwork network) {
        this.network = network;
        this.used = new ArrayList<>(Collections.nCopies(network.getNodeCount(), false));
    }

    private int naiveDfs(int eId) {
        StateNetwork.Edge edge = network.getEdge(eId);
        int u = edge.getTo();

        boolean deadEnd = true;
        int sum = 0;
        if (!used.get(u)) {
            used.set(u, true);
            for (int i : network.getAdjacentEdgeIds(u)) {
                StateNetwork.Edge e = network.getEdge(i);
                if (i % 2 != 0 || e.getAction() == null) {
                    continue;
                }
                deadEnd = false;
                sum += naiveDfs(i);
            }
        }
        if (deadEnd) {
            for (int i : network.getAdjacentEdgeIds(u)) {
                StateNetwork.Edge rootEdge = network.getEdge(i);
                if (i % 2 == 0 && rootEdge.getTo() == network.getRoot()) {
                    rootEdge.incFlow(1);
                    rootEdge.getTwin().incFlow(-1);
                    sum += 1;
                }
            }
        }
        edge.incFlow(sum);
        edge.getTwin().incFlow(-sum);
        return sum;
    }

    @Override
    public void findMaxFlow() {
        used.set(network.getRoot(), true);
        for (int eId : network.getAdjacentEdgeIds(network.getRoot())) {
            if (eId % 2 == 0 && network.getEdge(eId).getAction() != null) {
                naiveDfs(eId);
            }
        }

        for (int eId = 0; eId < network.getEdgeCount(); eId += 2) {
            StateNetwork.Edge edge = network.getEdge(eId);
            if (edge.getAction() != null) {
                edge.incFlow(-1);
                edge.getTwin().incFlow(1);
            }
            if (edge.getFrom() == network.getSource() || edge.getTo() == network.getSink()) {
                int cap = edge.getCapacity();
                edge.incFlow(cap);
                edge.getTwin().incFlow(-cap);
            }
        }
    }
}
