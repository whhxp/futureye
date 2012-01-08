package edu.uta.futureye.lib.shapefun;

import java.util.HashMap;
import java.util.Map;

import edu.uta.futureye.core.Element;
import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FAxpb;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.basic.FX;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.util.container.ObjList;

/**
 * 2D Serendipity 局部坐标形函数
 * 
 * 
 * @author liuyueming
 *
 */
public class SFSerendipity2D extends AbstractFunction implements ScalarShapeFunction {
	private int funIndex;
	private Function funCompose = null;
	private Function funOuter = null;
	private ObjList<String> innerVarNames = null;
	
	private Function jac = null;
	private Function x_r = null;
	private Function x_s = null;
	private Function y_r = null;
	private Function y_s = null;
	
	/**
	 * 构造下列形函数中的一个：
	 *  s
	 *  ^
	 *  |
	 *  |
	 * 
	 *  4--7--3
	 *  |     |
	 *  8     6
	 *  |     |
	 *  1--5--2  --> r
	 * -1  0  1
	 *
	 *for i=1,2,3,4
	 * Ni = (1+r0)*(1+s0)*(r0+s0-1)/4
	 * 
	 *for i=5,6,7,8
	 * Ni = (1-r^2)*(1+s0), when ri=0
	 * Ni = (1+r0)*(1-s^2), when si=0
	 * 
	 *where
	 * r0=r*ri
	 * s0=s*si
	 * 
	 * @param funID = 1,...,8
	 * 
	 */	
	public SFSerendipity2D(int funID) {
		funIndex = funID - 1;
		if(funID<1 || funID>8) {
			System.out.println("ERROR: funID should be 1,...,8.");
			return;
		}
		
		varNames.add("r");
		varNames.add("s");
		innerVarNames = new ObjList<String>("x","y");
		
		//复合函数
		Map<String, Function> fInners = new HashMap<String, Function>(4);
		
		for(final String varName : varNames) {
			fInners.put(varName, new AbstractFunction(innerVarNames.toList()) {
				public Function _d(String var) {
					if(varName.equals("r")) {
						if(var.equals("x"))
							return y_s.D(jac);
						if(var.equals("y"))
							return FC.C0.S(x_s.D(jac));
					} else if(varName.equals("s")) {
						if(var.equals("x"))
							return FC.C0.S(y_r.D(jac));
						if(var.equals("y"))
							return x_r.D(jac);
					}
					return null;
				}
			});
		}
	
		Function fx = new FX("r");
		Function fy = new FX("s");
	
		Function f1mx = new FAxpb("r",-1.0,1.0);
		Function f1px = new FAxpb("r",1.0,1.0);
		Function f1my = new FAxpb("s",-1.0,1.0);
		Function f1py = new FAxpb("s",1.0,1.0);
		
		if(funIndex == 0)
			funOuter = FC.c(-0.25).M(f1mx).M(f1my).M(f1px.A(fy));
		else if(funIndex == 1)
			funOuter = FC.c(-0.25).M(f1px).M(f1my).M(f1mx.A(fy));
		else if(funIndex == 2)
			funOuter = FC.c(-0.25).M(f1px).M(f1py).M(f1mx.S(fy));
		else if(funIndex == 3)
			funOuter = FC.c(-0.25).M(f1mx).M(f1py).M(f1px.S(fy));
		else if(funIndex == 4)
			funOuter = FC.c(0.5).M(f1my).M(FC.C1.S(fx.M(fx)));
		else if(funIndex == 5)
			funOuter = FC.c(0.5).M(f1px).M(FC.C1.S(fy.M(fy)));
		else if(funIndex == 6)
			funOuter = FC.c(0.5).M(f1py).M(FC.C1.S(fx.M(fx)));
		else if(funIndex == 7)
			funOuter = FC.c(0.5).M(f1mx).M(FC.C1.S(fy.M(fy)));

		//使用复合函数构造形函数
		funCompose = funOuter.compose(fInners);		
	}
	
	@Override
	public void asignElement(Element e) {
		Function[] funs = e.getCoordTrans().getJacobianMatrix();
		
		x_r = funs[0];
		x_s = funs[1];
		y_r = funs[2];
		y_s = funs[3];
		jac = e.getCoordTrans().getJacobian();
		
	}

	//TODO ??? 应该采用一维二次型函数
	ScalarShapeFunction sf1d1 = new SFLinearLocal1D(1);
	ScalarShapeFunction sf1d2 = new SFLinearLocal1D(2);
	@Override
	public ScalarShapeFunction restrictTo(int funIndex) {
		if(funIndex == 1) return sf1d1;
		else return sf1d2;
	}

	@Override
	public Function _d(String varName) {
		return funCompose._d(varName);
	}

	@Override
	public double value(Variable v) {
		return funCompose.value(v);
	}

	public String toString() {
		return "N"+(funIndex+1)+"( r,s )="+funOuter.toString();
	}
	
	public static void main(String[] args){
		for(int i=1;i<=8;i++) {
			SFSerendipity2D s = new SFSerendipity2D(i);
			System.out.println(s);
			System.out.println(s._d("r"));
			System.out.println(s._d("s"));
		}
	}

	@Override
	public ObjList<String> innerVarNames() {
		return innerVarNames;
	}
}
