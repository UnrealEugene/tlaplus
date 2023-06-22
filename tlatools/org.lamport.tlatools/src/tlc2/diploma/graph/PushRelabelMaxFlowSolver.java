package tlc2.diploma.graph;

import java.util.*;

import static tlc2.diploma.graph.StateNetwork.INF;

public class PushRelabelMaxFlowSolver implements MaxFlowSolver {
    private final StateNetwork network;
    private final List<Integer> height;
    private final List<Integer> excess;
    private final List<Integer> adjListPt;
    private final Queue<Integer> excessQueue;


    public PushRelabelMaxFlowSolver(StateNetwork network) {
        this.network = network;

        this.height = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
        this.height.set(network.getSource(), network.getNodeCount());
        this.excess = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
        this.excess.set(network.getSource(), INF);
        this.adjListPt = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
        this.excessQueue = new ArrayDeque<>();

        for (int eId : network.getAdjacentEdgeIds(network.getSource())) {
            push(network.getEdge(eId));
        }
    }

    private void push(StateNetwork.Edge e) {
        int u = e.getFrom(), v = e.getTo();
        int d = Math.min(excess.get(u), e.getCapacity() - e.getFlow());
        e.incFlow(d);
        e.getTwin().incFlow(-d);
        excess.set(u, excess.get(u) - d);
        excess.set(v, excess.get(v) + d);
        if (d > 0 && excess.get(v) == d) {
            excessQueue.add(v);
        }
    }

    private void relabel(int u) {
        int d = INF;
        for (int eId : network.getAdjacentEdgeIds(u)) {
            StateNetwork.Edge e = network.getEdge(eId);
            if (e.getCapacity() - e.getFlow() > 0) {
                d = Math.min(d, height.get(e.getTo()));
            }
        }
        if (d < INF) {
            height.set(u, d + 1);
        }
    }

    private void discharge(int u) {
        while (excess.get(u) > 0) {
            int pt = adjListPt.get(u);
            List<Integer> adjListU = network.getAdjacentEdgeIds(u);
            if (pt < adjListU.size()) {
                StateNetwork.Edge e = network.getEdge(adjListU.get(pt));
                if (e.getCapacity() - e.getFlow() > 0 && height.get(e.getFrom()) > height.get(e.getTo())) {
                    push(e);
                } else {
                    adjListPt.set(u, pt + 1);
                }
            } else {
                relabel(u);
                adjListPt.set(u, 0);
            }
        }
    }

    @Override
    public void findMaxFlow() {
        while (!excessQueue.isEmpty()) {
            int u = excessQueue.poll();
            if (u != network.getSource() && u != network.getSink()) {
                discharge(u);
            }
        }
    }
}
