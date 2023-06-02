package tlc2.diploma.model;

import tlc2.diploma.TlaTypeVisitor;

import java.util.Objects;

public class TlaFunctionType implements TlaType {
    private final TlaType fromType;
    private final TlaType toType;

    public TlaFunctionType(TlaType fromType, TlaType toType) {
        this.fromType = fromType;
        this.toType = toType;
    }

    public TlaType getFromType() {
        return fromType;
    }

    public TlaType getToType() {
        return toType;
    }

    @Override
    public <T, C> T visit(TlaTypeVisitor<T, C> visitor, C context) {
        return visitor.visit(this, context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TlaFunctionType that = (TlaFunctionType) o;
        return Objects.equals(fromType, that.fromType) && Objects.equals(toType, that.toType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromType, toType);
    }
}
