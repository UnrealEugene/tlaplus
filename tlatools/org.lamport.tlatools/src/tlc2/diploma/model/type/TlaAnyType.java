package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import tlc2.module.Json;
import tlc2.value.impl.Value;

import java.io.IOException;
import java.io.UncheckedIOException;

public class TlaAnyType implements TlaType {
    @Override
    public JsonElement toJson(Value value) {
        try {
            return JsonParser.parseString(Json.toJson(value).getVal().toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
