package tlc2.diploma.model.type;

import com.google.gson.JsonElement;
import tlc2.value.impl.Value;

public interface TlaType {
    JsonElement toJson(Value value);
//    String toProtobufDecl();
}
