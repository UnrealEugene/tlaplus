package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.Value;
import util.UniqueString;

import java.util.List;
import java.util.Map;

public class TlaRecordType implements TlaType {
    private final Map<String, TlaType> fields;

    public TlaRecordType(Map<String, TlaType> fields) {
        this.fields = fields;
    }

    public Map<String, TlaType> getFields() {
        return fields;
    }

    @Override
    public JsonElement toJson(Value value) {
        if (value instanceof RecordValue) {
            JsonObject jsonObject = new JsonObject();
            RecordValue recordValue = (RecordValue) value;
            for (int i = 0; i < recordValue.names.length; i++) {
                String fieldName = recordValue.names[i].toString();
                if (!fields.containsKey(fieldName)) {
                    return JsonNull.INSTANCE;
                }
                Value fieldValue = recordValue.values[i];
                jsonObject.add(fieldName, fields.get(fieldName).toJson(fieldValue));
            }
            return jsonObject;
        }
        return JsonNull.INSTANCE;
    }

    public static class Field {
        private final TlaType type;
        private final String name;

        public Field(TlaType type, String name) {
            this.type = type;
            this.name = name;
        }

        public TlaType getType() {
            return type;
        }

        public String getName() {
            return name;
        }
    }
}
