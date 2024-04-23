package tlc2.util;

import tlc2.tool.Action;
import tlc2.tool.TLCState;

public class NoopTraceWriter implements ITraceWriter {
    @Override
    public void init() {
        // No operation
    }

    @Override
    public void writeTraceInitState(TLCState state) {
        // No operation
    }

    @Override
    public void writeTraceAction(TLCState state, TLCState successor, Action action) {
        // No operation
    }

    @Override
    public void writeTraceEnd() {
        // No operation
    }

    @Override
    public void close() {
        // No operation
    }
}
