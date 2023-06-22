package tlc2.diploma.graph;

import java.util.*;

import static tlc2.diploma.graph.StateNetwork.INF;

public class DinicMaxFlowSolver implements MaxFlowSolver {
    private final StateNetwork network;

    private final List<Integer> distance;
    private final Queue<Integer> queue;
    private final List<Integer> adjListPt;

    public DinicMaxFlowSolver(StateNetwork network) {
        this.network = network;

        this.distance = new ArrayList<>(Collections.nCopies(network.getNodeCount(), INF));
        this.queue = new ArrayDeque<>();
        this.adjListPt = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
    }

    @SuppressWarnings("DataFlowIssue")
    private boolean dinicBfs() {
        Collections.fill(distance, INF);
        distance.set(network.getSource(), 0);

        queue.add(network.getSource());
        while (!queue.isEmpty() && distance.get(network.getSink()) == INF) {
            int cur = queue.poll();
            for (int eId : network.getAdjacentEdgeIds(cur)) {
                StateNetwork.Edge edge = network.getEdge(eId);
                int to = edge.getTo();
                if (distance.get(to) == INF && edge.getCapacity() - edge.getFlow() > 0) {
                    distance.set(to, distance.get(cur) + 1);
                    queue.add(to);
                }
            }
        }
        queue.clear();

        return distance.get(network.getSink()) < INF;
    }

    private int dinicDfs(int v, int flow) {
        if (v == network.getSink()) {
            return flow;
        }
        List<Integer> adjListV = network.getAdjacentEdgeIds(v);
        for (; adjListPt.get(v) < adjListV.size(); adjListPt.set(v, adjListPt.get(v) + 1)) {
            int eId = adjListV.get(adjListPt.get(v));
            StateNetwork.Edge fwd = network.getEdge(eId);
            StateNetwork.Edge bck = fwd.getTwin();
            int to = fwd.getTo();

            int cap = fwd.getCapacity() - fwd.getFlow();
            if (distance.get(to) == distance.get(v) + 1 && cap > 0) {
                int df = dinicDfs(to, Math.min(flow, cap));
                if (df > 0) {
                    fwd.incFlow(df);
                    bck.incFlow(-df);
                    return df;
                }
            }
        }
        return 0;
    }

    @Override
    public void findMaxFlow() {
        while (dinicBfs()) {
            Collections.fill(adjListPt, 0);
            while (true) {
                if (dinicDfs(network.getSource(), INF) == 0) {
                    break;
                }
            }
        }
    }
}
