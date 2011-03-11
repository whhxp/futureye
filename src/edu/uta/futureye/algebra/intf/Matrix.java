package edu.uta.futureye.algebra.intf;

import java.util.Map;

/**
 * General matrix interface
 * һ�����ӿڣ��������������������飨������������ľ���ӿڲμ�AlgebraMatrix��
 * ��;��
 * 1. ��������նȾ���ϳɲ����еľֲ���ȫ�־���
 * 2. ��ӡ�������
 * 
 * @author liuyueming
 *
 */
public interface Matrix {
	/**
	 * ��Ԫ����ֵ
	 */
	public static double zeroEps = 1e-10;
	
	/**
	 * ���þ�������
	 * @param nRowDim TODO
	 */
	void setRowDim(int nRowDim);
	
	/**
	 * ��ȡ��������
	 * @return TODO
	 */
	int getRowDim();
	
	/**
	 * ���þ�������
	 * @param nColDim TODO
	 */
	void setColDim(int nColDim);
	
	/**
	 * ��ȡ��������
	 * @return
	 */
	int getColDim();
	
	/**
	 * ����row��col��Ԫ�ص�ֵ
	 * @param row
	 * @param col
	 * @param value
	 */
	void set(int row, int col,double value);
	
	/**
	 * ��ȡrow��col��Ԫ�ص�ֵ
	 * @param row
	 * @param col
	 * @return
	 */
	double get(int row, int col);
	
	/**
	 * m(row,col) += value
	 * @param row
	 * @param col
	 * @param value
	 */
	void add(int row, int col,double value);
	
	/**
	 * get all non-zero element, instead of iterator
	 * ��ȡ���з���Ԫ�أ���ʹ�õ�����
	 * @return
	 */
	Map<Integer,Map<Integer,Double>> getAll();
	
	/**
	 * M(nRowBase + row, nColBase + col) = values in map
	 * ������map�е�����Ԫ��ֵ��ֵ������������map�����е��кŶ�����nRowBase��
	 * ���е��кŶ�����nColBase
	 * @param nRowBase
	 * @param nColBase
	 * @param map
	 */
	void setAll(int nRowBase, int nColBase, Map<Integer,Map<Integer,Double>> map);
	
	/**
	 * y = M*x
	 * ����������ˣ��ٶȽ����������ڿ��ٴ������������ľ���ӿڲμ�AlgebraMatrix
	 * @param x
	 * @param y
	 */
	void mult(Vector x, Vector y);
	
	/**
	 * ��վ����е�����Ԫ��
	 */
	void clear();
	
	/**
	 * ��ӡ����Ԫ��
	 */
	void print();
}