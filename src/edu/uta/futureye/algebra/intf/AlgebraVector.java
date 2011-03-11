package edu.uta.futureye.algebra.intf;

/**
 * Ϊ�������������Ż��������ӿ�
 * 
 * @author liuyueming
 *
 */
public interface AlgebraVector {
	/**
	 * ���������ά��
	 * @return
	 */
	public int getDim();
	
	/**
	 * �������������
	 * @return
	 */
	public double[] getData();
	
	/**
	 * this = v
	 * @param v
	 */
	public AlgebraVector set(AlgebraVector v);

	/**
	 * this += v
	 * @param a
	 * @param v
	 */
	public AlgebraVector plus(AlgebraVector v);	

	/**
	 * this -= v
	 * @param a
	 * @param v
	 */
	public AlgebraVector minus(AlgebraVector v);	

	/**
	 * this += a*v
	 * @param a
	 * @param v
	 */
	public AlgebraVector add(double a, AlgebraVector v);	

	/**
	 * this *= a
	 * @param a
	 * @return
	 */
	public AlgebraVector scale(double a);

	/**
	 * this *= a
	 * �ȼ��� scale(double a);
	 * @param a
	 * @return
	 */
	public AlgebraVector ax(double a);
	
	/**
	 * x = a*x+y
	 * �ȼ��� add(double a, AlgebraVector v);
	 * @param a
	 * @param b
	 * @return
	 */
	public AlgebraVector axpy(double a, AlgebraVector y);
	
	/**
	 * x = (a*x).*y
	 * @param a
	 * @param y
	 * @return
	 */
	public AlgebraVector axmy(double a, AlgebraVector y);
	
	/**
	 * x.y
	 * �ڻ�
	 * @param y
	 * @return
	 */
	public double dot(AlgebraVector y);
	
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
	 * print the component values of this vector
	 */
	public void print();

}
