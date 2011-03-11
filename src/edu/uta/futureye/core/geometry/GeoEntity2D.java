package edu.uta.futureye.core.geometry;

import edu.uta.futureye.core.geometry.topology.Topology2D;
import edu.uta.futureye.util.list.ObjList;

/**
 * ��ά����ʵ�壬��������ԪElement�ļ�����Ϣ
 * 
 *   �����vertices����ά��Ԫ�Ķ��㼯�ϣ�e.g.�����ε�Ԫ���������㣬�ı��ε�Ԫ���ĸ����㣬...��
 *   edges����ά��Ԫ�ı߼���
 *   faceNodes����ά��Ԫ�ڲ��Ľ�㼯��
 *   
 * @author liuyueming
 *
 */
public class GeoEntity2D<
						TEdge extends GeoEntity1D<TNode>,
						TNode extends Point
						> extends GeoEntity0D {
	protected Topology2D topology = null;
	protected ObjList<TEdge> edges = new ObjList<TEdge>();
	protected ObjList<TNode> faceNodes = null;
	
	public void addEdge(TEdge edge) {
		this.edges.add(edge);
	}
	public void addAllEdges(ObjList<TEdge> edges) {
		this.edges.clear();
		this.edges.addAll(edges);
	}
	public ObjList<TEdge> getEdges() {
		return this.edges;
	}
	public void clearEdges() {
		this.edges.clear();
	}
	
	public void addFaceNode(TNode node) {
		if(this.faceNodes == null)
			this.faceNodes = new ObjList<TNode>();
		this.faceNodes.add(node);
	}
	public void addAllFaceNodes(ObjList<TNode> faceNodes) {
		if(this.faceNodes == null)
			this.faceNodes = new ObjList<TNode>();
		this.faceNodes.clear();
		this.faceNodes.addAll(faceNodes);
	}
	public ObjList<TNode> getFaceNodes() {
		return this.faceNodes;
	}
	public void clearFaceNodes() {
		if(this.faceNodes != null)
			this.faceNodes.clear();
	}
	public void clearAll() {
		this.edges.clear();
		if(this.faceNodes != null)
			this.faceNodes.clear();
	}
	public Topology2D getTopology() {
		return topology;
	}
	public void setTopology(Topology2D topology) {
		this.topology = topology;
	}	
}
