package edu.uta.futureye.function.intf;

import java.util.List;
import java.util.Map;

import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.VariableArray;

/**
 * Function interface
 * 
 * @author liuyueming
 *
 */
public interface Function {
	/**
	 * Return function value at variable v
	 * <p>
	 * 返回自变量v对应的函数值
	 * 
	 * @param v
	 * @return double function value
	 */
	double value(Variable v);
	
	/**
	 * Return function value at variable v with cache enabled
	 * <p>
	 * 返回自变量v对应的函数值，支持缓存。在一个复杂的函数表达式中，
	 * 对于同一函数多次求值的情况，使用缓存可以提高计算速度。例如在
	 * 有限元问题的数值积分中，Jacobian在被积函数中可能多处出现，
	 * 此时可以利用缓存对象在第一次求值后将其缓存起来，后续的求值调用
	 * 只需要从缓存中获取即可，不需要反复求值。
	 * 
	 * @param v
	 * @param cache
	 * @return
	 */
	double value(Variable v, Map<Object,Object> cache);
	
	/**
	 * For more efficiency, this interface can be used to return function values
	 * at an array of variables at once.
	 * 
	 * @param valAry VariableArray object which represents an array of variable values
	 * @param cache Cache for efficient evaluation of functions. <tt>null</tt> parameter will disable the cache mechanism
	 * @return Function values evaluated at array of variables <tt>valAry</tt> 
	 */
	double[] valueArray(VariableArray valAry, Map<Object, Object> cache);
	
	/**
	 * Set function variable names
	 * <p>
	 * 设置函数自变量名称，对于复合函数，只设置外层自变量名称
	 * <p>
	 * 关于复合函数的构造 @see compose()
	 * 
	 * @param varNames
	 * @return TODO
	 */
	Function setVarNames(List<String> varNames);
	
	/**
	 * Return all variable names of the function
	 * <p>
	 * 返回所有自变量名称
	 * 
	 * @return
	 */
	List<String> varNames();
	
	/**
	 * Add
	 * 
	 * @param g
	 * @return f+g, f==this
	 */
	Function A(Function g);
	
	/**
	 * Add
	 * 
	 * @param g
	 * @return f+g, f==this
	 */
	Function A(double g);
	
	/**
	 * Subtract
	 * 
	 * @param g
	 * @return f-g, f==this
	 */
	Function S(Function g);
	
	/**
	 * Subtract
	 * 
	 * @param g
	 * @return f-g, f==this
	 */
	Function S(double g);
	
	/**
	 * Multiply
	 * 
	 * @param g
	 * @return f*g, f==this
	 */
	Function M(Function g);
	
	/**
	 * Multiply
	 * 
	 * @param g
	 * @return f*g, f==this
	 */
	Function M(double g);
	
	/**
	 * Divide
	 * 
	 * @param g
	 * @return f/g, f==this
	 */
	Function D(Function g);
	
	/**
	 * Divide
	 * 
	 * @param g
	 * @return f/g, f==this
	 */	
	Function D(double g);
	
	/**
	 * Composition function (复合函数)
	 * <p><blockquote><pre>
	 * e.g.
	 * Function fx = FX.fx;
	 * Function fr = new FX("r");
	 * Function fOut = fx.M(fx).S(FC.c1);
	 * System.out.println(fOut); //x*x - 1.0
	 * 
	 * Function fIn = fr.M(fr).A(FC.c1);
	 * System.out.println(fIn); //r*r + 1.0
	 * 
	 * //Construct a map to define variable mapping
	 * Map<String,Function> map = new HashMap<String,Function>();
	 * map.put("x", fIn); //x=r*r + 1.0
	 * Function fComp = fOut.compose(map);
	 * System.out.println(fComp); //x(r)*x(r) - 1.0, where x=r*r + 1.0
	 * </pre></blockquote>
	 * 
	 * @param fInners: Variable map e.g.[ x = x(r,s), y = y(r,s) ]
	 * @return composed function e.g. f = f(x,y) = f( x(r,s),y(r,s) )
	 */
	Function compose(Map<String,Function> fInners);
	
	/**
	 * First derivative with respect to <code>varName</code> 
	 * <p>
	 * 关于<code>varName</code>的一阶导数：
	 * <p>
	 * f(x)._d(x) := \frac{ \partial{this} }{ \partial{varName} }
	 * 
	 * @param varName
	 * @return
	 */
	Function _d(String varName);

	
	/**
	 * Return value of constant function only
	 * <p>
	 * 返回常值函数的函数值，仅用于常值函数
	 * 
	 * @return
	 */
	double value();

	/**
	 * If it is a constant function
	 * 
	 * @return
	 */
	boolean isConstant();
	
	/**
	 * Deep copy
	 * 
	 * @return
	 */
	Function copy();
	
	/**
	 * Return the expression of function
	 * 
	 * @return
	 */
	String getExpression();
	
	/**
	 * Get function name
	 * <p>
	 * If function name is not null, the name instead of the expression of
	 * function is returned by <code>toString()</code> method
	 * 
	 * @return
	 */
	String getFName();
	
	/**
	 * Set function name
	 * <p>
	 * If function name is not null, the name instead of the expression of
	 * function is returned by <code>toString()</code> method
	 * 
	 * @param name
	 * @return
	 */
	Function setFName(String name);
	
	static int OP_ORDER0 = 0; //Brackets first
	static int OP_ORDER1 = 1; //Exponents next
	static int OP_ORDER2 = 2; //Multiply and Divide next
	static int OP_ORDER3 = 3; //Add and Subtract last of all
	
	/**
	 * Get order of operations (Priority Rules for Arithmetic)
	 * <blockquote><pre>
	 * 0 Brackets first
	 * 1 Exponents next
	 * 2 Multiply and Divide next
	 * 3 Add and Subtract last of all.
	 * </blockquote></pre>  
	 */	
	int getOpOrder();
	
	/**
	 * Set order of operations (Priority Rules for Arithmetic)
	 * <blockquote><pre>
	 * 0 Brackets first
	 * 1 Exponents next
	 * 2 Multiply and Divide next
	 * 3 Add and Subtract last of all.
	 * </blockquote></pre>  
	 * @param order
	 */
	void setOpOrder(int order);
	
}
