package edu.uta.futureye.function.intf;

import java.util.List;

import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.function.Variable;

public interface VectorFunction {
	Vector value(Variable v);
	
	void setVarNames(List<String> varNames);
	List<String> varNames();
	
	/**
	 * ���������ά��
	 * @return
	 */
	int getDim();
	
	/**
	 * ������index��ֵ����Ϊvalue
	 * @param index
	 * @param value
	 */
	void set(int index, Function value);
	
	/**
	 * ��÷���index��ֵ
	 * @param index
	 * @return
	 */
	Function get(int index);

	/**
	 * �����Լ�
	 * @return
	 */
	VectorFunction copy();
	
	/**
	 * ������
	 * @return
	 */
	Function norm2();
	
	/**
	 * �����
	 * @return
	 */
	Function normInf();
	
	/**
	 * ��ˣ��ڻ���
	 * Dot product
	 * 
	 * @param b = (b1(x) b2(x) ... bn(x))'
	 * @return = a1(x)*b1(x) + a2(x)*b2(x) + ... + an(x)*bn(x)
	 */
	Function dot(VectorFunction b);
	
	/**
	 * ��ˣ��ڻ���
	 * Dot product
	 * 
	 * @param b = (b1 b2 ... bn)'
	 * @return = a1(x)*b1 + a2(x)*b2 + ... + an(x)*bn
	 */
	Function dot(Vector b);
	
	/**
	 * print the component values of this vector function
	 */
	void print();
}
