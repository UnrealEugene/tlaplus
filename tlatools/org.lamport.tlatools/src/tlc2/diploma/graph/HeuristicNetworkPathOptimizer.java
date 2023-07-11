package tlc2.diploma.graph;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Queue;

import static tlc2.diploma.graph.StateNetwork.INF;

public class HeuristicNetworkPathOptimizer implements NetworkPathOptimizer {
    private final StateNetwork network;
    private final MutableIntList adjListPt;
    private final MutableIntList color;
    private final MutableIntList distance;
    private final int iterations;

    public HeuristicNetworkPathOptimizer(StateNetwork network, int iterations) {
        this.network = network;
        this.adjListPt = IntArrayList.newWithNValues(network.getNodeCount(), 0);
        this.color = IntArrayList.newWithNValues(network.getNodeCount(), INF);
        this.distance = IntArrayList.newWithNValues(network.getNodeCount(), INF);
        this.iterations = iterations;
    }

    private int simpleCycleDfs(int u, int flow) {
        if (flow == 0) {
            return 0;
        }
        IntList adjListU = network.getAdjacentEdgeIds(u);
        for (; adjListPt.get(u) < adjListU.size(); adjListPt.set(u, adjListPt.get(u) + 1)) {
            int eId = adjListU.get(adjListPt.get(u));
            StateNetwork.Edge fwd = network.getEdge(eId);
            StateNetwork.Edge bck = network.getEdge(eId ^ 1);
            if (fwd.getFlow() == 0) {
                continue;
            }
            int to = fwd.getTo(), w = distance.get(u) < distance.get(to) ? 0 : 1;
            if (eId % 2 == 0 && to == network.getRoot() && !fwd.hasAction()) {
                int df = Math.min(flow, fwd.getFlow());
                network.incFlow(eId, -df);
                network.incFlow(eId ^ 1, df);
                return df;
            }
            if (!fwd.hasAction() && !bck.hasAction()) {
                continue;
            }
            if (color.get(u) + w == color.get(to)) {
                int df = simpleCycleDfs(to, Math.min(flow, fwd.getFlow()));
                if (df > 0) {
                    network.incFlow(eId, -df);
                    network.incFlow(eId ^ 1, df);
                    return df;
                }
            }
        }
        return 0;
    }

    private void distanceBfs() {
        distance.collectInt(x -> INF, distance);

        // TODO: change to eclipse-collections Queue
        Queue<Integer> queue = new ArrayDeque<>();
        queue.add(network.getRoot());
        distance.set(network.getRoot(), 0);

        while (!queue.isEmpty()) {
            int u = queue.poll();
            int dist = distance.get(u);
            IntList adjListU = network.getAdjacentEdgeIds(u);
            for (int i = 0; i < adjListU.size(); i++) {
                int eId = adjListU.get(i);
                StateNetwork.Edge fwd = network.getEdge(eId);
                StateNetwork.Edge bck = network.getEdge(eId ^ 1);
                if (!fwd.hasAction() && !bck.hasAction()) {
                    continue;
                }
                int to = fwd.getTo();
                if (dist + 1 < distance.get(to)) {
                    distance.set(to, dist + 1);
                    queue.add(to);
                }
            }
        }
    }

    private void colorBfs() {
        color.collectInt(x -> INF, color);

        Deque<Integer> deque = new ArrayDeque<>();
        deque.add(network.getRoot());
        color.set(network.getRoot(), 0);

        while (!deque.isEmpty()) {
            int u = deque.pollFirst();
            int dist = color.get(u);
            IntList adjListU = network.getAdjacentEdgeIds(u);
            for (int i = 0; i < adjListU.size(); i++) {
                int eId = adjListU.get(i);
                StateNetwork.Edge fwd = network.getEdge(eId);
                StateNetwork.Edge bck = network.getEdge(eId ^ 1);
                if (fwd.getFlow() == 0 || (!fwd.hasAction() && !bck.hasAction())) {
                    continue;
                }
                int to = fwd.getTo(), w = distance.get(u) < distance.get(to) ? 0 : 1;
                if (dist + w < color.get(to) && dist + w < StateNetwork.INF) {
                    color.set(to, dist + w);
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
        distanceBfs();
        for (int i = 1; i <= iterations; i++) {
            colorBfs();

            adjListPt.collectInt(x -> 0, adjListPt);
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
