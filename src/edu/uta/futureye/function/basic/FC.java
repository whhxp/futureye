package edu.uta.futureye.function.basic;

import java.util.HashMap;
import java.util.Map;

import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.intf.Function;

/**
 * Constant function: f=c
 * 
 * @author liuyueming
 *
 */
public class FC extends AbstractFunction{
	//Predefined constant
	public static FC c0 = new FC(0.0);
	public static FC c1 = new FC(1.0);
	
	protected double val = 0.0;
	protected static Map<Double, FC> cs = new HashMap<Double, FC>();
	
	public FC(double val) {
		this.val = val;
	}
	
	/**
	 * ���س�ֵ���������ұ����ھ�̬Map�У��Ա���ʹ�ã���ʡ�ڴ�
	 * ע�⣺��Ҫʹ�øú������ɴ��������������ڴ��ڳ����˳�ǰ�����ͷ�
	 * @param v
	 * @return
	 */
	public static FC c(double v) {
		FC c = cs.get(v);
		if(c == null) {
			c = new FC(v);
			cs.put(v, c);
			return c;
		} else {
			return c;
		}
	}
	
	@Override
	public double value(Variable v) {
		return val;
	}

	@Override
	public double value() {
		return val;
	}
	
	@Override
	public Function d(String varName) {
		return c(0.0);
	}
	
	public String toString() {
		return String.valueOf(val);
	}
}
