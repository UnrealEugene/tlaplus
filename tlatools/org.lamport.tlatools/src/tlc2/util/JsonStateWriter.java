package tlc2.util;

import com.alibaba.fastjson2.JSONWriter;
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
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static tla2sany.semantic.ASTConstants.UserDefinedOpKind;

public class JsonStateWriter extends StateWriter {
    private final boolean generateGo;
    private final StateGraphPathExtractor stateGraphPathExtractor;

    public JsonStateWriter(String fileName, boolean generateGo) throws IOException {
        super(fileName);
        this.generateGo = generateGo;
        this.stateGraphPathExtractor = new StateGraphPathExtractor();
    }

    private Object serializeValue(Object value) throws IOException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Value)) {
            value = new StringValue(value.toString());
        }
        return Json.getNode((Value) value);
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
    @SuppressWarnings("unchecked")
    public void close() {
        if (TLCGlobals.mainChecker.getStateQueueSize() != 0) {
            super.close();
            new File(getDumpFileName()).deleteOnExit();
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
        MP.printMessage(EC.GENERAL, "Found the following actions:");
        for (Location loc : actionLocations) {
            MP.printMessage(EC.GENERAL, "  " + actionNames.get(loc));
        }
        Map<Location, Integer> locToId = IntStream.range(0, actionLocations.size())
                .boxed()
                .collect(Collectors.toMap(actionLocations::get, Function.identity()));

        MP.printMessage(EC.GENERAL, "State graph exporting started.");

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

        MP.printMessage(EC.GENERAL, "Model state graph JSON exporting started.");
        try (JSONWriter jsonWriter = JSONWriter.ofUTF8()) {
            jsonWriter.startObject();

            jsonWriter.writeName("constants");
            jsonWriter.writeColon();
            jsonWriter.startObject();
            for (Map.Entry<String, Value> entry : constMap.entrySet()) {
                jsonWriter.writeName(entry.getKey());
                jsonWriter.writeColon();
                jsonWriter.writeAny(serializeValue(entry.getValue()));
            }
            jsonWriter.endObject();

            jsonWriter.writeName("states");
            jsonWriter.writeColon();
            jsonWriter.startArray();
            for (Iterator<TLCState> iterator = states.iterator(); iterator.hasNext(); ) {
                TLCState state = iterator.next();
                jsonWriter.startObject();
                Map<UniqueString, IValue> stateVals = state.getVals();
                for (Map.Entry<UniqueString, IValue> entry : stateVals.entrySet()) {
                    jsonWriter.writeName(entry.getKey().toString());
                    jsonWriter.writeColon();
                    jsonWriter.writeAny(serializeValue(entry.getValue()));
                }
                jsonWriter.endObject();
                jsonWriter.flushTo(this.writer);
                if (iterator.hasNext()) {
                    jsonWriter.writeComma();
                }
            }
            jsonWriter.endArray();

            jsonWriter.writeName("executions");
            jsonWriter.writeColon();
            jsonWriter.startObject();

            jsonWriter.writeName("count");
            jsonWriter.writeColon();
            jsonWriter.writeInt32(this.stateGraphPathExtractor.getPathCount());
            jsonWriter.writeName("array");
            jsonWriter.writeColon();
            jsonWriter.startArray();
            for (Iterator<List<StateGraphPathExtractor.Edge>> iterator = paths.iterator(); iterator.hasNext(); ) {
                List<StateGraphPathExtractor.Edge> path = iterator.next();
                jsonWriter.startArray();
                if (!path.isEmpty()) {
                    jsonWriter.writeInt32(path.get(0).getFrom());
                }
                for (StateGraphPathExtractor.Edge edge : path) {
                    jsonWriter.writeComma();

                    jsonWriter.startArray();

                    Action action = edge.getAction();
                    int actionId = locToId.get(action.getDeclaration());
                    jsonWriter.writeInt32(actionId);
                    jsonWriter.writeComma();

                    OpApplNode opApplNode = (OpApplNode) action.pred;
                    if (opApplNode.getOperator().getKind() == UserDefinedOpKind) {
                        ExprOrOpArgNode[] args = opApplNode.getArgs();
                        for (int i = 0; i < args.length; i++) {
                            if (i > 0) {
                                jsonWriter.writeComma();
                            }
                            ExprOrOpArgNode arg = args[i];
                            Object val = tool.getVal(arg, action.con, false);
                            if (val instanceof LazyValue) {
                                val = ((LazyValue) val).eval(tool, states.get(edge.getFrom()), states.get(edge.getTo()));
                            }
                            jsonWriter.writeAny(serializeValue(val));
                        }
                    } else {
                        FormalParamNode[] params = action.getOpDef().getParams();
                        for (int i = 0; i < params.length; i++) {
                            if (i > 0) {
                                jsonWriter.writeComma();
                            }
                            FormalParamNode param = params[i];
                            jsonWriter.writeAny(serializeValue(tool.lookup(param, action.con, false)));
                        }
                    }

                    jsonWriter.endArray();
                    jsonWriter.writeComma();
                    jsonWriter.writeInt32(edge.getTo());
                }
                jsonWriter.endArray();
                jsonWriter.flushTo(this.writer);
                if (iterator.hasNext()) {
                    jsonWriter.writeComma();
                }
            }
            jsonWriter.endArray();
            jsonWriter.endObject();

            jsonWriter.endObject();

            jsonWriter.flushTo(this.writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        MP.printMessage(EC.GENERAL, "Path cover successfully exported into JSON file " + getDumpFileName() + ".");

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
        super.close();
    }

    @Override
    public void snapshot() throws IOException {
        // No operations
    }
}
