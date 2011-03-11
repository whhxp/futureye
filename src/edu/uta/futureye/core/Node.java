package edu.uta.futureye.core;

import edu.uta.futureye.core.geometry.Point;
import edu.uta.futureye.util.Constant;
import edu.uta.futureye.util.list.ElementList;
import edu.uta.futureye.util.list.NodeList;

/**
 * Global finite element node class
 * ����Ԫ��ȫ�֣����
 * 
 * @author liuyueming
 *
 */
public class Node implements Point {
	//�ռ�ά��
	protected int dim = 0;
	
	//�ռ�����
	protected double[] coords = new double[3];
	
	//ȫ����������ţ�
	public int globalIndex = 0;
	
	//������ͣ��߽��� or �ڲ����
	private NodeType nodeType = null;
	
	//ȫ�����������������Ԫ
	public ElementList belongToElements = new ElementList();
	
	//���ڽ��
	public NodeList neighbors = new NodeList();
	
	public Node() {
	}
	public Node(int dim) {
		this.dim = dim;
	}
	
	/**
	 * ����һ��ȫ�ֽ��
	 * @param globalIndex ȫ����������ţ�
	 * @param x ��һ������
	 * @param coords �������꣨y:2D or y,z:3D��
	 */
	public Node(int globalIndex, double x, double ...coords) {
		this.globalIndex = globalIndex;
		this.coords[0] = x;
		if(coords!=null && coords.length > 0) {
			this.dim = 1+coords.length;
			for(int i=0;i<coords.length;i++)
				this.coords[1+i] = coords[i];
		} else {
			this.dim = 1;
		}
	}
	
	public Node set(int globalIndex, double ...coords) {
		this.globalIndex = globalIndex;
		if(coords!=null && coords.length > 0) {
			this.dim = coords.length;
			for(int i=0;i<dim;i++)
				this.coords[i] = coords[i];
		}
		return this;
	}
	
	@Override
	public int getIndex() {
		return this.globalIndex;
	}
	
	@Override
	public int dim() {
		return dim;
	}
	
	@Override
	public double coord(int index) {
		return coords[index-1];
	}
	
	@Override
	public double[] coords() {
		double[] rlt;
		if(this.dim < 3) {
			rlt = new double[dim];
			for(int i=0;i<dim;i++)
				rlt[i] = this.coords[i];
		} else
			rlt = this.coords;
		return rlt;
	}
	
	public void setCoord(int index,double val) {
		coords[index-1] = val;
	}
	
	@Override
	public boolean coordEquals(Point p) {
		for(int i=1;i<=this.dim;i++) {
			if(Math.abs(this.coord(i)-p.coord(i)) > Constant.eps)
				return false;
		}
		return true;
	}
	
	public boolean isInnerNode() {
		return nodeType==NodeType.Inner;
	}
	
	public NodeType getNodeType() {
		return nodeType;
	}
	
	public void setNodeType(NodeType nodeType) {
		this.nodeType = nodeType;
	}
	
	public void addBelongToElements(Element e) {
		for(int i=1;i<=belongToElements.size();i++) {
			//TODO e.globalIndex �������������ֱ�ӱȽϣ�
			if(e.equals(belongToElements.at(i)))
				return;
		}
		belongToElements.add(e);
	}
	
	//////////////////////////////////////////////////////
	//������ڵļ��ܲ�Σ�����level�μ��ܲ����Ľ��
	protected int level = 1;
	
	public int getLevel() {
		return this.level;
	}
	
	public void setLevel(int level) {
		this.level = level;
	}
	///////////////////////////////////////////////////////
	
	public String toString()  {
		String r = "GN"+globalIndex+"( ";
		for(int i=0;i<dim;i++)
			r += String.valueOf(coords[i])+" ";
		return r+")";
	}
}
 