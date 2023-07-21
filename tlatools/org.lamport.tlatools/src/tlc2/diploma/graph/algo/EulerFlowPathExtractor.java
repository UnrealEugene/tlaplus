package tlc2.diploma.graph.algo;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import tlc2.diploma.graph.StateGraphPathExtractor.Edge;
import tlc2.diploma.graph.StateNetwork;
import tlc2.diploma.util.FileIntStack;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

public class EulerFlowPathExtractor implements FlowPathExtractor {
    private final StateNetwork network;
    private final FileIntStack edgeStack;
    private final MutableIntList adjListPt;

    public EulerFlowPathExtractor(StateNetwork network) {
        try {
            this.network = network;
            Path tempDir = Files.createTempDirectory("tlc-euler-");
            tempDir.toFile().deleteOnExit();
            this.edgeStack = new FileIntStack(tempDir);
            this.adjListPt = IntArrayList.newWithNValues(network.getNodeCount(), 0);

            int firstEdge = network.getAdjacentEdgeIds(network.getRoot()).get(0);
            network.incFlow(firstEdge, -1);
            edgeStack.push(firstEdge);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Iterator<List<Edge>> iterator() {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return !edgeStack.isEmpty();
            }

            @Override
            public List<Edge> next() {
                try {
                    MutableList<Edge> path = new FastList<>();

                    while (!edgeStack.isEmpty()) {
                        StateNetwork.Edge edge = network.getEdge(edgeStack.peek());
                        int v = edge.getTo();

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
                                edgeStack.push(eId);
                                break;
                            }
                        }
                        if (adjListPt.get(v) == adjListV.size()) {
                            int eId = edgeStack.pop();
                            if (edge.hasAction()) {
                                path.add(new Edge(eId / 2, edge.getFrom() - 1, edge.getTo() - 1));
                            }
                            if (edge.getFrom() == network.getRoot() && !path.isEmpty()) {
                                break;
                            }
                        }
                    }

                    return path.reverseThis();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
