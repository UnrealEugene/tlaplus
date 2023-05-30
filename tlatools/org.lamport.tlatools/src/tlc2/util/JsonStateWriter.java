package tlc2.util;

import com.google.gson.*;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.FormalParamNode;
import tla2sany.semantic.OpApplNode;
import tla2sany.st.Location;
import tlc2.TLCGlobals;
import tlc2.diploma.StateGraphPathExtractor;
import tlc2.module.Json;
import tlc2.tool.Action;
import tlc2.tool.ITool;
import tlc2.tool.TLCState;
import tlc2.tool.impl.Tool;
import tlc2.value.IValue;
import tlc2.value.impl.LazyValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;
import util.Assert;
import util.ToolIO;
import util.UniqueString;

import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static tla2sany.semantic.ASTConstants.UserDefinedOpKind;

public class JsonStateWriter extends StateWriter {
    private final StateGraphPathExtractor stateGraphPathExtractor;
    private final JsonObject jsonObject;
    private final Gson gson;

    private static final String STATES = "states";
    private static final String EXECUTIONS = "executions";

    public JsonStateWriter(String fileName) throws IOException {
        super(fileName);
        this.stateGraphPathExtractor = new StateGraphPathExtractor();
        this.gson = new Gson();
        this.jsonObject = new JsonObject();

        this.jsonObject.add(STATES, new JsonArray());
        this.jsonObject.add(EXECUTIONS, new JsonArray());
    }

    private JsonElement serializeValue(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (!(value instanceof Value)) {
            value = new StringValue(value.toString());
        }
        JsonElement elem = JsonNull.INSTANCE;
        try {
            String json = Json.toJson((Value) value).getVal().toString();
            elem = this.gson.fromJson(json, JsonElement.class);
        } catch (IOException ignored) { }
        return elem;
    }

    private void addState(TLCState state) {
        Map<UniqueString, IValue> stateVals = state.getVals();
        JsonObject stateObj = new JsonObject();
        for (Map.Entry<UniqueString, IValue> entry : stateVals.entrySet()) {
            stateObj.add(entry.getKey().toString(), serializeValue(entry.getValue()));
        }
        this.jsonObject.getAsJsonArray(STATES).add(stateObj);
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

    private void writeState(TLCState state, TLCState successor, BitVector actionChecks, int from, int length,
                            boolean successorStateIsNew, Visualization visualization, Action action) {
        if (visualization == Visualization.STUTTERING) {
            return;
        }
        addEdge(state, successor, action);
    }

    @Override
    public String getDumpFileName() {
        return this.fname;
    }

    @Override
    public boolean isNoop() {
        return true;
    }

    @Override
    public boolean isDot() {
        return false;
    }

//    private void fillVariableArray(ITool tool) {
//        Assert.check(tool.getInitStates().size() == 1,
//                "JSON state graph dumping does not support multiple init states.");
//        TLCState state = tool.getInitStates().first();
//
//        JsonArray variableArray = this.jsonObject.getAsJsonArray(VARIABLES);
//        for (String varName : state.getVarsAsStrings()) {
//            JsonObject varObj = new JsonObject();
//            varObj.addProperty("name", varName);
//            varObj.addProperty("type", "any");
//
//            variableArray.add(varObj);
//        }
//    }

//    private void fillActionArray(ITool tool) {
//        Collection<Action> actions = Arrays.stream(tool.getActions())
//                .collect(Collectors.toMap(Action::getDeclaration, Function.identity(), (x, y) -> x, TreeMap::new)).values();
//
//        JsonArray actionArray = this.jsonObject.getAsJsonArray(ACTIONS);
//        for (Action action : actions) {
//            JsonArray actionArgs = new JsonArray();
//            for (FormalParamNode param : action.getOpDef().getParams()) {
//                JsonObject actionArgObj = new JsonObject();
//                actionArgObj.addProperty("name", param.getName().toString());
//                actionArgObj.addProperty("type", "any");
//                actionArgs.add(actionArgObj);
//            }
//
//            JsonObject actionObj = new JsonObject();
//            actionObj.addProperty("name", action.getName().toString());
//            actionObj.add("args", actionArgs);
//
//            actionArray.add(actionObj);
//        }
//    }

    @Override
    public void close() {
        List<TLCState> states = this.stateGraphPathExtractor.getStates();
        states.forEach(this::addState);

        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        List<Location> actionLocations = Arrays.stream(tool.getActions())
                .map(Action::getDeclaration)
                .distinct()
                .collect(Collectors.toList());
        Map<Location, Integer> locToId = IntStream.range(0, actionLocations.size())
                .boxed()
                .collect(Collectors.toMap(actionLocations::get, Function.identity()));

        JsonArray executionsArray = this.jsonObject.getAsJsonArray(EXECUTIONS);
        List<List<StateGraphPathExtractor.Edge>> paths = this.stateGraphPathExtractor.extractPaths();
        for (List<StateGraphPathExtractor.Edge> path : paths) {
            JsonArray execution = new JsonArray();
            execution.add(path.get(0).getFrom());
            for (StateGraphPathExtractor.Edge edge : path) {
                Action action = edge.getAction();
                JsonArray actionArray = new JsonArray();
                int actionId = locToId.get(action.getDeclaration());
                actionArray.add(actionId);

                OpApplNode opApplNode = (OpApplNode) action.pred;
                if (opApplNode.getOperator().getKind() == UserDefinedOpKind) {
                    for (ExprOrOpArgNode arg : opApplNode.getArgs()) {
                        Object val = tool.getVal(arg, action.con, false);
                        if (val instanceof LazyValue) {
                            val = ((LazyValue) val).eval(tool, states.get(edge.getFrom()), states.get(edge.getTo()));
                        }
                        actionArray.add(serializeValue(val));
                    }
                } else {
                    for (FormalParamNode param : action.getOpDef().getParams()) {
                        actionArray.add(serializeValue(tool.lookup(param, action.con, false)));
                    }
                }

                execution.add(actionArray);
                execution.add(edge.getTo());
            }

            executionsArray.add(execution);
        }


        this.gson.toJson(this.jsonObject, this.writer);
        super.close();
    }

    @Override
    public void snapshot() throws IOException {
        // No operations
    }
}
