package edu.uta.futureye.core.geometry;

import edu.uta.futureye.util.list.ObjList;

/**
 * һά����ʵ�壬��������ԪElement�ļ�����Ϣ
 *   �����vertices��һά��Ԫ��Ӧ���߶ζ��㣨�����˵㣩
 *   nodes�����ϵĽ���б��������˵㣨e.g.����Ԫ��0��������Ԫ��1����...��
 *   
 * @author liuyueming
 *
 */
public class GeoEntity1D<TNode extends Point> extends GeoEntity0D {
	//���ϵĽ�㣬�������˵�
	protected ObjList<TNode> edgeNodes = null;
	
	public void addEdgeNode(TNode node) {
		if(this.edgeNodes == null)
			this.edgeNodes = new ObjList<TNode>();
		this.edgeNodes.add(node);
	}
	
	public void addAllEdgeNodes(ObjList<TNode> nodes) {
		if(this.edgeNodes == null)
			this.edgeNodes = new ObjList<TNode>();
		this.edgeNodes.clear();
		this.edgeNodes.addAll(nodes);
	}
	
	public ObjList<TNode> getEdgeNodes() {
		return this.edgeNodes;
	}
	
	public void clearEdgeNodes() {
		if(this.edgeNodes != null)
			this.edgeNodes.clear();
	}
}