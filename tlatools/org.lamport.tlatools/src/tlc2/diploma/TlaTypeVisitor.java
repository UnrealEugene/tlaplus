package tlc2.diploma;

import tlc2.diploma.model.*;

public interface TlaTypeVisitor<T, C> {
    T visit(TlaType type);
    T visit(TlaAnyType type, C context);
    T visit(TlaBoolType type, C context);
    T visit(TlaEnumType type, C context);
    T visit(TlaFunctionType type, C context);
    T visit(TlaIntType type, C context);
    T visit(TlaNatType type, C context);
    T visit(TlaRecordType type, C context);
    T visit(TlaSetOfType type, C context);
    T visit(TlaSeqType type, C context);
}
