package tlc2.diploma.graph.algo;

import org.eclipse.collections.api.list.primitive.IntList;
import tlc2.diploma.graph.StateNetwork;
import util.ToolIO;

import java.util.Arrays;

import static tlc2.diploma.graph.StateNetwork.INF;

public class DinicMaxFlowSolver implements MaxFlowSolver {
    private final StateNetwork network;

    private final int[] distance;
    private final int[] queue;
    private int queueLeft;
    private int queueRight;
    private final int[] adjListPt;

    public DinicMaxFlowSolver(StateNetwork network) {
        this.network = network;
        this.distance = new int[network.getNodeCount()];
        this.queue = new int[network.getNodeCount()];
        this.queueLeft = 0;
        this.queueRight = 0;
        this.adjListPt = new int[network.getNodeCount()];
    }

    private boolean dinicBfs() {
        int source = network.getSource(), sink = network.getSink();

        Arrays.fill(distance, INF);
        distance[source] = 0;

        queue[queueRight++] = source;
        while (queueRight > queueLeft && distance[sink] == INF) {
            int cur = queue[queueLeft++];
            int dist = distance[cur];
            IntList adjListCur = network.getAdjacentEdgeIds(cur);
            for (int i = 0; i < adjListCur.size(); i++) {
                int eId = adjListCur.get(i);
                StateNetwork.Edge edge = network.getEdge(eId);
                int to = edge.getTo();
                if (distance[to] == INF && edge.getCapacity() - edge.getFlow() > 0) {
                    distance[to] = dist + 1;
                    queue[queueRight++] = to;
                }
            }
        }
        queueLeft = 0;
        queueRight = 0;

        ToolIO.out.print(distance[sink] + " ");

        return distance[sink] < INF;
    }

    private int dinicDfs(int v, int flow) {
        if (v == network.getSink()) {
            return flow;
        }
        int dist = distance[v];
        IntList adjListV = network.getAdjacentEdgeIds(v);
        for (; adjListPt[v] < adjListV.size(); adjListPt[v]++) {
            int eId = adjListV.get(adjListPt[v]);
            StateNetwork.Edge fwd = network.getEdge(eId);
            int to = fwd.getTo();

            int cap = fwd.getCapacity() - fwd.getFlow();
            if (distance[to] == dist + 1 && cap > 0) {
                int df = dinicDfs(to, Math.min(flow, cap));
                if (df > 0) {
                    network.incFlow(eId, df);
                    return df;
                }
            }
        }
        return 0;
    }

    @Override
    public void findMaxFlow() {
        ToolIO.out.print("    DinicMaxFlowSolver started\n    ");
        while (dinicBfs()) {
            Arrays.fill(adjListPt, 0);
            while (true) {
                if (dinicDfs(network.getSource(), INF) == 0) {
                    break;
                }
            }
        }
        ToolIO.out.print("\n    DinicMaxFlowSolver finished\n");
    }
}
