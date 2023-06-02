package tlc2.diploma;

import tla2sany.semantic.*;
import tlc2.diploma.model.*;
import tlc2.tool.Action;
import tlc2.tool.BuiltInOPs;
import tlc2.tool.impl.Tool;
import tlc2.util.Context;
import tlc2.value.impl.*;
import util.UniqueString;

import java.util.*;
import java.util.stream.Collectors;

import static tlc2.tool.ToolGlobals.*;
import static tlc2.value.ValueConstants.*;

public class TlaVariableTypeExtractor {
    private final Tool tool;

    public TlaVariableTypeExtractor(Tool tool) {
        this.tool = tool;
    }

    public Map<String, TlaType> extract() {
        Action[] invariants = this.tool.getInvariants();
        Optional<Action> typeOkOpt = Arrays.stream(invariants)
                .filter(a -> a.getName().toString().toLowerCase().contains("typeok"))
                .findFirst();
        if (typeOkOpt.isEmpty()) {
            return null;
        }
        Action typeOk = typeOkOpt.get();
        return findVariablesTypes(typeOk.pred);
    }

    private Map<String, TlaType> findVariablesTypes(SemanticNode node) {
        switch (node.getKind()) {
            case OpApplKind:
                return findVariablesTypes((OpApplNode) node);
            case LetInKind:
                return findVariablesTypes(((LetInNode) node).getBody());
        }
        return Collections.emptyMap();
    }

    private Map<String, TlaType> findVariablesTypes(OpApplNode node) {
        ExprOrOpArgNode[] args = node.getArgs();
        SymbolNode opNode = node.getOperator();
        int opCode = BuiltInOPs.getOpCode(opNode.getName());
        switch (opCode) {
            case OPCODE_be:
                return findVariablesTypes(args[0]);
            case OPCODE_cl: // Fallthrough
            case OPCODE_land:
                return Arrays.stream(args)
                        .map(this::findVariablesTypes)
                        .map(Map::entrySet)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            case OPCODE_in: {
                if (!(args[0] instanceof OpApplNode)) {
                    break;
                }
                OpApplNode lhs = (OpApplNode) args[0];
                if (lhs.getOperator().getKind() != VariableDeclKind) {
                    break;
                }
                String varName = lhs.getOperator().getName().toString();
                TlaType varType = extractVariableType(args[1]);
                return Map.of(varName, varType);
            }
        }
        return Collections.emptyMap();
    }

    private TlaType extractVariableType(SemanticNode node) {
        switch (node.getKind()) {
            case OpApplKind:
                return extractVariableType((OpApplNode) node);
            case LetInKind:
                return extractVariableType(((LetInNode) node).getBody());
        }
        return new TlaAnyType();
    }

    private List<ExprOrOpArgNode> extractCupAssocArgs(OpApplNode node) {
        ExprOrOpArgNode[] args = node.getArgs();
        List<ExprOrOpArgNode> types = new ArrayList<>();
        for (ExprOrOpArgNode arg : args) {
            if (arg.getKind() != OpApplKind) {
                types.add(arg);
                continue;
            }
            OpApplNode opApplNode = (OpApplNode) arg;
            if (BuiltInOPs.getOpCode(opApplNode.getOperator().getName()) == OPCODE_cup) {
                types.addAll(extractCupAssocArgs(opApplNode));
            } else {
                types.add(arg);
            }
        }
        return types;
    }

    private TlaType getUnionOfTypes(List<TlaType> types) {
        if (types.isEmpty()) {
            return new TlaAnyType();
        }
        if (types.stream().distinct().count() == 1) {
            return types.get(0);
        }
        List<Class<?>> classes = types.stream().map(Object::getClass).distinct().collect(Collectors.toList());
        if (classes.size() != 1) {
            return new TlaAnyType();
        }
        Class<?> clazz = classes.get(0);
        if (clazz == TlaRecordType.class) {
            Map<String, TlaType> newFields = types.stream()
                    .map(t -> ((TlaRecordType) t).getFields().entrySet())
                    .flatMap(Collection::stream)
                    .collect(Collectors.groupingBy(Map.Entry::getKey,
                            Collectors.mapping(Map.Entry::getValue,
                                    Collectors.collectingAndThen(Collectors.toList(), this::getUnionOfTypes))));
            return new TlaRecordType(newFields);
        }
        if (clazz == TlaEnumType.class) {
            List<String> newEnumElements = types.stream()
                    .map(t -> ((TlaEnumType) t).getElements())
                    .flatMap(Collection::stream)
                    .distinct()
                    .collect(Collectors.toList());
            return new TlaEnumType(newEnumElements);
        }
        return new TlaAnyType();
    }

