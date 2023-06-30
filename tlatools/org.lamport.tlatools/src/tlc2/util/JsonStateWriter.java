package tlc2.util;

import com.alibaba.fastjson2.JSONWriter;
import tla2sany.st.Location;
import tlc2.TLCGlobals;
import tlc2.diploma.TlaTypeToGoVisitor;
import tlc2.diploma.TlaVariableTypeExtractor;
import tlc2.diploma.graph.ConcreteAction;
import tlc2.diploma.graph.StateGraphPathExtractor;
import tlc2.diploma.model.TlaRecordType;
import tlc2.diploma.model.TlaType;
import tlc2.module.Json;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.impl.Tool;
import tlc2.value.IValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;
import util.FileUtil;
import util.UniqueString;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JsonStateWriter implements IStateWriter {
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");

    private final Path dir;
    private final boolean generateGo;
    private final StateGraphPathExtractor stateGraphPathExtractor;

    public JsonStateWriter(String dir, boolean generateGo) throws IOException {
        this.dir = Path.of(dir);
        this.generateGo = generateGo;
        this.stateGraphPathExtractor = new StateGraphPathExtractor();
    }

    private Object serializeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Value)) {
            value = new StringValue(value.toString());
        }
        try {
            return Json.getNode((Value) value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addEdge(TLCState from, TLCState to, Action action) {
        if (from.fingerPrint() != to.fingerPrint()) {
            this.stateGraphPathExtractor.addTransition(from, to, ConcreteAction.from(from, to, action));
        }
    }

    public void writeState(TLCState state) {
        // No operations
    }

    public void writeState(TLCState state, TLCState successor, boolean successorStateIsNew) {
        writeState(state, successor, successorStateIsNew, Visualization.DEFAULT);
    }

    public void writeState(TLCState state, TLCState successor, boolean successorStateIsNew, Action action) {
        writeState(state, successor, null, 0, 0, successorStateIsNew, Visualization.DEFAULT, action);
    }

    public void writeState(TLCState state, TLCState successor, boolean successorStateIsNew, Visualization visualization) {
        writeState(state, successor, null, 0, 0, successorStateIsNew, visualization, null);
    }

    public void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length, boolean successorStateIsNew) {
        writeState(state, successor, actionChecks, from, length, successorStateIsNew, Visualization.DEFAULT, null);
    }

    @Override
    public void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length, boolean successorStateIsNew, Visualization visualization) {
        writeState(state, successor, actionChecks, from, length, successorStateIsNew, visualization, null);
    }

    private void writeState(TLCState state, TLCState successor, BitVector ignoredActionChecks, int ignoredFrom, int ignoredLength,
                            boolean ignoredSuccessorStateIsNew, Visualization visualization, Action action) {
        if (visualization == Visualization.STUTTERING) {
            return;
        }
        addEdge(state, successor, action);
    }

    @Override
    public boolean isNoop() {
        return true;
    }

    @Override
    public boolean isDot() {
        return false;
    }

    private String now() {
        return SDF.format(new Date());
    }

    private String formatBytes(long bytes) {
        String[] units = new String[] { "bytes", "KiB", "MiB", "GiB", "TiB" };
        int unitIndex = (int) (Math.log10(bytes) / 3);
        double unitValue = 1L << (unitIndex * 10);
        return new DecimalFormat("#,##0.#")
                .format(bytes / unitValue) + " " + units[unitIndex];
    }

    @Override
    @SuppressWarnings("unchecked")
    public void close() {
        if (TLCGlobals.mainChecker.getStateQueueSize() != 0) {
            return;
        }

        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        Map<Location, UniqueString> actionNames = Arrays.stream(tool.getActions())
                .collect(Collectors.groupingBy(Action::getDeclaration,
                        Collectors.reducing(null, Action::getName, (x, y) -> x == null ? y : x)));
        List<Location> actionLocations = Arrays.stream(tool.getActions())
                .map(Action::getDeclaration)
                .distinct()
                .collect(Collectors.toList());
        Map<Location, Long> actionLocCount = Arrays.stream(tool.getActions())
                .collect(Collectors.groupingBy(Action::getDeclaration, Collectors.counting()));

        MP.printMessage(EC.GENERAL, "Found " + tool.getActions().length + " actions (" + actionLocations.size() + " distinct):");
        for (Location loc : actionLocations) {
            MP.printMessage(EC.GENERAL, "  " + actionNames.get(loc) + ": " + actionLocCount.get(loc));
        }
        Map<Location, Integer> locToId = IntStream.range(0, actionLocations.size())
                .boxed()
                .collect(Collectors.toMap(actionLocations::get, Function.identity()));

        List<TLCState> states = this.stateGraphPathExtractor.getStates();
        Iterable<List<StateGraphPathExtractor.Edge>> paths = this.stateGraphPathExtractor.extractPaths();

        Vect<Object> constVect = tool.getModelConfig().getConstants();
        Object[] constArray = new Vect[constVect.size()];
        constVect.copyInto(constArray);
        Map<String, Value> constMap = new HashMap<>();
        for (Object obj : constArray) {
            Vect<Object> vect = (Vect<Object>) obj;
            constMap.put((String) vect.elementAt(0), (Value) vect.elementAt(1));
        }

        MP.printMessage(EC.GENERAL, "Path cover JSON exporting started.");

        Path metaFile = this.dir.resolve("meta.json");
        try {
            Files.deleteIfExists(metaFile);
        } catch (IOException ignored) { }

        int threads = TLCGlobals.getNumWorkers();
//        int threads = 4;
        long exportedSize = 0;

        // write states
        Path stateDir = this.dir.resolve("states");
        try {
            Files.createDirectories(stateDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> stateFiles = new ArrayList<>();
        List<Integer> stateFileCounts = new ArrayList<>();
        int stateFilesCount = Math.min(states.size(), threads);
        for (int i = 0, l = 0; i < stateFilesCount; i++) {
            int div = states.size() / stateFilesCount, mod = states.size() % stateFilesCount;
            int r = l + div + (i < mod ? 1 : 0);

            String stateFileName = String.format("%03d.json", i + 1);
            Path stateFile = stateDir.resolve(stateFileName);

            stateFiles.add(this.dir.relativize(stateFile).toString());
            stateFileCounts.add(r - l);

            try (BufferedWriter writer = Files.newBufferedWriter(stateFile);
                 JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
                jsonWriter.startArray();
                for (int j = l; j < r; j++) {
                    if (j > l) {
                        jsonWriter.writeComma();
                    }
                    jsonWriter.startObject();
                    Map<UniqueString, IValue> stateVals = states.get(j).getVals();
                    for (Map.Entry<UniqueString, IValue> entry : stateVals.entrySet()) {
                        jsonWriter.writeName(entry.getKey().toString());
                        jsonWriter.writeColon();
                        jsonWriter.writeAny(serializeValue(entry.getValue()));
                    }
                    jsonWriter.endObject();
                    jsonWriter.flushTo(writer);
                }
                jsonWriter.endArray();
                jsonWriter.flushTo(writer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            exportedSize += stateFile.toFile().length();

            l = r;
        }

        int pathCount = this.stateGraphPathExtractor.getPathCount();

        // write executions
        Path execDir = this.dir.resolve("executions");
        try {
            Files.createDirectories(execDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        List<String> execFiles = new ArrayList<>();
        int execFilesCount = Math.min(pathCount, threads);
        Iterator<List<StateGraphPathExtractor.Edge>> pathIterator = paths.iterator();
        for (int i = 0, l = 0; i < execFilesCount; i++) {
            int div = pathCount / execFilesCount, mod = pathCount % execFilesCount;
            int r = l + div + (i < mod ? 1 : 0);

            String execFileName = String.format("%03d.json", i + 1);
            Path execFile = execDir.resolve(execFileName);
            execFiles.add(this.dir.relativize(execFile).toString());

            try (BufferedWriter writer = Files.newBufferedWriter(execFile);
                 JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
                jsonWriter.startArray();
                for (int j = l; j < r; j++) {
                    if (j > l) {
                        jsonWriter.writeComma();
                    }
                    List<StateGraphPathExtractor.Edge> path = pathIterator.next();
                    jsonWriter.startArray();
                    if (!path.isEmpty()) {
                        jsonWriter.writeInt32(path.get(0).getFrom());
                    }
                    for (StateGraphPathExtractor.Edge edge : path) {
                        jsonWriter.writeComma();

                        jsonWriter.startArray();

                        ConcreteAction action = edge.getAction();
                        int actionId = locToId.get(action.getDeclaration());
                        jsonWriter.writeInt32(actionId);
                        for (Object val : action.getArgs()) {
                            jsonWriter.writeComma();
                            jsonWriter.writeAny(serializeValue(val));
                        }

                        jsonWriter.endArray();
                        jsonWriter.writeComma();
                        jsonWriter.writeInt32(edge.getTo());
                    }
                    jsonWriter.endArray();
                    jsonWriter.flushTo(writer);
                }
                jsonWriter.endArray();
                jsonWriter.flushTo(writer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            exportedSize += execFile.toFile().length();

            l = r;
        }

        // write meta file
        try (BufferedWriter writer = Files.newBufferedWriter(metaFile);
             JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
            jsonWriter.startObject();

            jsonWriter.writeName("start_time");
            jsonWriter.writeColon();
            jsonWriter.writeString(now());

            jsonWriter.writeName("tla_module");
            jsonWriter.writeColon();
            String rootFile = TLCGlobals.mainChecker.tool.getRootFile();
            jsonWriter.writeString(Path.of(rootFile).getFileName().toString().replaceAll(".tla$", ""));

            jsonWriter.writeName("tla_constants");
            jsonWriter.writeColon();
            jsonWriter.startObject();
            for (Map.Entry<String, Value> entry : constMap.entrySet()) {
                jsonWriter.writeName(entry.getKey());
                jsonWriter.writeColon();
                jsonWriter.writeAny(serializeValue(entry.getValue()));
            }
            jsonWriter.endObject();

            jsonWriter.writeName("state_files");
            jsonWriter.writeColon();
            jsonWriter.writeAny(stateFiles);

            jsonWriter.writeName("state_file_counts");
            jsonWriter.writeColon();
            jsonWriter.writeAny(stateFileCounts);

            jsonWriter.writeName("execution_count");
            jsonWriter.writeColon();
            jsonWriter.writeInt32(pathCount);

            jsonWriter.writeName("execution_files");
            jsonWriter.writeColon();
            jsonWriter.writeAny(execFiles);

            jsonWriter.endObject();
            jsonWriter.flushTo(writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        exportedSize += metaFile.toFile().length();

        MP.printMessage(EC.GENERAL, "Path cover successfully exported ("
                + formatBytes(exportedSize) + ", " + now() + ").");

        if (generateGo) {
            TlaVariableTypeExtractor typeExtractor = new TlaVariableTypeExtractor(tool);
            Map<String, TlaType> typeMap = typeExtractor.extract();
            if (typeMap == null) {
                MP.printMessage(EC.GENERAL, "Can't find TypeOK invariant: Go model mapping file can't be generated.");
            } else {
                TlaRecordType tlaStateType = new TlaRecordType(typeMap);

                StringBuilder sb = new StringBuilder();
                sb.append("package test;");

                TlaTypeToGoVisitor visitor = new TlaTypeToGoVisitor();
                sb.append(visitor.visit(tlaStateType));

                sb.append("type ModelMappingImpl struct{};")
                        .append("func(m*ModelMappingImpl)Init(){};")
                        .append("func(m*ModelMappingImpl)Reset(){};")
                        .append("func(m*ModelMappingImpl)State()ModelState{return ModelState{}};");

                sb.append("type Action = int;const(");
                for (UniqueString actionName : actionNames.values()) {
                    sb.append(actionName)
                            .append(" Action=iota;");
                }
                sb.append(");");

                sb.append("func(m*ModelMappingImpl)PerformAction(id Action,args[]any){switch id {");
                for (UniqueString actionName : actionNames.values()) {
                    sb.append("case ")
                            .append(actionName)
                            .append(":break;");
                }
                sb.append("}};");

                String goFilename = getDumpFileName().replaceAll(".json$", ".go");
                try (BufferedWriter goWriter = new BufferedWriter(new OutputStreamWriter(FileUtil.newBFOS(goFilename)))) {
                    goWriter.write(sb.toString());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                MP.printMessage(EC.GENERAL, "Successfully generated Go model mapping file " + goFilename + ".");
            }
        }

        this.stateGraphPathExtractor.cleanup();
    }

    @Override
    public String getDumpFileName() {
        return null;
    }

    @Override
    public void snapshot() throws IOException {
        // No operations
    }
}
