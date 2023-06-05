package tlc2.diploma.model;

import tlc2.diploma.TlaTypeVisitor;

import java.util.Objects;

public class TlaSetOfType implements TlaType {
    private final TlaType innerType;

    public TlaSetOfType(TlaType innerType) {
        this.innerType = innerType;
    }

    public TlaType getInnerType() {
        return innerType;
    }

    @Override
    public <T, C> T visit(TlaTypeVisitor<T, C> visitor, C context) {
        return visitor.visit(this, context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TlaSetOfType that = (TlaSetOfType) o;
        return Objects.equals(innerType, that.innerType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(innerType);
    }
}
