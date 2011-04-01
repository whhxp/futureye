package edu.uta.futureye.function.operator;

import java.util.List;
import java.util.Map;

import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.basic.SpaceVectorFunction;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.VectorFunction;
import edu.uta.futureye.util.FutureyeException;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.ObjList;

public class FMath {
	public static Function Plus(final Function f1, final Function f2) {
		if(f1 instanceof FC && Double.compare(f1.value(null), 0.0)==0)
			return f2;
		else if(f2 instanceof FC && Double.compare(f2.value(null), 0.0)==0)
			return f1;
		else
			return new AbstractFunction(Utils.mergeList(f1.varNames(), f2.varNames())) {
				@Override
				public double value(Variable v) {
					return f1.value(v) + f2.value(v);
				}
				@Override
				public Function _d(String varName) {
					return Plus(f1._d(varName),f2._d(varName));
				}
				public String toString() {
					return "("+f1.toString()+"  +  "+f2.toString()+")";
				}
			};
	}
	
	public static Function PlusAll(final Function ...f) {
		if(f.length<=1) {
			FutureyeException e = new FutureyeException("Parameter length should > 1");
			e.printStackTrace();
			return null;
		}
		List<String> names = f[0].varNames();
		for(int i=1;i<f.length;i++) {
			names = Utils.mergeList(names, f[i].varNames());
		}
		return new AbstractFunction(names) {
			@Override
			public double value(Variable v) {
				double val = f[0].value(v);
				for(int i=1;i<f.length;i++) 
					val += f[i].value(v);
				return val;
			}
			@Override
			public Function _d(String varName) {
				throw new UnsupportedOperationException();
			}
			public String toString() {
				String name = "("+f[0].toString();
				for(int i=1;i<f.length;i++) 
					name += " + "+f[i].toString();
				name += ")";
				return name;
			}
		};
	}
	
	public static Function Minus(final Function f1, final Function f2) {
		if(f1 instanceof FC && Double.compare(f1.value(null), 0.0)==0 &&
				f2 instanceof FC)
			return new FC( - f2.value(null) );
		else if(f2 instanceof FC && Double.compare(f2.value(null), 0.0)==0)
			return f1;
		else
			return new AbstractFunction(Utils.mergeList(f1.varNames(), f2.varNames())) {
				@Override
				public double value(Variable v) {
					return f1.value(v) - f2.value(v);
				}
				@Override
				public Function _d(String varName) {
					return Minus(f1._d(varName),f2._d(varName));
				}
				public String toString() {
					if(f1 instanceof FC && Double.compare(f1.value(null), 0.0)==0)
						return "  -  "+f2.toString();
					return "("+f1.toString()+"  -  "+f2.toString()+")";
				}
			};
	}
	
	public static Function Mult(final Function f1, final Function f2) {
		if( (f1 instanceof FC && Double.compare(f1.value(null), 0.0)==0) ||
				f2 instanceof FC && Double.compare(f2.value(null), 0.0)==0)
			return new FC(0.0);
		else if(f1 instanceof FC && Double.compare(f1.value(null), 1.0)==0)
			return f2;
		else if(f2 instanceof FC && Double.compare(f2.value(null), 1.0)==0)
			return f1;
		else
			return new AbstractFunction(Utils.mergeList(f1.varNames(), f2.varNames())) {
				@Override
				public double value(Variable v) {
					return f1.value(v) * f2.value(v);
				}
				@Override
				public Function _d(String varName) {
					return Plus(
							Mult(f1._d(varName),f2),
							Mult(f1,f2._d(varName))
							);
				}
				public String toString() {
					return "("+f1.toString()+"  <*>  "+f2.toString()+")";
				}
			};
	}
	
	public static Function Divi(final Function f1, final Function f2) {
		if( (f1 instanceof FC && Double.compare(f1.value(null), 0.0)==0))
			return new FC(0.0);
		return new AbstractFunction(Utils.mergeList(f1.varNames(), f2.varNames())) {
			@Override
			public double value(Variable v) {
				return f1.value(v) / f2.value(v);
			}
			@Override
			public Function _d(String varName) {
				return Plus(
						Mult(f1._d(varName),f2),
						Mult(f1,f2._d(varName))
						);
			}
			public String toString() {
				return "("+f1.toString()+"  </>  "+f2.toString()+")";
			}
		};
	}
	
	public static Function LinearCombination(final double coef1, final Function f1,
			final double coef2,final Function f2) {
		return new AbstractFunction(Utils.mergeList(f1.varNames(), f2.varNames())) {
			@Override
			public double value(Variable v) {
				return coef1*f1.value(v) + coef2 * f2.value(v);
			}
			@Override
			public Function _d(String varName) {
				return Plus(
						Mult(new FC(coef1),f1._d(varName)),
						Mult(new FC(coef2),f2._d(varName))
						);
			}
			public String toString() {
				return "("+coef1+"*("+f1.toString()+")  +  "+coef2+"*("+f2.toString()+"))";
			}
		};
	}
	
