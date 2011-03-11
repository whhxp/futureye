package edu.uta.futureye.core.intf;

import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.function.intf.Function;

/**
 * ����ϳɽӿڣ���������Laplace����ʱ������ֵ��������û�г���Ϊ�ӿڡ�
 * ���ǣ�����������ֵ����ʱ�����ܻ�����ֿ������ʱ����ϳɵĹ�������ǰ
 * �кܴ�������Ҫ������ķ�ʽʵ�֣���˽�������ϳɡ����ֳ���Ϊ�ӿڡ�
 * 
 * @author liuyueming
 *
 */
public interface Assembler {
	/**
	 * ִ������ϳɲ���
	 */
	void assemble();

	Matrix getStiffnessMatrix();
	
	Vector getLoadVector();
	
	void imposeDirichletCondition(Function diri);
}
