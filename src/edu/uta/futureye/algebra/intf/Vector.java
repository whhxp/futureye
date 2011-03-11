package edu.uta.futureye.algebra.intf;

/**
 * General vector interface
 * һ�������ӿڣ��������������������飨������������������ӿڲμ�AlgebraVector��
 * ��;��
 * 1. ��ά����ά���ռ���������������������
 * 2. ���������Ҷ������ϳɲ����еľֲ���ȫ������
 * 3. ����ϡ������ġ��������������ڴ�ӡ���
 * 
 * @author liuyueming
 * 
 */
public interface Vector {
	/**
	 * ����������ά��
	 * @param dim
	 */
	public void setDim(int dim);
	
	/**
	 * ���������ά��
	 * @return
	 */
	public int getDim();

	/**
	 * ������index��ֵ����Ϊvalue
	 * @param index
	 * @param value
	 */
	public void set(int index, double value);
	
	/**
	 * ������v��ֵ��ֵ��this
	 * @param v
	 */
	public void set(Vector v);

	/**
	 * ��÷���index��ֵ
	 * @param index
	 * @return
	 */
	public double get(int index);

	/**
	 * v(index) += value
	 * ����index��ֵ�ټ���value
	 * @param index
	 * @param value
	 */
	public void add(int index,double value);
	
	/**
	 * this += a*v
	 * 
	 * @param a
	 * @param v
	 */
	public void add(double a, Vector v);

	/**
	 * �����Լ������
	 * @return
	 */
	public Vector copy();
	
	/**
	 * ��ˣ��ڻ���
	 * Dot product
	 * 
	 * @param b = (b1 b2 ... bn)'
	 * @return = a1*b1 + a2*b2 + ... + an*bn
	 */
	public double dot(Vector b);
	
	/**
	 * ������
	 * @return
	 */
	public double norm2();
	
	/**
	 * �����
	 * @return
	 */
	public double normInf();
	
	/**
	 * �������������Ԫ��
	 */
	public void clear();
	
	/**
	 * print the component values of this vector
	 * ��ӡ����Ԫ��
	 */
	public void print();
	

}
