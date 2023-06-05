package tlc2.diploma.model;

import tlc2.diploma.TlaTypeVisitor;

import java.util.Map;
import java.util.Objects;

public class TlaRecordType implements TlaType {
    private final Map<String, TlaType> fields;

    public TlaRecordType(Map<String, TlaType> fields) {
        this.fields = fields;
    }

    public Map<String, TlaType> getFields() {
        return fields;
    }

    @Override
    public <T, C> T visit(TlaTypeVisitor<T, C> visitor, C context) {
        return visitor.visit(this, context);
    }

    public static class Field {
        private final TlaType type;
        private final String name;

        public Field(TlaType type, String name) {
            this.type = type;
            this.name = name;
        }

        public TlaType getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Field field = (Field) o;
            return Objects.equals(type, field.type) && Objects.equals(name, field.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, name);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TlaRecordType that = (TlaRecordType) o;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }
}
