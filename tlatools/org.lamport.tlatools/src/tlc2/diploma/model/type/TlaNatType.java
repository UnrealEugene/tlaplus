package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.Value;

public class TlaNatType implements TlaType {
    @Override
    public JsonElement toJson(Value value) {
        if (value instanceof IntValue) {
            int intValue = ((IntValue) value).val;
            if (intValue >= 0) {
                return new JsonPrimitive(intValue);
            }
        }
        return JsonNull.INSTANCE;
    }
}
