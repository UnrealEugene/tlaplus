package tlc2.diploma;

import tlc2.diploma.model.*;

import java.util.*;

public class TlaTypeToGoVisitor implements TlaTypeVisitor<String, TlaTypeToGoVisitor.Context> {
    static class Context {
        private final List<String> fieldNameStack;
        private final Map<TlaType, String> typeToName;

        Context() {
            fieldNameStack = new ArrayList<>();
            typeToName = new HashMap<>();
            fieldNameStack.add("modelState");
        }

        public String popName() {
            int backIndex = fieldNameStack.size() - 1;
            String name = fieldNameStack.get(backIndex);
            fieldNameStack.remove(backIndex);
            return name;
        }

        public String popNameExported() {
            return upperFirstChar(this.popName());
        }

        public void pushName(String name) {
            fieldNameStack.add(name);
        }

        public boolean addTypeName(TlaType type, String typeName) {
            return typeToName.put(type, typeName) == null;
        }

        public String getTypeName(TlaType type) {
            return typeToName.get(type);
        }
    }

    private static String upperFirstChar(String str) {
        if (str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    @Override
    public String visit(TlaType type) {
        return type.visit(this, new Context());
    }

    public String visit(TlaType type, Context context) {
        return type.visit(this, context);
    }

    @Override
    public String visit(TlaAnyType type, Context context) {
        context.popName();
        context.addTypeName(type, "any");
        return "";
    }

    @Override
    public String visit(TlaBoolType type, Context context) {
        context.popName();
        context.addTypeName(type, "bool");
        return "";
    }

    @Override
    public String visit(TlaEnumType type, Context context) {
        String name = context.popNameExported();
        if (context.addTypeName(type, name)) {
            StringBuilder sb = new StringBuilder();
            for (String elemName : type.getElements()) {
                sb.append(String.format("%s_%s %s = \"%s\";", name, upperFirstChar(elemName), name, elemName));
            }
            return String.format("type %s string;const(%s);", name, sb);
        }
        return "";
    }

    @Override
    public String visit(TlaFunctionType type, Context context) {
        String name = context.popName();

        TlaType fromType = type.getFromType();
        context.pushName(name + "Key");
        String defFrom = this.visit(fromType, context);

        TlaType toType = type.getToType();
        context.pushName(name + "Value");
        String defTo = this.visit(toType, context);

        context.addTypeName(type, String.format("map[%s]%s", context.getTypeName(fromType), context.getTypeName(toType)));
        return defFrom + defTo;
    }

    @Override
    public String visit(TlaIntType type, Context context) {
        context.popName();
        context.addTypeName(type, "int");
        return "";
    }

    @Override
    public String visit(TlaNatType type, Context context) {
        context.popName();
        context.addTypeName(type, "uint");
        return "";
    }

    @Override
    public String visit(TlaRecordType type, Context context) {
        String structName = context.popNameExported();

        if (context.addTypeName(type, structName)) {
            StringBuilder defs = new StringBuilder();
            StringBuilder body = new StringBuilder();
            Map<String, TlaType> fields = type.getFields();
            for (Map.Entry<String, TlaType> entry : fields.entrySet()) {
                String fieldName = entry.getKey();
                TlaType fieldType = entry.getValue();

                context.pushName(fieldName);
                String defField = this.visit(fieldType, context);
                defs.append(defField);
                body.append(String.format("%s %s `json:\"%s\"`;", upperFirstChar(fieldName),
                        context.getTypeName(fieldType), fieldName));
            }
            return String.format("%stype %s struct{%s};", defs, structName, body);
        }
        return "";
    }

    @Override
    public String visit(TlaSetOfType type, Context context) {
        return this.visit(new TlaSeqType(type.getInnerType()), context);
    }

    @Override
    public String visit(TlaSeqType type, Context context) {
        String name = context.popName();
        TlaType innerType = type.getInnerType();
        context.pushName(name + "Entry");
        String defInner = this.visit(innerType, context);
        context.addTypeName(type, String.format("[]%s", context.getTypeName(innerType)));
        return defInner;
    }
}
