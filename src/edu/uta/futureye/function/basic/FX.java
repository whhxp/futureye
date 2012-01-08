package edu.uta.futureye.function.basic;

import java.util.Map;

import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.VariableArray;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.util.Constant;

/**
 * f(x)=x
 * 
 * @author liuyueming
 *
 */
public class FX extends AbstractFunction{
	/**
	 * Used to form f(x)=x, instead of construct a new FX object, 
	 * it will be faster and memory saving :)
	 */
	public final static FX fx = new FX(Constant.x); 
	
	/**
	 * Different variable names
	 */
	public final static FX fy = new FX(Constant.y); 
	public final static FX fz = new FX(Constant.z); 
	
	public final static FX fr = new FX(Constant.r); 
	public final static FX fs = new FX(Constant.s); 
	public final static FX ft = new FX(Constant.t); 
	
	protected String varName = null;
	
	/**
	 * Use this to construct f(varName)
	 */
	public FX(String varName) {
		super(varName);
		this.varName = varName;
	}

	@Override
	public Function _d(String varName) {
		if(this.varName.equals(varName))
			return FC.C1;
		else
			return FC.C0;
	}

	@Override
	public double value(Variable v) {
		return v.get(varName);
	}
	
	@Override
	public double value(Variable v, Map<Object,Object> cache) {
		return v.get(varName);
	}
	
	@Override
	public double[] valueArray(VariableArray v, Map<Object,Object> cache) {
		return v.get(varName);
	}
	
	@Override
	public int getOpOrder() {
		return OP_ORDER0;
	}
	
	@Override
	public Function copy() {
		return new FX(this.varName);
	}
	
	@Override
	public String toString() {
		return varName;
	}
}
