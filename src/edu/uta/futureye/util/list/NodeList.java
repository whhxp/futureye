package edu.uta.futureye.util.list;

import edu.uta.futureye.core.Node;

/**
 * Node List Class
 * �ڵ��б���
 * 
 * @author liuyueming
 *
 */
public class NodeList extends ObjList<Node>{
	@Override
	public String toString() {
		return "NodeList"+objs.toString();
	}
}