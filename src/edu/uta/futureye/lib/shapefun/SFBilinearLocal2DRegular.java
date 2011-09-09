package edu.uta.futureye.lib.shapefun;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uta.futureye.core.CoordinateTransform;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FAxpb;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.operator.FMath;
import edu.uta.futureye.util.Constant;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.ObjList;
import edu.uta.futureye.util.container.VertexList;

/**
 * 适用于各边与坐标轴平行的四边形单元
 * 针对该特殊情形，简化了计算，可以快速完成关于x,y物理坐标的导数计算
 * 
 * @author liuyueming
 *
 */
public class SFBilinearLocal2DRegular extends AbstractFunction implements ScalarShapeFunction {
	private int funIndex;
	private Function funCompose = null;
	private Function funOuter = null;
	private ObjList<String> innerVarNames = null;
	private double coef = 1.0;

	private Element e;
	private double jacFast = 0.0;
	
	
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
		Map<String, Function> fInners = new HashMap<String, Function>(4);
		
		for(final String varName : varNames) {
			fInners.put(varName, new AbstractFunction(innerVarNames.toList()) {
				
				public Function _d(String var) {
					double x1 = e.nodes.at(1).coord(1);
					double x2 = e.nodes.at(2).coord(1);
					double x3 = e.nodes.at(3).coord(1);
					double x4 = e.nodes.at(4).coord(1);
					double y1 = e.nodes.at(1).coord(2);
					double y2 = e.nodes.at(2).coord(2);
					double y3 = e.nodes.at(3).coord(2);
					double y4 = e.nodes.at(4).coord(2);
	/**
  	 *  4--3  Case1
	 *  |  |
	 *  1--2
	 * x = x1*N1 + x2*N2 + x2*N3 + x1*N4 = x1*(N1+N4) + x2*(N2+N3) = (x1*2*(1-r) + x2*2*(1+r))/4
	 * y = y1*N1 + y1*N2 + y3*N3 + y3*N4 = y1*(N1+N2) + y3*(N3+N4) = (y1*2*(1-s) + y3*2*(1+s))/4
	 * x_r = (x2 - x1)/2,  x_s = 0
	 * y_r = 0,            y_s = (y3 - y1)/2 
	 * 
  	 *  3--2  Case2
	 *  |  |
	 *  4--1
	 * x = x1*N1 + x1*N2 + x3*N3 + x3*N4 = x1*(N1+N2) + x2*(N3+N4) = (x1*2*(1-s) + x3*2*(1+s))/4
	 * y = y1*N1 + y2*N2 + y2*N3 + y1*N4 = y1*(N1+N4) + y2*(N2+N3) = (y1*2*(1-r) + y2*2*(1+r))/4
	 * x_r = 0,            x_s = (x3 - x1)/2
	 * y_r = (y2 - y1)/2,  y_s = 0
	 * 
  	 *  2--1  Case3
	 *  |  |
	 *  3--4
	 * x = x1*N1 + x2*N2 + x2*N3 + x1*N4 = x1*(N1+N4) + x2*(N2+N3) = (x1*2*(1-r) + x2*2*(1+r))/4
	 * y = y1*N1 + y1*N2 + y3*N3 + y3*N4 = y1*(N1+N2) + y2*(N3+N4) = (y1*2*(1-s) + y3*2*(1+s))/4
	 * x_r = (x2 - x1)/2,  x_s = 0
	 * y_r = 0,            y_s = (y3 - y1)/2
	 * 
  	 *  1--4  Case4
	 *  |  |
	 *  2--3
	 * x = x1*N1 + x1*N2 + x3*N3 + x3*N4 = x1*(N1+N2) + x3*(N3+N4) = (x1*2*(1-s) + x3*2*(1+s))/4
	 * y = y1*N1 + y2*N2 + y2*N3 + y1*N4 = y1*(N1+N4) + y2*(N2+N3) = (y1*2*(1-r) + y2*2*(1+r))/4
	 * x_r = 0,            x_s = (x3 - x1)/2
	 * y_r = (y2 - y1)/2,  y_s = 0
	 */
					double x_r,x_s,y_r,y_s;
					if(Math.abs(y1-y2)<Constant.eps) {
						x_r = (x2-x1)/2.0;
						x_s = 0.0;
						y_r = 0.0;
						y_s = (y3-y1)/2.0;
					} else {
						x_r = 0.0;
						x_s = (x3-x1)/2.0;
						y_r = (y2-y1)/2.0;
						y_s = 0.0;
					}
					
					if(varName.equals("r")) {
						if(var.equals("x"))
							return new FC(y_s/jacFast);
						if(var.equals("y"))
							return FC.c0.S(x_s/jacFast);
					} else if(varName.equals("s")) {
						if(var.equals("x"))
							return FC.c0.S(y_r/jacFast);
						if(var.equals("y"))
							return new FC(x_r/jacFast);
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

	public SFBilinearLocal2DRegular(int funID,double coef) {
		Create(funID,coef);
	}
	
	public SFBilinearLocal2DRegular(int funID) {
		Create(funID,1.0);
	}

	public Function _d(String varName) {
		return funCompose._d(varName);
	}

	public double value(Variable v) {
		return funCompose.value(v);
	}

	@Override
	public void asignElement(Element e) {
		this.e = e;
		VertexList vList = e.vertices();
		//用面积计算Jacobin
		jacFast = Utils.getRectangleArea(vList)/4.0;
		
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
