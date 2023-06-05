package tlc2.diploma.model;

import tlc2.diploma.TlaTypeVisitor;

public class TlaIntType implements TlaType {
    @Override
    public <T, C> T visit(TlaTypeVisitor<T, C> visitor, C context) {
        return visitor.visit(this, context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
