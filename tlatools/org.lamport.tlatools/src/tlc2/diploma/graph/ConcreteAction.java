package tlc2.diploma.graph;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.list.mutable.FastList;
import tla2sany.semantic.ExprOrOpArgNode;
import tla2sany.semantic.FormalParamNode;
import tla2sany.semantic.OpApplNode;
import tla2sany.st.Location;
import tlc2.TLCGlobals;
import tlc2.tool.Action;
import tlc2.tool.TLCState;
import tlc2.tool.impl.Tool;
import tlc2.value.impl.LazyValue;

import java.util.List;

import static tla2sany.semantic.ASTConstants.UserDefinedOpKind;

public class ConcreteAction {
    private final Location declaration;
    private final ImmutableList<Object> args;

    private ConcreteAction(Location declaration, List<Object> args) {
        this.declaration = declaration;
        this.args = new FastList<>(args).toImmutable();
    }

    public static ConcreteAction from(TLCState from, TLCState to, Action action) {
        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        OpApplNode opApplNode = (OpApplNode) action.pred;
        List<Object> args = new FastList<>();
        if (opApplNode.getOperator().getKind() == UserDefinedOpKind) {
            ExprOrOpArgNode[] nodeArgs = opApplNode.getArgs();
            for (ExprOrOpArgNode arg : nodeArgs) {
                Object val = tool.getVal(arg, action.con, false);
                if (val instanceof LazyValue) {
                    val = ((LazyValue) val).eval(tool, from, to);
                }
                args.add(val);
            }
        } else {
            FormalParamNode[] params = action.getOpDef().getParams();
            for (FormalParamNode param : params) {
                args.add(tool.lookup(param, action.con, false));
            }
        }
        return new ConcreteAction(action.getDeclaration(), args);
    }

    public Location getDeclaration() {
        return declaration;
    }

    public List<Object> getArgs() {
        return args.castToList();
    }
}