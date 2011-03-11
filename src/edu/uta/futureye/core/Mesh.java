package edu.uta.futureye.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.util.Constant;
import edu.uta.futureye.util.MultiKey;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.list.EdgeList;
import edu.uta.futureye.util.list.ElementList;
import edu.uta.futureye.util.list.FaceList;
import edu.uta.futureye.util.list.NodeList;
import edu.uta.futureye.util.list.ObjList;

public class Mesh {
	//Global node list
	protected NodeList nodeList = new NodeList();
	
	//Global element list
	protected ElementList eleList = new ElementList();
	
	//Global edge list
	protected EdgeList edgeList = null;
	
	//Global face list
	protected FaceList faceList = null;
	
	public EdgeList getEdgeList() {
		return edgeList;
	}
	public void setEdgeList(EdgeList edgeList) {
		this.edgeList = edgeList;
	}
	
	public FaceList getFacelist() {
		return faceList;
	}
	public void setFacelist(FaceList facelist) {
		this.faceList = facelist;
	}

	//Global volume list
	//??? protected VolumeList volumeList = null;
	
	public static double eps = 1e-6;
	
	public NodeList getNodeList() {
		return nodeList;
	}
	public ElementList getElementList() {
		return eleList;
	}
	public void addNode(Node n) {
		nodeList.add(n);
	}	
	public void addElement(Element e) {
		eleList.add(e);
		e.globalIndex = eleList.size();
	}
	public void clearAll() {
		nodeList.clear();
		eleList.clear();
	}
	
	public void computeNodesBelongToElement() {
		System.out.println("computeNodesBelongToElement...");
		
//		for(int i=1;i<=nodeList.size();i++) {
//			for(int j=1;j<=eleList.size();j++) {
//				Node node = nodeList.at(i);
//				Element e = eleList.at(j);
//				if(e.isInElement(node))
//					node.belongToElements.add(e);
//			}
//			if(i%10==0)
//				System.out.println(String.format("%.1f", 100.0*i/nodeList.size())+"%");
//		}
		
		//New algorithm
		for(int i=1;i<=nodeList.size();i++) {
			nodeList.at(i).belongToElements.clear();
		}
		for(int i=1;i<=eleList.size();i++) {
			Element e = eleList.at(i);
			for(int j=1;j<=e.nodes.size();j++) {
				e.nodes.at(j).addBelongToElements(e);
			}
		}
		
		System.out.println("computeNodesBelongToElement done!");
	}
	
	/**
	 * ��ӱ߽�������
	 * @param nodeType
	 * @param fun ���ƺ�����fun(x)>0�߽�㣬fun(x)<=0�ڵ㣬xΪ�������
	 */
	public void addBorderType(NodeType nodeType, Function fun) {
		
	}
	
	public void addBorderType(NodeType nodeType, Node node) {
		
	}
	
	/**
	 * Mark border node type according to mapNTF
	 * 
	 * ��Ǳ߽������ͣ����Ḳ���ѱ�ǹ����͵ı߽��㣬
	 * �����Ҫ�޸ģ��ȵ���clearBorderNodeMark()
	 * 
	 * TODO
	 * �������������߽������ͣ���α�ǣ�
	 * 
	 * @param mapNTF
	 */
	public void markBorderNode(Map<NodeType,Function> mapNTF) {
		System.out.println("markBorderNode...");
		for(int i=1;i<=nodeList.size();i++) {
			Node node = nodeList.at(i);
			if(node.belongToElements.size()==0) {
				System.out.println("ERROR: Call computeNodesBelongToElement() first!");
			} else if(node instanceof NodeRefined && ((NodeRefined) node).isHangingNode()) {
				//TODO Hanging �������Ϊ�ڵ㣬���������취��
				node.setNodeType(NodeType.Inner); 
			} else {
				 //������nodeΪ���㣬��Χ��Ԫ�����֮�γɵļнǽǶȣ����Ϊ360�ȣ�2*PI������˵�����ڵ�
				double sum = 0.0;
				double coef = 0;
				if(node.coords().length==2) {
					coef = 2;
					for(int j=1;j<=node.belongToElements.size();j++) {
						sum += node.belongToElements.at(j).getAngleInElement2D(node);
					}
				} else if(node.coords().length==3) {
					coef = 4;
					for(int j=1;j<=node.belongToElements.size();j++) {
						sum += node.belongToElements.at(j).getUnitSphereTriangleArea(node);
					}
				}
				if(Math.abs(sum-coef*Math.PI) <= eps) { //2D=2*PI��3D=4*PI�����ڲ����
					node.setNodeType(NodeType.Inner); 
				} else { //�����Ǳ߽���
					//System.out.println("Border Node:"+node.globalIndex);
					for(Entry<NodeType,Function> entry : mapNTF.entrySet()) {
						NodeType nodeType = entry.getKey();
						Function fun = entry.getValue();
						if(fun != null) {
							Variable v = new Variable();
							int ic = 1;
							for(String vn : fun.varNames())
								v.set(vn, node.coord(ic++));
							if(fun.value(v) > 0)
								node.setNodeType(nodeType);
						} else {
							if(node.getNodeType() == null)
								node.setNodeType(nodeType);
						}
					}
				}
			}
		}	
		System.out.println("markBorderNode done!");
	}	
	
