package edu.uta.futureye.function.intf;

import edu.uta.futureye.core.Element;
import edu.uta.futureye.util.list.ObjList;

public interface ShapeFunction {
	/**
	 * ���κ����Ǿֲ�����ĸ��Ϻ���ʱ���������������������
	 * @return
	 */
	ObjList<String> innerVarNames();
	
	/**
	 * �����κ����͵�Ԫ
	 * @param e
	 */
	void asignElement(Element e);
	
	/**
	 * ���κ�������Ϊ��һά���κ���
	 * @param funIndex
	 * @return
	 */
	ShapeFunction restrictTo(int funIndex);
	
}
