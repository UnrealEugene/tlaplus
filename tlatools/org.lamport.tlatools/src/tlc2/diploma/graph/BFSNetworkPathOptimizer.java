package tlc2.diploma.graph;

import java.util.*;

import static tlc2.diploma.graph.StateNetwork.INF;

public class BFSNetworkPathOptimizer implements NetworkPathOptimizer {
    private final StateNetwork network;
    private final List<Integer> distance;
    private final List<Integer> parent;
    private final Queue<Integer> queue;
    private final List<Integer> count;
    private final Map<Integer, Integer> countMap;

    public BFSNetworkPathOptimizer(StateNetwork network) {
        this.network = network;
        this.distance = new ArrayList<>(Collections.nCopies(network.getNodeCount(), INF));
        this.parent = new ArrayList<>(Collections.nCopies(network.getNodeCount(), -1));
        this.queue = new ArrayDeque<>();
        this.count = new ArrayList<>(Collections.nCopies(network.getNodeCount(), 0));
        this.countMap = new HashMap<>();
    }

    @SuppressWarnings("DataFlowIssue")
    private boolean tryRemovePath() {
        Collections.fill(distance, INF);
        Collections.fill(parent, -1);
        for (int eId : network.getAdjacentEdgeIds(network.getRoot())) {
            if (eId % 2 == 0) {
                continue;
            }
            StateNetwork.Edge edge = network.getEdge(eId);
            int to = edge.getTo();
            if (edge.getCapacity() - edge.getFlow() > 0) {
                distance.set(to, 0);
                parent.set(to, eId);
                queue.add(to);
            }
        }


        while (!queue.isEmpty() && distance.get(network.getRoot()) == INF) {
            int cur = queue.poll();
            for (int eId : network.getAdjacentEdgeIds(cur)) {
                StateNetwork.Edge edge = network.getEdge(eId);
                int to = edge.getTo();
                if (to == network.getRoot() && eId % 2 == 0) {
                    continue;
                }
                if (distance.get(to) == INF && edge.getCapacity() - edge.getFlow() > 0) {
                    distance.set(to, distance.get(cur) + 1);
                    parent.set(to, eId);
                    queue.add(to);
                    count.set(to, count.get(cur) + (eId % 2 == 0 ? 1 : 0));
                }
            }
        }
        queue.clear();


        if (distance.get(network.getRoot()) != INF) {
            countMap.compute(count.get(network.getRoot()), (k, v) -> v == null ? 1 : v + 1);

            int cur = network.getRoot();
            do {
                int eId = parent.get(cur);
                StateNetwork.Edge fwd = network.getEdge(eId);
                StateNetwork.Edge bck = fwd.getTwin();
                fwd.incFlow(1);
                bck.incFlow(-1);
                cur = fwd.getFrom();
            } while (cur != network.getRoot());
            return true;
        }
        return false;
    }

    @Override
    public void optimizePaths() {
        while (true) {
            if (!tryRemovePath()) {
                break;
            }
        }
    }
}
