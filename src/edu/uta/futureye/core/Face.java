package edu.uta.futureye.core;

import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.geometry.GeoEntity2D;
import edu.uta.futureye.util.FutureyeException;
import edu.uta.futureye.util.list.ObjList;

/**
 * Global face of an element
 * ȫ���棨��ά��Ԫ��ȫ���棩
 * 
 * @author liuyueming
 *
 */
public class Face extends GeoEntity2D<EdgeLocal,NodeLocal> {
	protected int globalIndex;
	protected Vector globalUnitNormVector = null; //ȫ�ֵ�λ������
	
	public int getGlobalIndex() {
		return globalIndex;
	}

	public void setGlobalIndex(int globalIndex) {
		this.globalIndex = globalIndex;
	}
	
	public Vector getNormVector() {
		if(this.globalUnitNormVector == null) {
			//TODO
		}
		return this.globalUnitNormVector;
	}
	
	/**
	 * ������ı߽����ͣ�ȷ�����ж��������Ҫ����ͬ
	 * 
	 * @return
	 */
    public NodeType getBorderType() {
    	NodeType nt1 = this.vertices.at(1).globalNode().getNodeType();
    	for(int i=2;i<this.vertices.size();i++) {
    		NodeType nt2 = this.vertices.at(2).globalNode().getNodeType();
    	   	if(nt1 != nt2) return null;                  
    	}
    	return nt1;                  
    }
    
	public boolean isBorderFace() {
		//�����Ӧ��NodeLocal�Ƿ�Inner���ɣ�Ҳ����˵ֻҪ��һ����Inner˵�����治�Ǳ߽���
		ObjList<Vertex> vs = this.getVertices();
		if(vs.size() >= 3) {
			for(int i=1;i<=vs.size();i++) {
				NodeLocal nl = vs.at(i).localNode();
				if(nl.globalNode.getNodeType()==NodeType.Inner)
					return false;
			}
		} else {
			FutureyeException ex = new FutureyeException("Number of vertices on a face is "+vs.size());
			ex.printStackTrace();
			System.exit(0);
		}
		return true;
	}
	
	public String toString() {
		return "GlobalFace"+this.globalIndex+this.vertices.toString();
	}
}
