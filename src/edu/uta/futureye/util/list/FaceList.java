package edu.uta.futureye.util.list;

import edu.uta.futureye.core.Face;

/**
 * Face List Class
 * ��ȫ�֣������б�
 * 
 * @author liuyueming
 *
 */
public class FaceList extends ObjList<Face> {
	@Override
	public String toString() {
		return "FaceList"+objs.toString();
	}
}
