package tlc2.diploma.graph;

import java.util.*;

import static tlc2.diploma.graph.StateNetwork.INF;

public class HeuristicNetworkPathOptimizer implements NetworkPathOptimizer {
    private final StateNetwork network;
    private final List<Integer> adjListPt;
    private final List<Integer> distance;
    private final int depth;

    public HeuristicNetworkPathOptimizer(StateNetwork network, int depth) {
        this.network = network;
        this.adjListPt = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
        this.distance = new ArrayList<>(Collections.nCopies(network.getNodeCount(), INF));
        this.depth = depth;
    }

    private int simpleCycleDfs(int u, int flow) {
        if (flow == 0) {
            return 0;
        }
        List<Integer> adjListU = network.getAdjacentEdgeIds(u);
        for (; adjListPt.get(u) < adjListU.size(); adjListPt.set(u, adjListPt.get(u) + 1)) {
            int eId = adjListU.get(adjListPt.get(u));
            StateNetwork.Edge fwd = network.getEdge(eId);
            StateNetwork.Edge bck = fwd.getTwin();
            if (fwd.getFlow() == 0) {
                continue;
            }
            int to = fwd.getTo(), w = fwd.getAction() != null ? 0 : 1;
            if (eId % 2 == 0 && to == network.getRoot()) {
                int df = Math.min(flow, fwd.getFlow());
                fwd.incFlow(-df);
                bck.incFlow(df);
                return df;
            }
            if (fwd.getAction() == null && bck.getAction() == null) {
                continue;
            }
            if (distance.get(u) + w == distance.get(to)) {
                int df = simpleCycleDfs(to, Math.min(flow, fwd.getFlow()));
                if (df > 0) {
                    fwd.incFlow(-df);
                    bck.incFlow(df);
                    return df;
                }
            }
        }
        return 0;
    }

    private void distanceBfs() {
        Collections.fill(distance, INF);

        Deque<Integer> deque = new ArrayDeque<>();
        deque.add(network.getRoot());
        distance.set(network.getRoot(), 0);

        while (!deque.isEmpty()) {
            int u = deque.pollFirst();
            int dist = distance.get(u);
            for (int eId : network.getAdjacentEdgeIds(u)) {
                StateNetwork.Edge fwd = network.getEdge(eId);
                StateNetwork.Edge bck = fwd.getTwin();
                if (fwd.getFlow() == 0 || (fwd.getAction() == null && bck.getAction() == null)) {
                    continue;
                }
                int to = fwd.getTo(), w = fwd.getAction() != null ? 0 : 1;
                if (dist + w < distance.get(to) && dist + w < StateNetwork.INF) {
                    distance.set(to, dist + w);
                    if (w == 1) {
                        deque.addLast(to);
                    } else {
                        deque.addFirst(to);
                    }
                }
            }
        }
    }

    @Override
    public void optimizePaths() {
        for (int d = 1; d <= depth; d++) {
            distanceBfs();

            Collections.fill(adjListPt, 0);
            boolean progress = false;
            while (simpleCycleDfs(network.getRoot(), INF) != 0) {
                progress = true;
            }
            if (!progress) {
                break;
            }
        }
    }
}
