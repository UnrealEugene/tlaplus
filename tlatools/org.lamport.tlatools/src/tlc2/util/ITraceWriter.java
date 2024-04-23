package tlc2.util;

import tlc2.tool.Action;
import tlc2.tool.TLCState;

public interface ITraceWriter {
    void writeTraceInitState(TLCState state);

    void writeTraceAction(TLCState state, TLCState successor, Action action);

    void writeTraceEnd();

    void close();
}
