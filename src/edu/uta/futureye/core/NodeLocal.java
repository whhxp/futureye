package edu.uta.futureye.core;

import edu.uta.futureye.core.geometry.Point;

/**
 * * Local finite element node class. 
 * 
 * * ����Ԫ���ֲ�����㣬������ȫ�ֽ������ã�������ֵΪȫ�ֽ������ꡣ
 * * �ֲ������ڴ�ռ�����ȫ�ֽ��Ҫ�ٺܶ�
 * * �ֲ����ľֲ���������ţ�������ڸýڵ������Ķ�����˵�ģ����磺
 *     �����ε�Ԫ�����ϵľֲ����Ϊt1��t2��t3���оֲ���e12��e23��e31��
 *     ����e23��Ӧ��ȫ�ֱ�ge23�����ϵľֲ���� ���Ӧ����ge1��ge2��
 *     �������������ȫ�ֽ���й�ϵ��t2=ge1��t3=ge2
 * * ��һ���ֲ��������ĳ�����޵�Ԫʱ���ֲ����Ӧ�ð����Ե�Ԫ�����ã�
 *   ������ĳ�����޵�Ԫʱ����Ӧ�ÿ���Ϊnull������һ��ȫ�ֱ��ϵľֲ����
 *   �Ͳ������κ����޵�Ԫ
 * 
 * @author liuyueming
 *
 */
public class NodeLocal implements Point {
	public Element owner = null; //������Ԫ
	public Node globalNode; //��Ӧ��ͬ�����ȫ�ֽ��
	public int localIndex; //�ڵ�Ԫ�ڵľֲ����
	
	public NodeLocal(int localIndex, Node globalNode) {
		this.localIndex = localIndex;
		this.globalNode = globalNode;
	}
	
	public NodeLocal(int localIndex, Node globalNode, Element owner) {
		this.localIndex = localIndex;
		this.globalNode = globalNode;
		this.owner = owner;
	}
	
	public Node globalNode() {
		return this.globalNode;
	}
	
	@Override
	public double coord(int index) {
		return this.globalNode.coord(index);
	}

	@Override
	public boolean coordEquals(Point p) {
		return this.globalNode.coordEquals(p);
	}

	@Override
	public double[] coords() {
		return this.globalNode.coords();
	}

	@Override
	public int dim() {
		return this.globalNode.dim();
	}

	@Override
	public int getIndex() {
		return this.localIndex;
	}

	@Override
	public void setCoord(int index, double value) {
		this.globalNode.setCoord(index, value);
	}
	
	@Override
	public String toString() {
		return "NodeLocal"+localIndex+"<=>"+globalNode.toString();
	}
}
