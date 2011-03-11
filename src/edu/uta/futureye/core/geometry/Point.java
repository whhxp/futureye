package edu.uta.futureye.core.geometry;

/**
 * ����Ԫ�ռ��
 * 
 * @author liuyueming
 *
 */
public interface Point extends GeoEntity {
	
	int dim();
	
	double coord(int index);
	
	double[] coords();
	
	void setCoord(int index,double value);
	
	/**
	 * �ж�������Ƿ����
	 * @param p
	 * @return
	 */
	boolean coordEquals(Point p);
	
	/**
	 * ��ȡ������ţ�
	 * ����ȫ�ֽ�㣬����ȫ�ֱ�ţ�
	 * ���ھֲ���㣬���ؾֲ����
	 * 
	 * @return
	 */
	int getIndex();
}