    private TlaType extractVariableType(OpApplNode node) {
        ExprOrOpArgNode[] args = node.getArgs();
        SymbolNode opNode = node.getOperator();
        int opCode = BuiltInOPs.getOpCode(opNode.getName());
        switch (opCode) {
            case 0: {
                if (opNode instanceof OpDefNode) {
                    OpDefNode opDefNode = (OpDefNode) opNode;
                    UniqueString opName = opDefNode.getName();
                    ModuleNode moduleNode = opDefNode.getOriginallyDefinedInModuleNode();
                    if (moduleNode != null) {
                        UniqueString opModuleName = moduleNode.getName();
                        if (opModuleName.equals("Naturals")) {
                            if (opName.equals("Nat")) {
                                return new TlaNatType();
                            }
                            if (opName.equals("..")) {
                                ExprOrOpArgNode left = args[0];
                                if (left instanceof NumeralNode && ((NumeralNode) left).val() >= 0) {
                                    return new TlaNatType();
                                }
                                return new TlaIntType();
                            }
                        }
                        if (opModuleName.equals("Integers") && opName.equals("Int")) {
                            return new TlaIntType();
                        }
                        if (opModuleName.equals("Sequences") && opName.equals("Seq")) {
                            return new TlaSeqType(extractVariableType(args[0]));
                        }
                    }
                    if (opName.equals("BOOLEAN")) {
                        return new TlaBoolType();
                    }
                    return extractVariableType(opDefNode.getBody());
                }
                if (opNode.getKind() == ConstantDeclKind) {
                    Value value = (Value) this.tool.lookup(opNode, Context.Empty, false);
                    return extractSetType(value);
                }
                break;
            }
            case OPCODE_sof: {
                TlaType fromType = extractVariableType(args[0]);
                TlaType toType = extractVariableType(args[1]);
                if (fromType instanceof TlaNatType) {
                    return new TlaSeqType(toType);
                }
                return new TlaFunctionType(fromType, toType);
            }
            case OPCODE_sor: {
                Map<String, TlaType> fields = new HashMap<>();
                for (ExprOrOpArgNode arg : args) {
                    OpApplNode pairNode = (OpApplNode) arg;
                    ExprOrOpArgNode[] pair = pairNode.getArgs();
                    String newFieldName = ((StringNode) pair[0]).getRep().toString();
                    fields.put(newFieldName, extractVariableType(pair[1]));
                }
                return new TlaRecordType(fields);
            }
            case OPCODE_se: {
                if (args.length == 0) { // shouldn't pass invariant check
                    return new TlaAnyType();
                }
                List<ExprOrOpArgNode> elems = Arrays.asList(args);
                List<Integer> elemKinds = elems.stream().map(SemanticNode::getKind).distinct().collect(Collectors.toList());
                if (elemKinds.size() != 1) {
                    return new TlaAnyType();
                }
                int kind = elemKinds.get(0);
                switch (kind) {
                    case OpApplKind: {
                        List<OpApplNode> opApplArgs = Arrays.stream(args)
                                .map(e -> ((OpApplNode) e))
                                .collect(Collectors.toList());
                        if (opApplArgs.stream()
                                .map(e -> e.getOperator().getName())
                                .allMatch(e -> e.equals("TRUE") || e.equals("FALSE"))) {
                            return new TlaBoolType();
                        }
                        break;
                    }
                    case StringKind: {
                        return new TlaEnumType(Arrays.stream(args)
                                .map(e -> ((StringNode) e).getRep().toString())
                                .collect(Collectors.toList()));
                    }
                    case NumeralKind: {
                        int min = Arrays.stream(args)
                                .mapToInt(e -> ((NumeralNode) e).val())
                                .min().getAsInt();
                        return min >= 0 ? new TlaNatType() : new TlaIntType();
                    }
                }
                break;
            }
            case OPCODE_soa: {
                TlaType innerType = extractVariableType(args[0]);
                return new TlaSetOfType(innerType);
            }
            case OPCODE_union: {
                TlaType innerType = extractVariableType(args[0]);
                if (innerType instanceof TlaSetOfType) {
                    return ((TlaSetOfType) innerType).getInnerType();
                }
                break;
            }
            case OPCODE_cup: {
                List<ExprOrOpArgNode> assocArgs = extractCupAssocArgs(node);
                List<TlaType> types = assocArgs.stream()
                        .map(this::extractVariableType)
                        .collect(Collectors.toList());
                return getUnionOfTypes(types);
            }
        }
        return new TlaAnyType();
    }

    private TlaType extractSetType(Value value) {
        switch (value.getKind()) {
            case SETENUMVALUE: {
                SetEnumValue enumValue = (SetEnumValue) value;
                List<Value> elems = Arrays.asList(enumValue.elems.toArray());
                List<Byte> elemKinds = elems.stream().map(Value::getKind).distinct().collect(Collectors.toList());
                if (elemKinds.size() != 1) {
                    return new TlaAnyType();
                }
                byte kind = elemKinds.get(0);
                switch (kind) {
                    case STRINGVALUE: {
                        return new TlaEnumType(elems.stream()
                                .map(v -> ((StringValue) v).val.toString())
                                .collect(Collectors.toList()));
                    }
                    case INTVALUE: {
                        if (elems.stream().allMatch(v -> ((IntValue) v).val >= 0)) {
                            return new TlaNatType();
                        }
                        return new TlaIntType();
                    }
                    case SETENUMVALUE: {
                        List<TlaType> types = elems.stream()
                                .map(this::extractSetType)
                                .distinct()
                                .collect(Collectors.toList());
                        if (types.size() != 1) {
                            return new TlaSetOfType(new TlaAnyType());
                        }
                        return new TlaSetOfType(types.get(0));
                    }
                }
                return new TlaAnyType();
            }
        }
        return new TlaAnyType();
    }
}