	/**
	 * 复合函数
	 * @param e.g. fOuter f = f(x,y)
	 * @param e.g. fInners x = x(r,s), y = y(r,s)
	 * @return e.g. f = f(x,y) = f( x(r,s),y(r,s) )
	 */
	public static Function Compose(final Function fOuter, final Map<String,Function> fInners) {
		return new AbstractFunction(fOuter.varNames()) {
			@Override
			public double value(Variable v) {
				//bugfix 增加"或条件"
				if(fOuter.varNames().containsAll(v.getValues().keySet()) ||
						v.getValues().keySet().containsAll(fOuter.varNames())) {
					return fOuter.value(v);
				} else if(fOuter.varNames().size() == fInners.size()){
					Variable newVar = new Variable();
					for(String varName : fOuter.varNames()) {
						Function f = fInners.get(varName);
						if(f != null ) {
							newVar.set(varName, f.value(v));
						}
						else {
							FutureyeException e = new FutureyeException("ERROR: Can not find "+varName+" in fInners.");
							e.printStackTrace();
							System.exit(0);
							return 0.0;
						}
					}
					return fOuter.value(newVar);
				} else {
					FutureyeException e = new FutureyeException(
							"ERROR: Variable Number Mismatch of fOuter("+
							fOuter.varNames()+") and fInner("+fInners+").");
					e.printStackTrace();
					System.exit(0);
				}
				return 0.0;
			}
			/**
			 * 链式求导
			 * f( x(r,s),y(r,s) )_r = f_x * x_r + f_y * y_r
			 */
			@Override
			public Function _d(String varName) {
				Function rlt = null;
				if(fOuter.varNames().contains(varName)) {
					//f(x,y)关于x或y求导
					rlt = fOuter._d(varName);
					return rlt;
				} else {
					//f(x,y)关于r或s求导
					rlt = new FC(0.0);
					for(String innerVarName : fOuter.varNames()) {
						Function fInner = fInners.get(innerVarName);
						if(fInner != null) {
							Function rltOuter = fOuter._d(innerVarName);
							Function rltInner = fInner._d(varName);
							//f_x * x_r + f_y * y_r
							rlt = Plus(rlt, Mult(rltOuter,rltInner));
						}
					}
					return rlt;
				}
			}
			public String toString() {
				//return "("+fOuter.toString()+"  ,  "+fInners.toString()+")";
				return "("+fOuter.toString()+")";
			}
		};
	}
	
	public static Function Sqrt(final Function f) {
		return new AbstractFunction(f.varNames()) {
			@Override
			public double value(Variable v) {
				return Math.sqrt(f.value(v));
			}
			@Override
			public Function _d(String varName) {
				//TODO
				return null;
			}
			public String toString() {
				return "Sqrt("+f.toString()+")";
			}
			
		};
	}
	
	public static Function Abs(final Function f) {
		return new AbstractFunction(f.varNames()) {
			@Override
			public double value(Variable v) {
				return Math.abs(f.value(v));
			}
			@Override
			public Function _d(String varName) {
				//TODO
				return null;
			}
			public String toString() {
				return "Abs("+f.toString()+")";
			}
		};
	}
	
	public static VectorFunction Grad(Function fun) {
		List<String> names = fun.varNames();
		VectorFunction rlt = new SpaceVectorFunction(names.size());
		for(int i=0;i<names.size();i++)
			rlt.set(i+1, fun._d(names.get(i)));
		return rlt;
	}
	
	public static VectorFunction Grad(Function fun,ObjList<String> varNames) {
		VectorFunction rlt = new SpaceVectorFunction(varNames.size());
		for(int i=1;i<=varNames.size();i++)
			rlt.set(i, fun._d(varNames.at(i)));
		return rlt;
	}
	
	public static Function Div(VectorFunction vFun) {
		int dim = vFun.getDim();
		Function rlt = FC.c0;
		for(int i=1; i<=dim; i++) {
			Function fd = (Function)vFun.get(i);
			rlt = rlt.A(fd._d(vFun.varNames().get(i-1)));
		}
		return rlt;
	}
	
	public static Function Div(VectorFunction vFun,ObjList<String> varNames) {
		Function rlt = new FC(0.0);
		for(int i=1;i<=varNames.size();i++) {
			Function fd = (Function)vFun.get(i);
			rlt = FMath.Plus(rlt, fd._d(varNames.at(i)));
		}
		return rlt;
	}
	
	public static Function Curl(VectorFunction vFun) {
		//TODO
		return null;
	}
	
	public static Vector log(Vector v) {
		Vector v2 = v.copy();
		for(int i=1;i<=v.getDim();i++) {
			v2.set(i,Math.log(v.get(i)));
		}
		return v2;
	}
	
	public static Vector abs(Vector v) {
		Vector v2 = v.copy();
		for(int i=1;i<=v.getDim();i++) {
			v2.set(i,Math.abs(v.get(i)));
		}
		return v2;
	}
}
