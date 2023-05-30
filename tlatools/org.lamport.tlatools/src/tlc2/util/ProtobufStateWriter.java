package tlc2.util;

import tla2sany.semantic.*;
import tlc2.tool.Action;
import tlc2.tool.BuiltInOPs;
import tlc2.tool.impl.Tool;
import tlc2.value.impl.*;
import util.Assert;
import util.FileUtil;
import util.UniqueString;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static tla2sany.semantic.ASTConstants.*;
import static tlc2.tool.ToolGlobals.*;
import static tlc2.value.ValueConstants.*;

public class ProtobufStateWriter {
    private final PrintWriter writer;
    private final Tool tool;

    private static final List<String> ONE_OF_SUFFIXES
            = List.of("First", "Second", "Third", "Fourth", "Fifth");

    public ProtobufStateWriter(String fileName, Tool tool) throws FileNotFoundException {
        this.writer = new PrintWriter(FileUtil.newBFOS(fileName));
        this.tool = tool;
    }

    private static void fail(String message) {
        fail(message, null, null);
    }

    private static void fail(String message, SemanticNode node, Context context) {
        Assert.fail("Protobuf for states can't be generated: " + message, node);
    }

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([A-Z]+|[A-Za-z])[a-z\\d]*");

    private static List<String> extractWords(String str) {
        List<String> words = new ArrayList<>();
        Matcher matcher = CAMEL_CASE_PATTERN.matcher(str);
        while (matcher.find()) {
            words.add(matcher.group());
        }
        return words;
    }

