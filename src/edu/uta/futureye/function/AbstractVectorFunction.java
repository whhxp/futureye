package edu.uta.futureye.function;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.uta.futureye.algebra.SpaceVector;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.basic.SpaceVectorFunction;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.VectorFunction;
import edu.uta.futureye.util.FutureyeException;
import edu.uta.futureye.util.Utils;

/**
 * Abstract vector function implementation.
 * <p>
 * Notice: Some member functions such as algebra operations will return 
 * an instance of SpaceVectorFunction which extends from AbstractVectorFunction
 * 
 * @author liuyueming
 *
 */
public abstract class AbstractVectorFunction implements VectorFunction {
	protected int dim = 0;
	protected List<String> varNames = new LinkedList<String>();
	protected String fName = null;
	
	public AbstractVectorFunction() {
	}
	
	public AbstractVectorFunction(int dim) {
		this.dim = dim;
	}
	
	public AbstractVectorFunction(int dim, List<String> varNames) {
		this.dim = dim;
		this.varNames = varNames;
	}
	
	public AbstractVectorFunction(int dim, String varName, String ...aryVarNames) {
		this.dim = dim;
		varNames.add(varName);
		for(String s : aryVarNames)
			varNames.add(s);
	}
	
	@Override
	public Vector value(Variable v) {
		Vector rlt = new SpaceVector(dim);
		for(int i=1; i<=dim; i++)
			rlt.set(i, this.get(i).value(v));
		return rlt;
	}
	
	@Override
	public Vector[] valueArray(VariableArray valAry, Map<Object,Object> cache) {
		int len = valAry.length();
		Vector[] rlt = new SpaceVector[len];
		for(int j=0;j<len;j++) rlt[j] = new SpaceVector(dim);
		for(int i=1; i<=dim; i++) {
			double [] vals = this.get(i).valueArray(valAry, cache);
			for(int j=0;j<len;j++) {
				rlt[j].set(i, vals[j]);
			}
		}
		return rlt;
	}
	
	@Override
	public int getDim() {
		return this.dim;
	}
	
	@Override
	public void setDim(int dim) {
		this.dim = dim;
	}
	
	@Override
	public void setVarNames(List<String> varNames) {
		this.varNames = varNames;
	}

	@Override
	public List<String> varNames() {
		return this.varNames;
	}
	
	@Override
	public VectorFunction compose(Map<String,Function> fInners) {
		for(int i=1; i<=dim; i++)
			this.set(i,this.get(i).compose(fInners));
		return this;
	}
	
	/////////////////////////////////////////////
	@Override
	public Function get(int index) {
		throw new FutureyeException("Component of AbstractVectorFunction is not define!");
	}
	
	@Override
	public VectorFunction set(VectorFunction v) {
		for(int i=1; i<=dim; i++)
			this.set(i,v.get(i));
		this.setVarNames(v.varNames());
		return this;
	}
	
	@Override
	public VectorFunction set(double a, VectorFunction v) {
		for(int i=1; i<=dim; i++) {
			this.set(i,v.get(i).M(a));
		}
		this.setVarNames(v.varNames());
		return this;
	}
	
	@Override
	public VectorFunction add(VectorFunction g) {
		for(int i=1; i<=dim; i++) {
			this.set(i,this.get(i).A(g.get(i)));
		}
		varNames = Utils.mergeList(varNames, g.varNames());
		return this;
	}
	
	@Override
	public VectorFunction add(double a, VectorFunction g) {
		for(int i=1; i<=dim; i++) {
			this.set(i,this.get(i).A(g.get(i).M(a)));
		}
		varNames = Utils.mergeList(varNames, g.varNames());
		return this;
	}
	
	@Override
	public VectorFunction scale(double a) {
		for(int i=1; i<=dim; i++) {
			this.set(i,this.get(i).M(a));
		}
		return this;
	}
	
	@Override
	public VectorFunction ax(double a) {
		for(int i=1; i<=dim; i++) {
			this.set(i,this.get(i).M(a));
		}
		return this;
	}
	
	@Override
	public VectorFunction axpy(double a, VectorFunction g) {
		for(int i=1; i<=dim; i++) {
			this.set(i,this.get(i).M(a).A(g.get(i)));
		}
		varNames = Utils.mergeList(varNames, g.varNames());
		return this;
	}
	
	@Override
	public Function dot(VectorFunction b) {
		if(dim != b.getDim()) {
			Exception e = new FutureyeException("Dims between two vector functions must be same!");
			e.printStackTrace();
			return null;
		}
		Function rlt = FC.C0;
		for(int i=1;i<=dim;i++) {
			rlt = rlt.A(this.get(i).M(b.get(i)));
		}
		return rlt;
	}

	@Override
	public Function dot(Vector b) {
		Function rlt = FC.C0;
		for(int i=1;i<=dim;i++) {
			rlt = rlt.A(this.get(i).M(b.get(i)));
		}
		return rlt;
	}
	
	/////////////////////////////////////////////
	
	@Override
	public VectorFunction A(VectorFunction g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).A(g.get(i)));
		}
		return svf;
	}
	
	@Override
	public VectorFunction A(Vector g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).A(g.get(i)));
		}
		return svf;
	}
	
	@Override
	public VectorFunction S(VectorFunction g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).S(g.get(i)));
		}
		return svf;
	}
	
	@Override
	public VectorFunction S(Vector g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).S(g.get(i)));
		}
		return svf;
	}
	

	@Override
	public VectorFunction M(VectorFunction g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).M(g.get(i)));
		}
		return svf;
	}
	@Override
	public VectorFunction M(Vector g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).M(g.get(i)));
		}
		return svf;		
	}
	

	@Override
	public VectorFunction D(VectorFunction g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).D(g.get(i)));
		}
		return svf;		
	}
	@Override
	public VectorFunction D(Vector g) {
		SpaceVectorFunction svf = new SpaceVectorFunction(dim);
		for(int i=1; i<=dim; i++) {
			svf.set(i, this.get(i).D(g.get(i)));
		}
		return svf;		
	}
	
	///////////////////////////////////////////////////
	
	@Override
	public VectorFunction copy() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getExpression() {
		String s = varNames.toString();
		String rlt = "[";
		for(int i=0;i<dim;i++) {
			rlt += "F("+s.substring(1, s.length()-1)+")";
			if(i<dim-1)
				rlt += ", ";
		}
		return rlt+"]";
	}
	
	@Override
	public String getFName() {
		return this.fName;
	}
	
	@Override
	public VectorFunction setFName(String name) {
		this.fName = name;
		return this;
	}
	
	@Override
	public String toString() {
		if(getFName() == null) {
			return getExpression();
		} else 
			return getFName();
	}
	
}
