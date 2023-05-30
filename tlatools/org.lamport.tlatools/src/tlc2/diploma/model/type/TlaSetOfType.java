package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import tlc2.value.impl.Value;

public class TlaSetOfType implements TlaType {
    private final TlaType innerType;

    public TlaSetOfType(TlaType innerType) {
        this.innerType = innerType;
    }

    public TlaType getInnerType() {
        return innerType;
    }

    @Override
    public JsonElement toJson(Value value) {
        throw new UnsupportedOperationException();
    }
}
