package tlc2.diploma.model.type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;

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
    public JsonElement toJson(Value value) {
        if (fromType instanceof TlaNatType) {
            if (value instanceof TupleValue) {
                TupleValue tupleValue = (TupleValue) value;
                JsonArray jsonArray = new JsonArray();
                for (Value elem : tupleValue.elems) {
                    jsonArray.add(toType.toJson(elem));
                }
                return jsonArray;
            } else if (value instanceof FcnRcdValue) {
                FcnRcdValue fcnRcdValue = (FcnRcdValue) value;
                if (fcnRcdValue.intv != null && fcnRcdValue.intv.low >= 0 && fcnRcdValue.intv.low <= 1) {
                    JsonArray jsonArray = new JsonArray();
                    for (Value elem : fcnRcdValue.values) {
                        jsonArray.add(toType.toJson(elem));
                    }
                    return jsonArray;
                }
            }
        }
        return JsonNull.INSTANCE;
    }
}
