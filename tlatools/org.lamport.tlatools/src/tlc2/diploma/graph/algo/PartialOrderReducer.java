package tlc2.diploma.graph.algo;

import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableBooleanList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.BooleanArrayList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import tlc2.diploma.graph.StateNetwork;
import tlc2.output.EC;
import tlc2.output.MP;

public class PartialOrderReducer {
    private final StateNetwork network;
    private final MutableBooleanList visitedTable;
    private final MutableIntList visitedList;
    private int reducedCount;

    public PartialOrderReducer(StateNetwork network) {
        this.network = network;
        this.visitedTable = BooleanArrayList.newWithNValues(network.getNodeCount(), false);
        this.visitedList = new IntArrayList();
        this.reducedCount = 0;
    }

    private void dfs(int u, int p, int eId, int depth) {
        if (depth == 0) {
            if (visitedTable.get(u)) {
                network.markAsRedundant(eId);
                reducedCount++;
            } else {
                visitedTable.set(u, true);
                visitedList.add(u);
            }
            return;
        }
        IntList adjListU = network.getAdjacentEdgeIds(u);
        for (int i = 0; i < adjListU.size(); i++) {
            int j = adjListU.get(i);
            StateNetwork.Edge e = network.getEdge(j);
            if (!e.isForward() || !e.hasAction()) {
                continue;
            }
            int v = e.getTo();
            if (v == p || v == u) {
                continue;
            }
            dfs(v, p, j, depth - 1);
        }
    }

    private void dfs(int u) {
        dfs(u, u, -1, 2);
    }

    private boolean hasTwoIncomingEdges(int v) {
        return false;
//        IntList adjListV = network.getAdjacentEdgeIds(v);
//        int count = 0;
//        for (int i = 0; i < adjListV.size() && count < 2; i++) {
//            StateNetwork.Edge bck = network.getEdge(adjListV.get(i) ^ 1);
//            if (bck.isForward() && bck.hasAction()) {
//                count++;
//            }
//        }
//        return count >= 2;
    }

    private void removeRhombusesFrom(int u) {
        IntList adjListU = network.getAdjacentEdgeIds(u);
        for (int i = 0; i < adjListU.size() - 1; i++) {
            StateNetwork.Edge ei = network.getEdge(adjListU.get(i));
            if (!ei.isForward() || !ei.hasAction()) {
                continue;
            }
            int v = ei.getTo();
            if (v == u || hasTwoIncomingEdges(v)) {
                continue;
            }
            for (int j = i + 1; j < adjListU.size(); j++) {
                StateNetwork.Edge ej = network.getEdge(adjListU.get(j));
                if (!ej.isForward() || !ej.hasAction()) {
                    continue;
                }
                int w = ej.getTo();
                if (w == u || w == v || hasTwoIncomingEdges(w)) {
                    continue;
                }
                IntList adjListV = network.getAdjacentEdgeIds(v);
                for (int k = 0; k < adjListV.size(); k++) {
                    StateNetwork.Edge ek = network.getEdge(adjListV.get(k));
                    if (!ek.isForward() || !ek.hasAction() || ek.getActionHash() != ej.getActionHash()) {
                        continue;
                    }
                    int z = ek.getTo();
                    if (z == u || z == w) {
                        continue;
                    }
                    IntList adjListW = network.getAdjacentEdgeIds(w);
                    for (int l = 0; l < adjListW.size(); l++) {
                        StateNetwork.Edge el = network.getEdge(adjListW.get(l));
                        if (!el.isForward() || !el.hasAction() || el.getTo() != z || el.getActionHash() != ei.getActionHash()) {
                            continue;
                        }
                        MP.printMessage(EC.GENERAL, "found rhombus!");
                        network.markAsRedundant(adjListW.get(l));
                        reducedCount++;
                    }
                }
            }
        }
    }

    public int reduce() {
        for (int u = network.getNodeCount() - 1; u >= 0; u--) {
            if (u == network.getSource() || u == network.getSink()) {
                continue;
            }
            removeRhombusesFrom(u);
            for (int i = 0; i < visitedList.size(); i++) {
                visitedTable.set(visitedList.get(i), false);
            }
            visitedList.clear();
        }
        return reducedCount;
    }
}
