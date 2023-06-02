package tlc2.diploma.model;

import tlc2.diploma.TlaTypeVisitor;

import java.util.List;
import java.util.Objects;

public class TlaEnumType implements TlaType {
    private final List<String> elements;

    public TlaEnumType(List<String> elements) {
        this.elements = elements;
    }

    public List<String> getElements() {
        return elements;
    }

    @Override
    public <T, C> T visit(TlaTypeVisitor<T, C> visitor, C context) {
        return visitor.visit(this, context);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TlaEnumType that = (TlaEnumType) o;
        return Objects.equals(elements, that.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }
}