	public void clearBorderNodeMark() {
		for(int i=1;i<=nodeList.size();i++) {
			Node node = nodeList.at(i);
			if(node.getNodeType() != NodeType.Inner)
				node.setNodeType(null);
		}
	}
	
	
	/**
	 * �ж������Ƿ�������node
	 * @param node
	 * @return null if not contains the node
	 */
	public Node containNode(Node node) {
		for(int i=1;i<=nodeList.size();i++) {
			int nDim = node.dim();
			boolean same = true;
			for(int j=1;j<=nDim;j++) {
				if(Math.abs(node.coord(j)-nodeList.at(i).coord(j)) > Constant.eps) {
					same = false;
					break;
				}
			}
			if(same) 
				return nodeList.at(i);
		}
		return null;
	}
	
	/**
	 * ��ȡ��������ӽ��Ľ��
	 * @param coord
	 * @param threshold
	 * @return
	 */
	public Node getNodeByCoord(double[] coord, double threshold) {
		for(int i=1;i<=nodeList.size();i++) {
			int nDim = nodeList.at(1).dim();
			boolean same = true;
			for(int j=1;j<=nDim;j++) {
				if(Math.abs(coord[j-1]-nodeList.at(i).coord(j)) > threshold) {
					same = false;
					break;
				}
			}
			if(same)
				return nodeList.at(i);
		}
		return null;
	}
	
	/**
	 * ��ȡ�����������ĵ�Ԫ����ά��
	 * @param coord
	 * @return
	 */
	public Element getElementByCoord(double[] coord) {
		Node node = new Node(2);
		node.set(0, coord);
		for(int i=1;i<=eleList.size();i++) {
			Element e = eleList.at(i);
			if(e.isCoordInElement(coord))
				return e;
		}
		return null;
	}
	
	public void computeNeiborNode() {
		for(int i=1;i<=nodeList.size();i++) {
			nodeList.at(i).neighbors.clear();
		}
		for(int i=1;i<=nodeList.size();i++) {
			Node node = nodeList.at(i);
			if(node.belongToElements.size()==0) {
				Exception e = new Exception("Call computeNodesBelongToElement() first!");
				e.printStackTrace();
				return;
			}
			else {
				ElementList eList = node.belongToElements;
				for(int j=1;j<=eList.size();j++) {
					NodeList nList = eList.at(j).nodes;
					for(int k=1;k<=nList.size();k++) {
						Node nbNode = nList.at(k);
						if(nbNode.globalIndex != node.globalIndex)
							node.neighbors.add(nbNode);
					}
				}
			}
		}
	}
	
	public void computeNeighborElement() {
		for(int i=1;i<=eleList.size();i++) {
			eleList.at(i).neighbors.clear();
		}
		for(int i=1;i<=nodeList.size();i++) {
			Node node = nodeList.at(i);
			if(node.belongToElements.size()==0) {
				Exception e = new Exception("Call computeNodesBelongToElement() first!");
				e.printStackTrace();
				return;
			}
			else {
				ElementList eList = node.belongToElements;
				for(int j=1;j<=eList.size();j++) {
					for(int k=1;k<=eList.size();k++) {
						Element e1 = eList.at(j);
						Element e2 = eList.at(k);
						if(e1.equals(e2))
							continue;
						if(isNeighbor(e1,e2)) {
							e1.addNeighborElement(e2);
							e2.addNeighborElement(e1);
						}
					}
				}
			}
		}
		
	}
	
