package edu.uta.futureye.function;

import java.util.LinkedHashMap;
import java.util.Map;

import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.geometry.Point;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.util.Constant;

/**
 * Function arguments (Independent variables of a function)
 * @author liuyueming
 *
 */
public class Variable {
	//LinkedHashMap ����ʱ��֤����˳��
	protected Map<String,Double> values = new LinkedHashMap<String,Double>();

	//protected boolean bApplyRestirct = false;
	
	//Node Index
	protected int index = 0;
	
	//�����п���Я��element����class DuDn
	protected Element element = null;
	
	public Variable() {
	}
	
	///////////////////////////////////////////////
	/**
	 * ����һά�Ա�������ֵ
	 */
	public Variable(double val) {
		values.put(Constant.x, val);
	}
	
	/**
	 * ��ȡһά�Ա���ֵ
	 * @return
	 */
	public double get() {
		//TODO Need check values.size==1 ?
		return values.values().iterator().next();
	}
	
	////////////////////////////////////////////////

	public Variable(String name, double val) {
		values.put(name, val);
	}
	
	public Variable(VarPair fitst, VarPair ...pairs) {
		values.put(fitst.name, fitst.value);
		for(int i=0;i<pairs.length;i++)
			values.put(pairs[i].name, pairs[i].value);
	}
	
	/**
	 * �����Ա��������ƶ�Ӧ��ֵ
	 * @param name
	 * @return
	 */
	public double get(String name) {
		return values.get(name);
	}
	
	/**
	 * �����Ա�����ֵ�����ơ�ֵ�ԣ� ���ر��������Ա���ʽ��
	 * ���磺���ö�ά�Ա���x=1,y=2��
	 * Variable v = new Variable("x",1).set("y",2);
	 * 
	 * @param name
	 * @param val
	 * @return
	 */
	public Variable set(String name, double val) {
		values.put(name, val);
		return this;
	}

	public Map<String,Double> getValues() {
		return values;
	}

	
//	public void applyRestirct(boolean flag) {
//		bApplyRestirct = flag;
//	}
//	
//	/**
//	 * ����������Ա��������ƣ������ά�κ������Ƶ�һά�߽��ϣ�isRestrict()����ʾ������ֵʱ�Ƿ�Ӧ�ø�����
//	 * @return
//	 */
//	public boolean isRestrict() {
//		return bApplyRestirct;
//	}

	
	/**
	 * ����fun���Ա���������Point��ֵ���Լ�index�������Ҫ�Ļ�����
	 * ����һ��Variable�Ķ���
	 * @param fun
	 * @param point
	 * @param index
	 * @return
	 */
	public static Variable createFrom(Function fun, Point point, int index) {
		if(fun == null)
			return null;
		Variable var = new Variable(index);
		if(fun.varNames() != null) {
			int ic = 1;
			for(String vn : fun.varNames()) {
				var.set(vn, point.coord(ic));
				ic++;
			}
		} else {
			//VectorBasedFunction
		}
		return var;
	}
	
	/**
	 * ������Я��������������ţ�
	 * @param index
	 */
	public Variable setIndex(int index) {
		this.index = index;
		return this;
	}
	public int getIndex() {
		return index;
	}
	
	/**
	 * ������Я����Ԫ����
	 * @return
	 */
	public Element getElement() {
		return element;
	}
	public Variable setElement(Element element) {
		this.element = element;
		return this;
	}
	
	public String toString() {
		return values.toString();
	}
	
	public static void main(String[] args) {
		Variable v1 = new Variable();
		System.out.println(v1);
		Variable v2 = new Variable(new VarPair("x",1.0));
		System.out.println(v2);
		Variable v3 = new Variable(new VarPair("x",1.0),
				new VarPair("y",2.0));
		System.out.println(v3);
	}
	
}
