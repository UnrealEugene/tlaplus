package tlc2.diploma.model;

import tlc2.diploma.TlaTypeVisitor;

public interface TlaType {
    <T, C> T visit(TlaTypeVisitor<T, C> visitor, C context);
}
