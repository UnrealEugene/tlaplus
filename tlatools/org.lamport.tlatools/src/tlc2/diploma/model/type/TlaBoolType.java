package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.Value;

public class TlaBoolType implements TlaType {
    @Override
    public JsonElement toJson(Value value) {
        if (value instanceof BoolValue) {
            return new JsonPrimitive(((BoolValue) value).val);
        }
        return JsonNull.INSTANCE;
    }
}
