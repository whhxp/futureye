package edu.uta.futureye.core;

import edu.uta.futureye.algebra.SpaceVector;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.geometry.GeoEntity1D;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.list.DOFList;
import edu.uta.futureye.util.list.ObjList;

/**
 * Local edge of an element
 * �ֲ���
 * 
 * @author liuyueming
 *
 */
public class EdgeLocal extends GeoEntity1D<NodeLocal> {
	//�߾ֲ���������ţ�
	public int localIndex;
	//global edge shared with all elements that containing the edge
	protected Edge globalEdge = null; 
	public Element owner = null;
	
	//�ֲ���λ���⣩������
	private Vector localUnitNormVector = null;
	
	public EdgeLocal(int localIndex, Element owner) {
		this.localIndex = localIndex;
		this.owner = owner;
	}
	
	public EdgeLocal(int localIndex, Edge globalEdge) {
		this.localIndex = localIndex;
		this.globalEdge = globalEdge;
	}
	
	public EdgeLocal(int localIndex, Edge globalEdge, Element owner) {
		this.localIndex = localIndex;
		this.globalEdge = globalEdge;
		this.owner = owner;
	}
	
	public void setGlobalEdge(Edge globalEdge) {
		this.globalEdge = globalEdge;
	}
	
	public Edge getGlobalEdge() {
    	return this.globalEdge;
    }

	/**
	 * �߽����ͣ����������Ƿ�����ȫ�ֱ߽�
	 * @return
	 */
    public NodeType getBorderType() {
    	NodeType nt1 = this.beginNode().getNodeType();
    	NodeType nt2 = this.endNode().getNodeType();                       
    	if(nt1 == nt2) return nt1;
    	else {
    		//TODO Exception?
    		return null;
    	}
    }
   
	/**
	 * �Ƿ�λ������߽磬���������Ƿ�����ȫ�ֱ߽�
	 * @return
	 */
    public boolean isBorderEdge() {
		if(this.beginNode().getNodeType()==NodeType.Inner ||
				this.endNode().getNodeType()==NodeType.Inner)
			return false;
		else
			return true;
	}
	
    public Vertex beginVertex() {
		return this.vertices.at(1);
    }
    
    public Vertex endVertex() {
		return this.vertices.at(2);
    }
    
	public Node beginNode() {
		return this.vertices.at(1).globalNode();
	}

	public Node endNode() {
		return this.vertices.at(2).globalNode();
	}    
    
    public Vector getNormVector() {
    	if(localUnitNormVector == null) {
    		if(this.globalEdge != null) {
    		 //�ֲ�����ȫ�ֱ߷����п�����ͬҲ�п��ܲ�ͬ����֤�����˳����ͬ����һ�£���ͬ�����෴
    		if(this.beginNode().globalIndex == this.globalEdge.beginNode().globalIndex)
    			localUnitNormVector = this.globalEdge.getNormVector().copy();
    		else
    			localUnitNormVector = SpaceVector.ax(-1.0, this.globalEdge.getNormVector());
    		} else {
    			localUnitNormVector = Utils.getNormVector(
    					this.beginNode(),
    					this.endNode()
    					);
    		}
    	}
    	return this.localUnitNormVector;
    }
    
    public Edge buildEdge() {
    	Edge edge = new Edge();
    	edge.addVertex(new Vertex(1,new NodeLocal(1,this.beginNode())));
    	edge.addVertex(new Vertex(2,new NodeLocal(2,this.endNode())));
		ObjList<NodeLocal> edgeNodes = this.getEdgeNodes();
		if(edgeNodes != null && edgeNodes.size()>0) {
			for(int i=1;i<=edgeNodes.size();i++)
				edge.addEdgeNode(new NodeLocal(2+i,edgeNodes.at(i).globalNode));
		}
		return edge;
    }
    
	/**
	 * Edge�Լ���Ϊһ����Ԫ�����ڱ߽���֣��߻��֣�
	 * @return
	 */
	public Element changeToElement() {
		//Ҫʹ��ȫ�ֱߣ��ֲ��ߵĽ���Ų�һ������ȷ�ġ�
		Element be = new Element(this.buildEdge());
		
		DOFList eDOFList = owner.getNodeDOFList(this.vertices.at(1).localNode().localIndex);
		for(int j=1;eDOFList!=null && j<=eDOFList.size();j++) {
			DOF dof = new DOF(
						1,
						eDOFList.at(j).globalIndex,
						eDOFList.at(j).getSSF().restrictTo(1)
					);
			be.addNodeDOF(1, dof);
		}
		eDOFList = owner.getNodeDOFList(this.vertices.at(2).localNode().localIndex);
		for(int j=1;eDOFList!=null && j<=eDOFList.size();j++) {
			DOF dof = new DOF(
						2,
						eDOFList.at(j).globalIndex,
						eDOFList.at(j).getSSF().restrictTo(2)
					);
			be.addNodeDOF(2, dof);
		}	
		
		ObjList<NodeLocal> edgeNodes = this.getEdgeNodes();
		if(edgeNodes != null && edgeNodes.size()>0) {
			int dofIndex = 3;
			for(int i=1;i<=edgeNodes.size();i++) {
				eDOFList = owner.getNodeDOFList(edgeNodes.at(i).localIndex);
				for(int j=1;eDOFList!=null && j<=eDOFList.size();j++) {
					int localIndex = dofIndex++;
					DOF dof = new DOF(
						localIndex,
						eDOFList.at(j).globalIndex,
						eDOFList.at(j).getSSF().restrictTo(localIndex)
					);
					be.addNodeDOF(localIndex, dof);
				}
			}
		}
		return be;
	}
	
	public String toString() {
		return "EdgeLocal"+this.localIndex+"<=>"+this.vertices.toString();
	}
}
