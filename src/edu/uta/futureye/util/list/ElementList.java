package edu.uta.futureye.util.list;

import edu.uta.futureye.core.Element;

/**
 * Element List Class
 * ��Ԫ�б���
 * 
 * @author liuyueming
 * 
 */
public class ElementList extends ObjList<Element>{
	@Override
	public String toString() {
		return "ElementList"+objs.toString();
	}
}
