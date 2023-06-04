package tlc2.util;

import com.google.gson.stream.JsonWriter;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.FormalParamNode;
import tla2sany.semantic.OpApplNode;
import tla2sany.st.Location;
import tlc2.TLCGlobals;
import tlc2.diploma.StateGraphPathExtractor;
import tlc2.diploma.TlaTypeToGoVisitor;
import tlc2.diploma.TlaVariableTypeExtractor;
import tlc2.diploma.model.TlaRecordType;
import tlc2.diploma.model.TlaType;
import tlc2.module.Json;
import tlc2.output.EC;
import tlc2.output.MP;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.impl.Tool;
import tlc2.value.IValue;
import tlc2.value.impl.LazyValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;
import util.FileUtil;
import util.UniqueString;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static tla2sany.semantic.ASTConstants.UserDefinedOpKind;

public class JsonStateWriter extends StateWriter {
    private final StateGraphPathExtractor stateGraphPathExtractor;

    private static final String STATES = "states";
    private static final String EXECUTIONS = "executions";

    public JsonStateWriter(String fileName) throws IOException {
        super(fileName);
        this.stateGraphPathExtractor = new StateGraphPathExtractor();
    }

    private String serializeValue(Object value) throws IOException {
        if (value == null) {
            return "null";
        }
        if (!(value instanceof Value)) {
            value = new StringValue(value.toString());
        }
        return Json.toJson((Value) value).getVal().toString();
    }

    private void addEdge(TLCState from, TLCState to, Action action) {
        if (from.fingerPrint() != to.fingerPrint()) {
            this.stateGraphPathExtractor.addTransition(from, to, action);
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
    public void close() {
        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        Map<Location, UniqueString> actionNames = Arrays.stream(tool.getActions())
                .collect(Collectors.groupingBy(Action::getDeclaration,
                        Collectors.reducing(null, Action::getName, (x, y) -> x == null ? y : x)));
        List<Location> actionLocations = Arrays.stream(tool.getActions())
                .map(Action::getDeclaration)
                .distinct()
                .collect(Collectors.toList());
        MP.printMessage(EC.GENERAL, "Found the following actions:");
        for (Location loc : actionLocations) {
            MP.printMessage(EC.GENERAL, "  " + actionNames.get(loc));
        }
        Map<Location, Integer> locToId = IntStream.range(0, actionLocations.size())
                .boxed()
                .collect(Collectors.toMap(actionLocations::get, Function.identity()));

        MP.printMessage(EC.GENERAL, "State graph exporting started.");

        List<TLCState> states = this.stateGraphPathExtractor.getStates();
        List<List<StateGraphPathExtractor.Edge>> paths = this.stateGraphPathExtractor.extractPaths();

        MP.printMessage(EC.GENERAL, "Model state graph JSON exporting started.");
        try (JsonWriter jsonWriter = new JsonWriter(this.writer)) {
            jsonWriter.beginObject();

            jsonWriter.name(STATES).beginArray();
            for (TLCState state : states) {
                jsonWriter.beginObject();
                Map<UniqueString, IValue> stateVals = state.getVals();
                for (Map.Entry<UniqueString, IValue> entry : stateVals.entrySet()) {
                    jsonWriter
                            .name(entry.getKey().toString())
                            .jsonValue(serializeValue(entry.getValue()));
                }
                jsonWriter.endObject();
            }
            jsonWriter.endArray();

            jsonWriter.name(EXECUTIONS).beginArray();
            for (List<StateGraphPathExtractor.Edge> path : paths) {
                jsonWriter.beginArray();
                if (!path.isEmpty()) {
                    jsonWriter.value(path.get(0).getFrom());
                }
                for (StateGraphPathExtractor.Edge edge : path) {
                    jsonWriter.beginArray();

                    Action action = edge.getAction();
                    int actionId = locToId.get(action.getDeclaration());
                    jsonWriter.value(actionId);

                    OpApplNode opApplNode = (OpApplNode) action.pred;
                    if (opApplNode.getOperator().getKind() == UserDefinedOpKind) {
                        for (ExprOrOpArgNode arg : opApplNode.getArgs()) {
                            Object val = tool.getVal(arg, action.con, false);
                            if (val instanceof LazyValue) {
                                val = ((LazyValue) val).eval(tool, states.get(edge.getFrom()), states.get(edge.getTo()));
                            }
                            jsonWriter.jsonValue(serializeValue(val));
                        }
                    } else {
                        for (FormalParamNode param : action.getOpDef().getParams()) {
                            jsonWriter.jsonValue(serializeValue(tool.lookup(param, action.con, false)));
                        }
                    }

                    jsonWriter.endArray();
                    jsonWriter.value(edge.getTo());
                }
                jsonWriter.endArray();
            }
            jsonWriter.endArray();

            jsonWriter.endObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MP.printMessage(EC.GENERAL, "Path cover successfully exported into JSON file " + getDumpFileName() + ".");

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

        super.close();
    }

    @Override
    public void snapshot() throws IOException {
        // No operations
    }
}
