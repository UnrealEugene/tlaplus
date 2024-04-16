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
import tlc2.util.FP64;
import tlc2.value.IValue;
import tlc2.value.impl.LazyValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.Value;

import java.util.List;

import static tla2sany.semantic.ASTConstants.UserDefinedOpKind;

public class ConcreteAction {
    private final Location declaration;
    private final ImmutableList<IValue> args;

    private ConcreteAction(Location declaration, List<IValue> args) {
        this.declaration = declaration;
        this.args = new FastList<>(args).toImmutable();
    }

    public static ConcreteAction from(TLCState from, TLCState to, Action action) {
        Tool tool = (Tool) TLCGlobals.mainChecker.tool;
        OpApplNode opApplNode = (OpApplNode) action.pred;
        List<IValue> args = new FastList<>();
        if (opApplNode.getOperator().getKind() == UserDefinedOpKind) {
            ExprOrOpArgNode[] nodeArgs = opApplNode.getArgs();
            for (ExprOrOpArgNode arg : nodeArgs) {
                Object val = tool.getVal(arg, action.con, false);
                if (val instanceof LazyValue) {
                    args.add(((LazyValue) val).eval(tool, from, to));
                } else if (val instanceof IValue) {
                    args.add((IValue) val);
                } else {
                    args.add(new StringValue(val.toString()));
                }
            }
        } else {
            FormalParamNode[] params = action.getOpDef().getParams();
            for (FormalParamNode param : params) {
                Object val = tool.lookup(param, action.con, false);
                if (val instanceof IValue) {
                    args.add((IValue) val);
                } else {
                    args.add(new StringValue(val.toString()));
                }
            }
        }
        for (IValue arg : args) {
            arg.deepNormalize();
        }
        return new ConcreteAction(action.getDeclaration(), args);
    }

    public Location getDeclaration() {
        return declaration;
    }

    public List<IValue> getArgs() {
        return args.castToList();
    }

    public long fingerPrint() {
        long fp = FP64.New();
        for (IValue arg : args) {
            fp = arg.fingerPrint(fp);
        }
        return FP64.Extend(fp, declaration.hashCode());
    }
}