	/**
	 * �������������ȫ�ֱ�
	 * 
	 */
	public void computeGlobalEdge() {
		Map<MultiKey, Edge> map = new HashMap<MultiKey, Edge>();
		for(int i=1;i<=eleList.size();i++) {
			Element e = eleList.at(i);
			ObjList<EdgeLocal> localEdges = e.edges();//��Ԫe�ľֲ����б�
			for(int j=1;j<=localEdges.size();j++) {
				EdgeLocal localEdge = localEdges.at(j);//�ֲ���
				MultiKey mkey = new MultiKey(
								localEdge.getVertices().at(1).globalNode().globalIndex,
								localEdge.getVertices().at(2).globalNode().globalIndex
						);
				Edge globalEdge = map.get(mkey);
				if(globalEdge == null) {
					globalEdge = localEdge.buildEdge();
					map.put(mkey, globalEdge);
				}
				//update global edge
				localEdge.globalEdge = globalEdge;
			}
		}
		//Ϊmesh.edgeList��ȫ�ֱߣ���ֵ������ȫ�ֱ���������ţ�
		this.edgeList = new EdgeList();
		int globalIndex = 1;
		for(Entry<MultiKey, Edge> entry : map.entrySet()) {
			//globalIndex��ȫ�ֱ���������ţ�
			//globalIndex: Global index of Global edges
			entry.getValue().setGlobalIndex(globalIndex++);
			this.edgeList.add(entry.getValue());
		}
	}
	
	/**
	 * �������������ȫ����
	 * 
	 */
	public void computeGlobalFace() {
		Map<Set<Integer>, Face> map = new HashMap<Set<Integer>, Face>();
		for(int i=1;i<=eleList.size();i++) {
			Element e = eleList.at(i);
			//�����ֲ��棬����HashMap��face�������жϣ��õ�ȫ����
			ObjList<FaceLocal> localFaces = e.faces();
			for(int j=1; j<=localFaces.size(); j++) {
				FaceLocal localFace = localFaces.at(j);
				Set<Integer> nodeSet = new HashSet<Integer>();
				ObjList<Vertex> vertices = localFace.getVertices();
				for(int n=1;n<=vertices.size();n++) {
					nodeSet.add(vertices.at(n).globalNode().globalIndex);
				}
				Face gobalFace = map.get(nodeSet);
				if(gobalFace == null) {
					gobalFace = localFace.buildFace();
					map.put(nodeSet, gobalFace);
				}
				//update global edge
				localFace.globalFace = gobalFace;
			}
		}
		//Ϊmesh.faceList��ȫ���棩��ֵ������ȫ������������ţ�
		this.faceList = new FaceList();
		int globalIndex = 1;
		for(Entry<Set<Integer>, Face> entry : map.entrySet()) {
			//globalIndex��ȫ������������ţ�
			//globalIndex: Global index of Global faces
			entry.getValue().setGlobalIndex(globalIndex++);
			this.faceList.add(entry.getValue());
		}
	}
	
	/**
	 * �������ڶ�ά
	 * @param e1
	 * @param e2
	 * @return
	 */
	//2011-02-19
	public boolean isNeighbor(Element e1, Element e2) {
//		int counter = 0;
//		for(int i=1;i<=e1.nodes.size();i++) {
//			for(int j=1;j<=e2.nodes.size();j++) {
//				//�������������(��������Ӧ�������ڴ���hanging node���������ڴֵ�Ԫ����˸÷����ж�����)
//				if(e1.nodes.at(i).globalIndex == e2.nodes.at(j).globalIndex) {
//					counter++;
//					if(counter==2) return true; 
//				}
//			}
//		}
		ObjList<EdgeLocal> e1EdgeList = e1.edges();
		ObjList<EdgeLocal> e2EdgeList = e2.edges();
		for(int i=1;i<=e1EdgeList.size();i++) {
			for(int j=1;j<=e2EdgeList.size();j++) {
				NodeList e1EndNodes = e1EdgeList.at(i).globalEdge.getEndNodes();
				NodeList e2EndNodes = e2EdgeList.at(j).globalEdge.getEndNodes();
				if(Utils.isLineOverlap(e1EndNodes.at(1), e1EndNodes.at(2), 
						e2EndNodes.at(1), e2EndNodes.at(2)))
					return true;
			}
		}
		return false;
	}
	
	/**
	 * 
	 */
	Mesh copy() {
		//TODO
		return null;
	}
	
}
