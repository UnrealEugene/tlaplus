package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;

import java.util.List;

public class TlaEnumType implements TlaType {
    private final List<String> elements;

    public TlaEnumType(List<String> elements) {
        this.elements = elements;
    }

    public List<String> getElements() {
        return elements;
    }

    @Override
    public JsonElement toJson(Value value) {
        if (value instanceof StringValue) {
            String stringValue = ((StringValue) value).val.toString();
            if (elements.contains(stringValue)) {
                return new JsonPrimitive(stringValue);
            }
        }
        return JsonNull.INSTANCE;
    }
}
