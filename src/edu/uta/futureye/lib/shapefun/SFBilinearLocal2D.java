package edu.uta.futureye.lib.shapefun;

import java.util.HashMap;
import java.util.Map;

import edu.uta.futureye.core.CoordinateTransform;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.VariableArray;
import edu.uta.futureye.function.basic.FAxpb;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.util.Constant;
import edu.uta.futureye.util.FutureyeException;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.ObjList;
import edu.uta.futureye.util.container.VertexList;
import static edu.uta.futureye.function.operator.FMath.*;

public class SFBilinearLocal2D extends AbstractFunction implements ScalarShapeFunction {
	private int funIndex;
	private Function funCompose = null;
	private Function funOuter = null;
	private ObjList<String> innerVarNames = null;
	private double coef = 1.0;

	Element e;
	CoordinateTransform trans = new CoordinateTransform(2);
	Function jac = null;
	Function x_r = null;
	Function x_s = null;
	Function y_r = null;
	Function y_s = null;
	
	
	/**
	 * 构造下列形函数中的一个：
	 *  s
	 *  ^
	 *  |
	 *  |
	 * 
	 *  4-----3
	 *  |     |
	 *  |     |
	 *  1-----2  --> r
	 * -1  0  1
	 *
	 * N1 = (1-r)*(1-s)/4
	 * N2 = (1+r)*(1-s)/4
	 * N3 = (1+r)*(1+s)/4
	 * N4 = (1-r)*(1+s)/4
	 * @param funID = 1,2,3,4
	 * 
	 */	
	public void Create(int funID,double coef) {
		funIndex = funID - 1;
		if(funID<1 || funID>4) {
			System.out.println("ERROR: funID should be 1,2,3 or 4.");
			return;
		}
		
		varNames.add("r");
		varNames.add("s");
		innerVarNames = new ObjList<String>("x","y");
		
		//复合函数
		Map<String, Function> fInners = new HashMap<String, Function>(3);
		
		//r = r(x,y)
		//s = s(x,y)
		for(final String varName : varNames) {
			fInners.put(varName, new AbstractFunction(innerVarNames.toList()) {
				
/**
How to get derivative r_x, r_y, s_x, s_y:

f(x,y) = g(r,s)
f_x = g_r*r_x + g_s*s_x  ---(1)
f_y = g_r*r_y + g_s*s_y  ---(2)

for (1), let f=x and f=y we get tow equations, solve them:
(x_r x_s)   (r_x)   (1)
(y_r y_s) * (s_x) = (0)

similarly, for (2):
(x_r x_s)   (r_y)   (0)
(y_r y_s) * (s_y) = (1)

        (x_r x_s)
Let J = (y_r y_s)

from the above four equations, we have:
 (r_x r_y)
 (s_x s_y) = inv(J)
 */				
				//Derivatives: r_x, r_y, s_x, s_y
				public Function _d(String var) {
					if(varName.equals("r")) {
						if(var.equals("x")) //r_x
							return y_s.D(jac);
						else //r_y
							return C0.S(x_s.D(jac));
					} else if(varName.equals("s")) {
						if(var.equals("x")) //s_x
							return C0.S(y_r.D(jac));
						else //s_y
							return x_r.D(jac);
					}
					return null;
				}
			});
		}
		
		if(funIndex == 0)
			funOuter = new FAxpb("r",-0.5,0.5).M(
					new FAxpb("s",-0.5,0.5));
		else if(funIndex == 1)
			funOuter = new FAxpb("r",0.5,0.5).M( 
					new FAxpb("s",-0.5,0.5));
		else if(funIndex == 2)
			funOuter = new FAxpb("r",0.5,0.5).M( 
					new FAxpb("s",0.5,0.5));
		else if(funIndex == 3)
			funOuter = new FAxpb("r",-0.5,0.5).M( 
					new FAxpb("s",0.5,0.5));
		
		//使用复合函数构造形函数
		this.coef = coef;
		funCompose = FC.c(this.coef).M(
					funOuter.compose(fInners)
				);
	}

	public SFBilinearLocal2D(int funID,double coef) {
		Create(funID,coef);
	}
	
	public SFBilinearLocal2D(int funID) {
		Create(funID,1.0);
	}

	public Function _d(String varName) {
		return funCompose._d(varName);
	}

	public double value(Variable v) {
		return funCompose.value(v);
	}
	
	@Override
	public double[] valueArray(VariableArray v, Map<Object,Object> cache) {
		return funCompose.valueArray(v,cache);
	}

	@Override
	public void assignElement(Element e) {
		this.e = e;
		VertexList vList = e.vertices();
	
//		//从_d()移动到这里，速度又快一倍
//		//Coordinate transform and Jacbian on element e
//		List<Function> funs = trans.getTransformFunction(
//				trans.getTransformLinear2DShapeFunction(e)
//				);
//		trans.setTransformFunction(funs);
//		Function fx = funs.get(0); //x=x(r,s)
//		Function fy = funs.get(1); //y=y(r,s)
//		x_r = fx._d("r");
//		x_s = fx._d("s");
//		y_r = fy._d("r");
//		y_s = fy._d("s");
		
		Function [] funs = e.getCoordTrans().getJacobianMatrix();
		x_r = funs[0];
		x_s = funs[1];
		y_r = funs[2];
		y_s = funs[3];
		//用面积计算Jacobin，速度要快一倍
		double area = Utils.getRectangleArea(vList)/4.0;
		if(Math.abs(area)<Constant.eps) throw new FutureyeException();
		jac = FC.c(area);
	}

	public String toString() {
		if(this.coef < 1.0)
			return "N"+(funIndex+1)+": "+this.coef+"*"+funOuter.toString();
		else
			return "N"+(funIndex+1)+": "+funOuter.toString();
			
	}

	ScalarShapeFunction sf1d1 = new SFLinearLocal1D(1);
	ScalarShapeFunction sf1d2 = new SFLinearLocal1D(2);
	@Override
	public ScalarShapeFunction restrictTo(int funIndex) {
		if(funIndex == 1) return sf1d1;
		else return sf1d2;
	}

	@Override
	public ObjList<String> innerVarNames() {
		return innerVarNames;
	}
}
