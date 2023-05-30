package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import tlc2.value.impl.Value;

import java.util.List;

public class TlaUnionType implements TlaType {
    private final List<TlaType> args;

    public TlaUnionType(List<TlaType> args) {
        this.args = args;
    }

    public List<TlaType> getArgs() {
        return args;
    }

    @Override
    public JsonElement toJson(Value value) {
        return null;
        // попробовать заабузить json_name
        // посмотреть в код для \cup
    }
}
