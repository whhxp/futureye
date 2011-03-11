package edu.uta.futureye.function.intf;

import java.util.List;
import java.util.Map;

import edu.uta.futureye.function.Variable;

public interface Function {
	/**
	 * ���غ���ֵ
	 * @param v
	 * @return
	 */
	double value(Variable v);
	
	/**
	 * ���ñ�������
	 * @param varNames
	 */
	void setVarNames(List<String> varNames);
	
	/**
	 * �������б�������
	 * @return
	 */
	List<String> varNames();
	
	/**
	 * this+f
	 * @param f
	 * @return
	 */
	Function P(Function f);
	
	/**
	 * this-f
	 * @param f
	 * @return
	 */
	Function M(Function f);
	
	/**
	 * this*f
	 * @param f
	 * @return
	 */
	Function X(Function f);
	
	/**
	 * this/f
	 * @param f
	 * @return
	 */
	Function D(Function f);
	
	/**
	 * ���Ϻ���
	 * @param e.g. fInners: Map[ x = x(r,s), y = y(r,s) ]
	 * @return e.g. f = f(x,y) = f( x(r,s),y(r,s) )
	 */
	Function compose(Map<String,Function> fInners);
	
	/**
	 * f(x).d(x) := \frac{ \partial{this} }{ \partial{varName} }
	 * 
	 * ����varName��һ�׵���
	 * @param varName
	 * @return
	 */
	Function d(String varName);
	
	/**
	 * For constant function only
	 * @return
	 */
	double value();

}