    private static String toSnakeCase(String str) {
        return extractWords(str).stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining("_"));
    }

    private static String toScreamingSnakeCase(String str) {
        return extractWords(str).stream()
                .map(String::toUpperCase)
                .collect(Collectors.joining("_"));
    }

    private static String upperFirstChar(String str) {
        if (str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static String lowerFirstChar(String str) {
        if (str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    private static String toCamelCase(String str) {
        return extractWords(str).stream()
                .map(String::toLowerCase)
                .map(ProtobufStateWriter::upperFirstChar)
                .collect(Collectors.joining());
    }

    private static String toLowerCamelCase(String str) {
        return lowerFirstChar(toCamelCase(str));
    }

    private List<ProtoField> findVariablesTypes(OpApplNode node) {
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
                        .flatMap(Collection::stream)
                        .collect(Collectors.toList());
            case OPCODE_in: {
                if (!(args[0] instanceof OpApplNode)) {
                    break;
                }
                OpApplNode lhs = (OpApplNode) args[0];
                if (lhs.getOperator().getKind() != VariableDeclKind) {
                    break;
                }
                String varName = lhs.getOperator().getName().toString();
                ProtoType varType = extractVariableType(args[1], varName);
                return List.of(new ProtoField(varType, varName));
            }
        }
        return Collections.emptyList();
    }

    private List<ProtoField> findVariablesTypes(SemanticNode node) {
        switch (node.getKind()) {
            case OpApplKind:
                return findVariablesTypes((OpApplNode) node);
            case LetInKind:
                return findVariablesTypes(((LetInNode) node).getBody());
        }
        return Collections.emptyList();
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

    private ProtoType extractVariableType(OpApplNode node, String typeName) {
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
                                return ProtoBuiltInType.UINT_32;
                            }
                            if (opName.equals("..")) {
                                ExprOrOpArgNode left = args[0];
                                if (left instanceof NumeralNode && ((NumeralNode) left).val() >= 0) {
                                    return ProtoBuiltInType.UINT_32;
                                }
                                return ProtoBuiltInType.INT_32;
                            }
                        }
                        if (opModuleName.equals("Integers") && opName.equals("Int")) {
                            return ProtoBuiltInType.INT_32;
                        }
                    }
                    if (opName.equals("BOOLEAN")) {
                        return ProtoBuiltInType.BOOLEAN;
                    }

                    return extractVariableType(opDefNode.getBody(), opName.toString());
                }
                if (opNode.getKind() == ConstantDeclKind) {
                    Value value = (Value) this.tool.lookup(opNode, Context.Empty, false);
                    return extractValueType(value, opNode.getName().toString());
                }
                break;
            }
            case OPCODE_sof: {
                ProtoType fromType = extractVariableType(args[0], typeName);
                ProtoType toType = extractVariableType(args[1], typeName);
                if (fromType == ProtoBuiltInType.UINT_32) {
                    return new ProtoRepeatedType(toType);
                }
                return new ProtoMapType(fromType, toType);
            }
            case OPCODE_sor: {
                List<ProtoField> fields = new ArrayList<>();
                for (ExprOrOpArgNode arg : args) {
                    OpApplNode pairNode = (OpApplNode) arg;
                    ExprOrOpArgNode[] pair = pairNode.getArgs();
                    String fieldName = ((StringNode) pair[0]).getRep().toString();
                    ProtoField field = new ProtoField(extractVariableType(pair[1], fieldName), fieldName);
                    fields.add(field);
                }
                return new ProtoMessageType(typeName, fields);
            }
            case OPCODE_se: {
                if (args.length == 0) { // shouldn't pass invariant check
                    return ProtoBuiltInType.ANY;
                }
                if (Arrays.stream(args).allMatch(e -> e.getKind() == OpApplKind)) {
                    List<OpApplNode> opApplArgs = Arrays.stream(args)
                            .map(e -> ((OpApplNode) e))
                            .collect(Collectors.toList());
                    if (opApplArgs.stream()
                            .map(e -> e.getOperator().getName())
                            .allMatch(e -> e.equals("TRUE") || e.equals("FALSE"))) {
                        return ProtoBuiltInType.BOOLEAN;
                    }
                }
                if (Arrays.stream(args).allMatch(e -> e.getKind() == StringKind)) {
                    return new ProtoEnumType(typeName, Arrays.stream(args)
                            .map(e -> ((StringNode) e).getRep().toString())
                            .collect(Collectors.toList()));
                }
                if (Arrays.stream(args).allMatch(e -> e.getKind() == NumeralKind)) {
                    int min = Arrays.stream(args)
                            .mapToInt(e -> ((NumeralNode) e).val())
                            .min().getAsInt();
                    return min >= 0 ? ProtoBuiltInType.UINT_32 : ProtoBuiltInType.INT_32;
                }
                break;
            }
            case OPCODE_soa: {
                ProtoType innerType = extractVariableType(args[0], typeName);
                return new ProtoSetType(innerType);
            }
            case OPCODE_union: {
                ProtoType innerType = extractVariableType(args[0], typeName);
                if (innerType instanceof ProtoSetType) {
                    return ((ProtoSetType) innerType).getInnerType();
                }
                break;
            }
            case OPCODE_cup: {
                List<ExprOrOpArgNode> assocArgs = extractCupAssocArgs(node);
                if (assocArgs.size() > ONE_OF_SUFFIXES.size()) {
                    break;
                }
                List<ProtoType> types = assocArgs.stream()
                        .map(arg -> extractVariableType(arg, typeName))
                        .collect(Collectors.toList());
                if (types.stream().allMatch(type -> type instanceof ProtoMessageType)) {
                    List<ProtoMessageType> messageTypes = types.stream()
                            .map(type -> ((ProtoMessageType) type))
                            .collect(Collectors.toList());

                    Map<ProtoFieldMeta, List<ProtoField>> count = new HashMap<>();
                    for (ProtoMessageType messageType : messageTypes) {
                        for (ProtoMessageEntry entry : messageType.getFields()) {
                            if (entry instanceof ProtoField) {
                                ProtoField field = ((ProtoField) entry);
                                ProtoFieldMeta meta = ProtoFieldMeta.from(field);
                                count.putIfAbsent(meta, new ArrayList<>());
                                count.get(meta).add(field);
                            }
                        }
                    }

                    List<ProtoField> sharedFields = new ArrayList<>();
                    boolean hasUniqueSharedTypeField = count.values().stream()
                            .filter(l -> l.size() == assocArgs.size()
                                    && l.stream().allMatch(field -> field.getType() instanceof ProtoEnumType))
                            .count() == 1;
                    List<String> oneOfFieldSuffixes = hasUniqueSharedTypeField ? new ArrayList<>() : ONE_OF_SUFFIXES;
                    for (Map.Entry<ProtoFieldMeta, List<ProtoField>> entry : count.entrySet()) {
                        if (entry.getValue().size() != assocArgs.size()) {
                            continue;
                        }
                        List<ProtoField> fields = entry.getValue();
                        if (fields.stream().allMatch(field -> field.getType() instanceof ProtoEnumType)) {
                            String enumName = null;
                            List<String> enumValues = new ArrayList<>();
                            for (ProtoField field : fields) {
                                ProtoEnumType enumType = (ProtoEnumType) field.getType();
                                enumValues.addAll(enumType.getValues());
                                enumName = enumType.getName();
                                if (hasUniqueSharedTypeField) {
                                    oneOfFieldSuffixes.add(String.join("_OR_", enumType.getValues()));
                                }
                            }
                            sharedFields.add(new ProtoField(new ProtoEnumType(enumName, enumValues), entry.getKey().getName()));
                        } else {
                            sharedFields.add(fields.get(0));
                        }
                    }

                    List<ProtoField> oneOfFields = new ArrayList<>();
                    for (int i = 0; i < messageTypes.size(); i++) {
                        ProtoMessageType messageType = messageTypes.get(i);
                        List<ProtoMessageEntry> newMessageFields = new ArrayList<>();
                        for (ProtoMessageEntry entry : messageType.getFields()) {
                            if (entry instanceof ProtoField) {
                                ProtoField field = (ProtoField) entry;
                                ProtoFieldMeta meta = ProtoFieldMeta.from(field);
                                if (count.get(meta).size() != assocArgs.size()) {
                                    newMessageFields.add(field);
                                }
                            } else {
                                newMessageFields.add(entry);
                            }
                        }
                        String messageName = messageType.getName() + oneOfFieldSuffixes.get(i);
                        ProtoMessageType newMessageType = new ProtoMessageType(messageName, newMessageFields);
                        oneOfFields.add(new ProtoField(newMessageType, messageName));
                    }

                    List<ProtoMessageEntry> fields = new ArrayList<>(sharedFields);
                    fields.add(new ProtoOneOf("OneOf", oneOfFields));
                    return new ProtoMessageType(typeName, fields);
                }
                return new ProtoMessageType(typeName, IntStream.range(0, assocArgs.size())
                        .mapToObj(i -> new ProtoField(types.get(i), typeName + ONE_OF_SUFFIXES.get(i)))
                        .collect(Collectors.toList()));
            }
        }
        return ProtoBuiltInType.ANY;
    }

    private ProtoType extractVariableType(SemanticNode node, String typeName) {
        switch (node.getKind()) {
            case OpApplKind:
                return extractVariableType((OpApplNode) node, typeName);
            case LetInKind:
                return extractVariableType(((LetInNode) node).getBody(), typeName);
        }
        return ProtoBuiltInType.ANY;
    }

    private ProtoType extractValueType(Value value, String name) {
        switch (value.getKind()) {
            case SETENUMVALUE: {
                SetEnumValue setEnumValue = (SetEnumValue) value;
                List<Value> args = Arrays.asList(setEnumValue.elems.toArray());
                if (args.stream().allMatch(e -> e.getKind() == STRINGVALUE)) {
                    return new ProtoEnumType(name, args.stream()
                            .map(e -> ((StringValue) e).getVal().toString())
                            .collect(Collectors.toList()));
                }
                if (args.stream().allMatch(e -> e.getKind() == INTVALUE)) {
                    int min = args.stream()
                            .mapToInt(e -> ((IntValue) e).val)
                            .min().getAsInt();
                    return min >= 0 ? ProtoBuiltInType.UINT_32 : ProtoBuiltInType.INT_32;
                }
                if (args.stream().allMatch(e -> e.getKind() == BOOLVALUE)) {
                    return ProtoBuiltInType.BOOLEAN;
                }
                break;
            }
            case INTERVALVALUE: {
                IntervalValue intervalValue = (IntervalValue) value;
                return intervalValue.low >= 0 ? ProtoBuiltInType.UINT_32 : ProtoBuiltInType.INT_32;
            }
        }
        return ProtoBuiltInType.ANY;
    }

    public void process() {
        Action[] invariants = this.tool.getInvariants();
        Optional<Action> typeOkOpt = Arrays.stream(invariants)
                .filter(a -> a.getName().toString().toLowerCase().contains("type"))
                .findFirst();
        if (typeOkOpt.isEmpty()) {
            fail("Can't find TypeOK invariant in the specification.");
            return;
        }
        Action typeOk = typeOkOpt.get();
        ProtoType stateType = new ProtoMessageType("State", findVariablesTypes(typeOk.pred));
        this.writer.print("syntax = \"proto3\"; ");
        this.writer.print("import \"google/protobuf/any.proto\"; ");
        this.writer.print("import \"google/protobuf/empty.proto\"; ");
        this.writer.write(stateType.toProtobufDefinitions().get(0));
        this.writer.close();
    }

    private interface ProtoType {
        List<String> toProtobufDefinitions();
        String toProtobufType();
    }

    private enum ProtoBuiltInType implements ProtoType {
        ANY("google.protobuf.Any"),
        EMPTY("google.protobuf.Empty"),
        BOOLEAN("bool"),
        INT_32("int32"),
        UINT_32("uint32"),
        STRING("string");

        private final String string;

        ProtoBuiltInType(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }

        @Override
        public List<String> toProtobufDefinitions() {
            return Collections.emptyList();
        }

        @Override
        public String toProtobufType() {
            return string;
        }
    }

    private static class ProtoRepeatedType implements ProtoType {
        private final ProtoType innerType;

        private ProtoRepeatedType(ProtoType innerType) {
            if (innerType instanceof ProtoRepeatedType) {
                fail("Attempt to create double repeated protobuf type");
            }
            this.innerType = innerType;
        }

        public ProtoType getInnerType() {
            return innerType;
        }

        @Override
        public String toString() {
            return "repeated " + innerType.toString();
        }

        @Override
        public List<String> toProtobufDefinitions() {
            return innerType.toProtobufDefinitions();
        }

        @Override
        public String toProtobufType() {
            return "repeated " + innerType.toProtobufType();
        }
    }

    private interface ProtoMessageEntry {
        String toProtobuf(int fieldNumber);
    }

    private static class ProtoField implements ProtoMessageEntry {
        private final ProtoType type;
        private final String name;

        public ProtoField(ProtoType type, String name) {
            this.type = type;
            this.name = toSnakeCase(name);
        }

        public ProtoType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return String.format("%s %s", type, name);
        }

        @Override
        public String toProtobuf(int fieldNumber) {
            return String.format("%s %s = %d;", type.toProtobufType(), name, fieldNumber);
        }
    }

    private static class ProtoFieldMeta {
        private final ProtoField innerField;

        private ProtoFieldMeta(ProtoField innerField) {
            this.innerField = innerField;
        }

        private static ProtoFieldMeta from(ProtoField field) {
            return new ProtoFieldMeta(field);
        }

        public String getName() {
            return innerField.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProtoFieldMeta that = (ProtoFieldMeta) o;
            return this.innerField.getType().toProtobufType().equals(that.innerField.getType().toProtobufType())
                    && this.innerField.getName().equals(that.innerField.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(innerField.getType().toProtobufType(), innerField.getName());
        }
    }

    private static class ProtoMessageType implements ProtoType {
        private final String name;
        private final List<ProtoMessageEntry> fields;

        public ProtoMessageType(String name, List<? extends ProtoMessageEntry> fields) {
            this.name = toCamelCase(name);
            this.fields = new ArrayList<>(fields);
        }

        public String getName() {
            return name;
        }

        public List<ProtoMessageEntry> getFields() {
            return fields;
        }

        @Override
        public String toString() {
            return name + fields.stream()
                    .map(ProtoMessageEntry::toString)
                    .collect(Collectors.joining(", ", "[", "]"));
        }

        @Override
        public List<String> toProtobufDefinitions() {
            String definitions = fields.stream()
                    .map(e -> e instanceof ProtoField ? List.of((ProtoField) e) : ((ProtoOneOf) e).getFields())
                    .flatMap(Collection::stream)
                    .map(ProtoField::getType)
                    .map(ProtoType::toProtobufDefinitions)
                    .flatMap(Collection::stream)
                    .collect(Collectors.joining(" ", "", " "));
            List<String> fieldStrings = new ArrayList<>();
            int j = 1;
            for (ProtoMessageEntry field : fields) {
                fieldStrings.add(field.toProtobuf(j));
                j += field instanceof ProtoOneOf ? ((ProtoOneOf) field).getFields().size() : 1;
            }
            return List.of(String.format("message %s { %s%s }", name, definitions, String.join(" ", fieldStrings)));
        }

        @Override
        public String toProtobufType() {
            return name;
        }
    }

    private static class ProtoMapType implements ProtoType {
        private final ProtoType fromType;
        private final ProtoType toType;

        private ProtoMapType(ProtoType fromType, ProtoType toType) {
            this.fromType = fromType;
            this.toType = toType;
        }

        public ProtoType getFromType() {
            return fromType;
        }

        public ProtoType getToType() {
            return toType;
        }

        @Override
        public String toString() {
            return String.format("map<%s, %s>", fromType.toString(), toType.toString());
        }

        @Override
        public List<String> toProtobufDefinitions() {
            return Stream.of(fromType.toProtobufDefinitions(), toType.toProtobufDefinitions())
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        }

        @Override
        public String toProtobufType() {
            return String.format("map<%s, %s>", fromType.toProtobufType(), toType.toProtobufType());
        }
    }

    private static class ProtoSetType implements ProtoType {
        private final ProtoMapType backingMap;

        private ProtoSetType(ProtoType type) {
            this.backingMap = new ProtoMapType(type, ProtoBuiltInType.EMPTY);
        }

        public ProtoType getInnerType() {
            return backingMap.getFromType();
        }

        public ProtoMapType getBackingMap() {
            return backingMap;
        }

        @Override
        public String toString() {
            return backingMap.toString();
        }

        @Override
        public List<String> toProtobufDefinitions() {
            return backingMap.toProtobufDefinitions();
        }

        @Override
        public String toProtobufType() {
            return backingMap.toProtobufType();
        }
    }

    private static class ProtoEnumType implements ProtoType {
        private final String name;
        private final List<String> values;

        public ProtoEnumType(String name, List<String> values) {
            this.name = toCamelCase(name);
            this.values = values.stream()
                    .map(ProtobufStateWriter::toScreamingSnakeCase)
                    .collect(Collectors.toList());
        }

        public String getName() {
            return name;
        }

        public List<String> getValues() {
            return values;
        }

        @Override
        public String toString() {
            return name + values.stream()
                    .collect(Collectors.joining(", ", "{", "}"));
        }

        @Override
        public List<String> toProtobufDefinitions() {
            String body = IntStream.range(0, values.size())
                    .mapToObj(i -> String.format("%s = %d;", values.get(i), i))
                    .collect(Collectors.joining(" "));
            return List.of(String.format("enum %s { %s }", name, body));
        }

        @Override
        public String toProtobufType() {
            return name;
        }
    }

    private static class ProtoOneOf implements ProtoMessageEntry {
        private final String name;
        private final List<ProtoField> fields;

        public ProtoOneOf(String name, List<ProtoField> fields) {
            this.name = toCamelCase(name);
            this.fields = fields;
        }

        public ProtoOneOf(String name, ProtoField... fields) {
            this.name = toCamelCase(name);
            this.fields = Arrays.asList(fields);
        }

        public String getName() {
            return name;
        }

        public List<ProtoField> getFields() {
            return fields;
        }

        @Override
        public String toString() {
            return fields.stream()
                    .map(ProtoField::toString)
                    .collect(Collectors.joining(" | ", "(", ")"));
        }

        @Override
        public String toProtobuf(int fieldNumber) {
            String body = IntStream.range(0, fields.size())
                    .mapToObj(i -> fields.get(i).toProtobuf(i + fieldNumber))
                    .collect(Collectors.joining(" "));
            return String.format("oneof %s { %s }", name, body);
        }
    }
}
