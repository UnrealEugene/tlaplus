package tlc2.tool.profile;

import com.google.gson.stream.JsonWriter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import tla2sany.semantic.OpApplNode;
import tla2sany.semantic.SemanticNode;
import tla2sany.st.Location;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public aspect TLCProfilerAspect {
//    private final Map<String, Long> methodSpentTime = Collections.synchronizedMap(new HashMap<>());
//
//    private final Map<String, Long> methodCallCount = Collections.synchronizedMap(new HashMap<>());

//    private final Map<Thread, Node> currentParents = Collections.synchronizedMap(new HashMap<>());
    private final List<NodeInfo> nodeInfos = Collections.synchronizedList(new ArrayList<>());
    private final List<Set<Integer>> adjList = Collections.synchronizedList(new ArrayList<>());
    private final Map<Integer, Integer> hashToId = Collections.synchronizedMap(new HashMap<>());

    private final Map<Thread, Integer> currentHashes = Collections.synchronizedMap(new HashMap<>());

    public TLCProfilerAspect() {
        addNode(0, new NodeInfo("root"));
    }

    private synchronized void addNode(int hash, NodeInfo nodeInfo) {
        int id = nodeInfos.size();
        nodeInfos.add(nodeInfo);
        adjList.add(new HashSet<>());
        hashToId.put(hash, id);
    }

    private Signature getMethodSignature(JoinPoint joinPoint) {
        return joinPoint.getStaticPart().getSignature();
    }

    private synchronized int tryAddNode(int hash, Location location, String string, int parentId) {
        if (!hashToId.containsKey(hash)) {
            addNode(hash, new NodeInfo(location, string));
        }
        int id = hashToId.get(hash);
        adjList.get(parentId).add(id);
        return id;
    }

    Object around() : execution(@tlc2.tool.profile.Profile * *(..)) {
        Thread thread = Thread.currentThread();
        if (!currentHashes.containsKey(thread)) {
            currentHashes.put(thread, 0);
        }
        int currentNodeHash = currentHashes.get(thread);
        int currentNodeId = hashToId.get(currentNodeHash);

        SemanticNode node = (SemanticNode) Arrays.stream(thisJoinPoint.getArgs())
                .filter(obj -> obj instanceof SemanticNode)
                .findFirst()
                .get();
        Location location = node.getLocation();
        String string = node instanceof OpApplNode
                ? ((OpApplNode) node).getOperator().getName().toString()
                : node.toString();
        Signature methodSignature = getMethodSignature(thisJoinPoint);
        int newNodeHash = Objects.hash(currentNodeHash, location, methodSignature);
        int newNodeId = tryAddNode(newNodeHash, location, string, currentNodeId);
        NodeInfo newNodeInfo = nodeInfos.get(newNodeId);

        currentHashes.put(thread, newNodeHash);

        long timeNs = System.nanoTime();
        Object result = proceed();
        long timeElapsedNs = System.nanoTime() - timeNs;

        currentHashes.put(thread, currentNodeHash);
        newNodeInfo.increaseTimeElapsed(timeElapsedNs);
        newNodeInfo.increaseExecuteCount(1);

        return result;
    }

    private void printMethodCallTree(int u, JsonWriter writer)  {
        try {
            NodeInfo nodeInfo = nodeInfos.get(u);

            writer.beginObject();

            List<Integer> children = new ArrayList<>(adjList.get(u));
            children.sort(Comparator.<Integer, Long>comparing(i -> nodeInfos.get(i).getTimeElapsed()).reversed());
            if (nodeInfo.getLocation() == null) {
                writer.name("name").value(nodeInfo.getString());
                writer.name("value").value(children.stream()
                        .map(nodeInfos::get)
                        .mapToLong(NodeInfo::getTimeElapsed)
                        .sum());
            } else {
                Location loc = nodeInfo.getLocation();
                writer.name("name").value(String.format("%s (%s:%d:%d-%d:%d)",
                        nodeInfo.getString(),
                        loc.source(), loc.beginLine(), loc.beginColumn(), loc.endLine(), loc.endColumn()));
                writer.name("value").value(nodeInfo.getTimeElapsed());
            }

            List<Integer> bigChildren = children.stream()
                    .filter(v -> nodeInfos.get(v).getTimeElapsed() > 1 << 25)
                    .collect(Collectors.toList());
            if (bigChildren.size() > 0) {
                writer.name("children").beginArray();
                bigChildren.forEach(v -> printMethodCallTree(v, writer));
                writer.endArray();
            }
            writer.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    before() : /*execution(public static void main(..)) || */ call(* java.lang.System.exit(*)) {
        try (JsonWriter writer = new JsonWriter(Files.newBufferedWriter(Path.of("data.json")))) {
            printMethodCallTree(0, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

//        if (!methodCallCount.isEmpty()) {
//            System.out.println("Method profile summary: ");
//            List<String> methodOrder = methodCallCount.entrySet().stream()
//                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
//                    .map(Map.Entry::getKey)
//                    .collect(Collectors.toList());
//            for (String methodName : methodOrder) {
//                long callCount = methodCallCount.get(methodName);
//                long timeElapsed = methodSpentTime.get(methodName);
//                System.out.printf("* %s: %d call(s), sum %d ns, avg %d ns%n",
//                        methodName, callCount, timeElapsed, timeElapsed / callCount);
//            }
//        }
    }

    private static class NodeInfo {
        private final Location location;
        private final String string;
        private long timeElapsed;
        private long executeCount;

        private NodeInfo(String string) {
            this(null, string);
        }

        private NodeInfo(Location location, String string) {
            this.location = location;
            this.string = string;
        }

        public Location getLocation() {
            return location;
        }

        public String getString() {
            return string;
        }

        public long getTimeElapsed() {
            return timeElapsed;
        }

        public synchronized void increaseTimeElapsed(long timeElapsed) {
            this.timeElapsed += timeElapsed;
        }

        public long getExecuteCount() {
            return executeCount;
        }

        public synchronized void increaseExecuteCount(long executeCount) {
            this.executeCount += executeCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NodeInfo nodeInfo = (NodeInfo) o;
            return Objects.equals(location, nodeInfo.location);
        }

        @Override
        public int hashCode() {
            return Objects.hash(location);
        }
    }
}