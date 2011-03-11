package edu.uta.futureye.core;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import edu.uta.futureye.core.geometry.GeoEntity;
import edu.uta.futureye.core.geometry.GeoEntity0D;
import edu.uta.futureye.core.geometry.GeoEntity1D;
import edu.uta.futureye.core.geometry.GeoEntity2D;
import edu.uta.futureye.core.geometry.GeoEntity3D;
import edu.uta.futureye.core.geometry.Point;
import edu.uta.futureye.core.geometry.topology.HexahedronTp;
import edu.uta.futureye.core.geometry.topology.RectangleTp;
import edu.uta.futureye.core.geometry.topology.TetrahedronTp;
import edu.uta.futureye.core.geometry.topology.TriangleTp;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.util.Constant;
import edu.uta.futureye.util.FutureyeException;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.list.DOFList;
import edu.uta.futureye.util.list.ElementList;
import edu.uta.futureye.util.list.NodeList;
import edu.uta.futureye.util.list.ObjList;
import edu.uta.futureye.util.list.VertexList;

/**
 * Element of a triangulation
 * �ʷֵ�Ԫ
 * 
 * @author liuyueming
 *
 */
public class Element {
	/**
	 * Global index of this element
	 * ��Ԫȫ�ֱ��
	 */
	public int globalIndex = 0;
	
	/**
	 * Node list of this element
	 * ��Ԫ����б�
	 */
	public NodeList nodes = new NodeList();
	
	/**
	 * Neighbors of this element
	 * ���ڵ�Ԫ
	 */
	public ElementList neighbors = new ElementList();
	
	/**
	 * A map between local index of a node and it's corresponding DOFList
	 * �뵥Ԫ����Ӧ��DOFList
	 */
	protected Map<Integer,DOFList> nodeDOFList;
	protected Map<Integer,DOFList> edgeDOFList;
	protected Map<Integer,DOFList> faceDOFList;
	protected DOFList volumeDOFList;
	
	/**
	 * ��Ԫ����ʵ�壬�����塢�桢�ߡ�������Ϣ
	 */
	protected GeoEntity0D geoEntity = null;
	public GeoEntity0D getGeoEntity() {
		return geoEntity;
	}

	/**
	 * ��Ԫά�ȣ�1D, 2D or 3D
	 */
	protected int eleDim = 0;
	public int eleDim() {
		return eleDim;
	}

	protected Function jac = null;

	////////////////////////////////////////////////////////////////////
	/**
	 * ����һ����Element���������Ĭ�ϰ������±�ţ�
	 * 
	 * һά��Ԫ��
	 * 
	 * 1---2
	 * 
	 * 1--3--2
	 * 
	 * 1--3--4--2
	 * 
	 * ��ά�����ε�Ԫ:
	 *  3
	 *  | \
	 *  |  \
	 *  1---2 
	 *  
	 *  3
	 *  | \
	 *  |  \
	 *  6   5
	 *  |    \
	 *  |     \
	 *  1--4---2 
	 *
	 *  3
	 *  | \
	 *  |  \
	 *  6   8
	 *  |    \
	 *  9     5
	 *  |      \
	 *  |       \
	 *  1--4--7--2 
	 *
	 * ��ά�ı��ε�Ԫ��
	 * 4----3
	 * |    |
	 * |    |
	 * 1----2
	 * 
	 * 4--7--3
	 * |     |
	 * 8     6
	 * |     |
	 * 1--5--2
	 * 
	 * 4--11--7--3
	 * |         |
	 * 8         10
	 * |         |
	 * 12        6
	 * |         |
	 * 1-- 5--9--2
	 * 
	 * ��ά�����嵥Ԫ��
	 *    4
	 *   /|\
	 *  / | \
	 * /  |  \
	 *1---|---3
	 * \  |  /
	 *  \ | /
	 *   \|/
	 *    2
	 * 
	 * ��ά�����嵥Ԫ��
	 *   4--------3
	 *  /|       /|
	 * 1--------2 |
	 * | |      | |
	 * | |      | |
	 * | 8------|-7
	 * | /      | /
	 * 5--------6
	 *
	 *
	 * @param node
	 */
	public Element(NodeList nodes) {
		buildElement(nodes);
	}
	
	/**
	 * ����һ��һ��Element����Ҫ�ṩ��Ԫ������Ϣ
	 * @param node
	 */
	public Element(GeoEntity0D geoEntity) {
		this.geoEntity = geoEntity;
		this.nodes.addAll(getNodeList(geoEntity));
		if(geoEntity instanceof GeoEntity2D<?,?>)
			this.eleDim = 2;
		else if(geoEntity instanceof GeoEntity3D<?,?,?>)
			this.eleDim = 3;
		else
			this.eleDim = 1;
	}
	
	public static ObjList<NodeLocal> getLocalNodeList1D(
			GeoEntity1D<NodeLocal> edge) {
		ObjList<NodeLocal> localNodes = new ObjList<NodeLocal>();
		ObjList<Vertex> vertices = edge.getVertices();
		for(int i=1;i<=vertices.size();i++)
			localNodes.add(vertices.at(i).localNode());
		ObjList<NodeLocal> edgeNodes = edge.getEdgeNodes();
		if(edgeNodes != null) {
			for(int i=1;i<=edgeNodes.size();i++)
				localNodes.add(edgeNodes.at(i));
		}
		return localNodes;
	}
	
	public static ObjList<NodeLocal> getLocalNodeList2D(
			GeoEntity2D<EdgeLocal,NodeLocal> face) {
		ObjList<NodeLocal> localNodes = new ObjList<NodeLocal>();
		ObjList<Vertex> vertices = face.getVertices();
		for(int i=1;i<=vertices.size();i++)
			localNodes.add(vertices.at(i).localNode());
		localNodes.addAll(getInnerLocalNodeList2D(face));
		return localNodes;
	}	
	public static ObjList<NodeLocal> getInnerLocalNodeList2D(
			GeoEntity2D<EdgeLocal,NodeLocal> face) {
		ObjList<NodeLocal> localNodes = new ObjList<NodeLocal>();
		ObjList<EdgeLocal> edges = face.getEdges();
		for(int i=1;i<=edges.size();i++)  {
			localNodes.addAll(edges.at(i).getEdgeNodes());
		}
		ObjList<NodeLocal> faceNodes = face.getFaceNodes();
		if(faceNodes != null) {
			for(int i=1;i<=faceNodes.size();i++)
				localNodes.add(faceNodes.at(i));
		}
		return localNodes;		
	}
	
	public static ObjList<NodeLocal> getLocalNodeList3D(
			GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal> volume) {
		ObjList<NodeLocal> localNodes = new ObjList<NodeLocal>();
		ObjList<Vertex> vertices = volume.getVertices();
		for(int i=1;i<=vertices.size();i++)
			localNodes.add(vertices.at(i).localNode());
		localNodes.addAll(getInnerLocalNodeList3D(volume));
		return localNodes;		
	}	
	public static ObjList<NodeLocal> getInnerLocalNodeList3D(
			GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal> volume) {
		ObjList<NodeLocal> localNodes = new ObjList<NodeLocal>();
		ObjList<FaceLocal> faces = volume.getFaces();
		for(int i=1;i<=faces.size();i++) 
			localNodes.addAll(getInnerLocalNodeList2D(faces.at(i)));
		ObjList<NodeLocal> volumeNodes = volume.getVolumeNodes();
		if(volumeNodes != null) {
			for(int i=1;i<=volumeNodes.size();i++)
				localNodes.add(volumeNodes.at(i));
		}
		return localNodes;		
	}	

	@SuppressWarnings("unchecked")
	public static NodeList getNodeList(GeoEntity geoEntity) {
		ObjList<NodeLocal> localNodes = null;
		if(geoEntity instanceof GeoEntity1D<?>) {
			localNodes = getLocalNodeList1D(
					(GeoEntity1D<NodeLocal>) geoEntity);
		} else if(geoEntity instanceof GeoEntity2D<?,?>) {
			localNodes = getLocalNodeList2D(
					(GeoEntity2D<EdgeLocal, NodeLocal>) geoEntity);
		} else if(geoEntity instanceof GeoEntity3D<?,?,?>) {
			localNodes = getLocalNodeList3D(
					(GeoEntity3D<FaceLocal, EdgeLocal, NodeLocal>) geoEntity);
		} else {
			FutureyeException ex = new FutureyeException("Error: Can not build NodeList, geoEntity="+
					geoEntity.getClass().getName());
			ex.printStackTrace();
			System.exit(0);
		}
//		Object[] a=localNodes.toArray();
//		Arrays.sort(a, (Comparator)new Comparator<NodeLocal>(){
//			@Override
//			public int compare(NodeLocal o1, NodeLocal o2) {
//				if(o1.localIndex > o2.localIndex)
//					return 1;
//				else
//					return -1;
//			}
//		});
		//���ص�ȫ�ֽ�㰴�վֲ����ı��˳�򷵻�
		List<NodeLocal> list = localNodes.toList();
		Collections.sort(list, new Comparator<NodeLocal>(){
			@Override
			public int compare(NodeLocal o1, NodeLocal o2) {
				//����
				if(o1.localIndex > o2.localIndex)
					return 1;
				else
					return -1;
			}
		});
		NodeList nodes = new NodeList();
		for(int i=0;i<list.size();i++) {
			nodes.add(list.get(i).globalNode);
		}
		return nodes;
	}
	
	/**
	 * ����һ�����޵�Ԫ
	 * ע�⣺�÷��������ĵ�Ԫ������Ϣ��������ȫ��Edge��2D,3D����ȫ��Face��3D����
	 * ȫ����Ϣ��Ҫ����Mesh�����غ�������õ���
	 * 
	 * @param nodes
	 */
	protected void buildElement(NodeList nodes) {
		this.nodes.clear();
		this.nodes.addAll(nodes);
		
		//�Խ���ά�Ⱦ�����Ԫ��ά��
		this.eleDim = nodes.at(1).dim();
		int n = nodes.size();
		if(this.eleDim == 1) {
			GeoEntity1D<NodeLocal> entity = new GeoEntity1D<NodeLocal>();
			if(n >= 2) {
				entity.addVertex(new Vertex(1,new NodeLocal(1,nodes.at(1))));
				entity.addVertex(new Vertex(2,new NodeLocal(2,nodes.at(n))));
				if(n > 2) {
					for(int i=2;i<n;i++) //edge nodes number from 3,4,5,...
						entity.addEdgeNode(new NodeLocal(1+i,nodes.at(i)));
				}
			} else {
				FutureyeException ex = new FutureyeException("Error: Number of nodes should be at least 2");
				ex.printStackTrace();
				System.exit(0);
			}
			this.geoEntity = entity;
			
		} else if(this.eleDim == 2) {
			GeoEntity2D<EdgeLocal,NodeLocal> entity = 
						new GeoEntity2D<EdgeLocal,NodeLocal>();
			int [][]edgesTp = null;
			if(n == 3) {
				//���������ε�Ԫ
				TriangleTp triTp = new TriangleTp();
				entity.setTopology(triTp);
				edgesTp = triTp.getEdges();
				for(int i=1;i<=nodes.size();i++)
					entity.addVertex(new Vertex(i,new NodeLocal(i,nodes.at(i))));
			} else if(n == 4) {
				//�����ı��ε�Ԫ
				RectangleTp rectTp = new RectangleTp();
				entity.setTopology(rectTp);
				edgesTp = rectTp.getEdges();
				for(int i=1;i<=nodes.size();i++)
					entity.addVertex(new Vertex(i,new NodeLocal(i,nodes.at(i))));
			} else if(n == 6) {
				
			} else if(n == 8) {
				
			} else if(n ==9) {
				
			} else if(n == 12) {
				
			} else {
				FutureyeException ex = new FutureyeException("Error: Not supported element, try use Element(GeoEntity geoEntity)");
				ex.printStackTrace();
				System.exit(0);
			}
			for(int i=0;i<edgesTp.length;i++) {
				EdgeLocal el = new EdgeLocal(i+1,this);
				for(int j=0;j<edgesTp[i].length;j++) {
					el.addVertex(
							new Vertex(edgesTp[i][j],
							new NodeLocal(edgesTp[i][j],nodes.at(edgesTp[i][j])))
							);
				}
				entity.addEdge(el);
			}
			this.geoEntity = entity;
			
		} else if(this.eleDim == 3) {
			GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal> entity = 
						new GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal>();
			int [][]edgesTp = null;
			int [][]facesTp = null;
			if(n == 4) {
				//���������嵥Ԫ
				TetrahedronTp tetTp = new TetrahedronTp();
				entity.setTopology(tetTp);
				edgesTp = tetTp.getEdges();
				facesTp = tetTp.getFaces();
				for(int i=1;i<=tetTp.getVertices().length;i++)
					entity.addVertex(new Vertex(i,new NodeLocal(i,nodes.at(i))));
			} else if(n == 8) {
				//���������嵥Ԫ
				HexahedronTp hexTp = new HexahedronTp();
				entity.setTopology(hexTp);
				edgesTp = hexTp.getEdges();
				facesTp = hexTp.getFaces();
				for(int i=1;i<=hexTp.getVertices().length;i++)
					entity.addVertex(new Vertex(i,new NodeLocal(i,nodes.at(i))));
			} else {
				FutureyeException ex = new FutureyeException("Error: Not supported element, try use Element(GeoEntity geoEntity)");
				ex.printStackTrace();
				System.exit(0);
			}
			for(int k=0;k<facesTp.length;k++) {
				FaceLocal fl = new FaceLocal(k+1,this);
				for(int i=0;i<facesTp[k].length;i++) {
					fl.addVertex(
							new Vertex(facesTp[k][i],
									new NodeLocal(facesTp[k][i],nodes.at(facesTp[k][i])))
							);
				}
				for(int i=0;i<edgesTp.length;i++) {
					EdgeLocal el = new EdgeLocal(i+1,this);
					for(int j=0;j<edgesTp[i].length;j++) {
						el.addVertex(
								new Vertex(edgesTp[i][j],
										new NodeLocal(edgesTp[i][j],nodes.at(edgesTp[i][j])))
								);
					}
					fl.addEdge(el);
				}
				entity.addFace(fl);
			}
			this.geoEntity = entity;			
			
		} else {
			FutureyeException ex = new FutureyeException("Error: Node dim should be 1,2,3 dim="+eleDim);
			ex.printStackTrace();
			System.exit(0);
		}
	}
	
	/**
	 * If you do some changes on this.geoEntity, call this method to 
	 * update the information in Element
	 * 
	 */
	public void applyChange() {
		this.nodes.clear();
		this.nodes.addAll(getNodeList(this.geoEntity));
	}
	
	/**
	 * ��ȡ��Ԫ�ļ��ζ���
	 * Ӧ�ã�
	 * 1.��������任
	 * 2.�����������
	 * 3.�ȵ�
	 * @return
	 */
	public VertexList vertices() {
		return this.geoEntity.getVertices();
	}
	
	@SuppressWarnings("unchecked")
	public ObjList<EdgeLocal> edges() {
		if(this.eleDim == 2) {
			GeoEntity2D entity = (GeoEntity2D)this.geoEntity;
			return entity.getEdges();
		} else {
			FutureyeException ex = new FutureyeException("Error: "+this.geoEntity.getClass().getName());
			ex.printStackTrace();
			System.exit(0);
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public ObjList<FaceLocal> faces() {
		if(this.eleDim == 3) {
			GeoEntity3D entity = (GeoEntity3D)this.geoEntity;
			return entity.getFaces();
		} else {
			FutureyeException ex = new FutureyeException("Error: "+this.geoEntity.getClass().getName());
			ex.printStackTrace();
			System.exit(0);
		}
		return null;
	}
	
	
	////////////////////////////////////////////////////////////////////

	
	
	/**
	 * ���һ�����������ɶȣ�1D/2D/3D��
	 * @param localNodeIndex
	 * @param dof
	 */
	public void addNodeDOF(int localNodeIndex,DOF dof) {
		if(nodeDOFList == null)
			nodeDOFList = new LinkedHashMap<Integer,DOFList>();
		DOFList dofList = nodeDOFList.get(localNodeIndex);
		if(dofList == null) {
			dofList = new DOFList();
			nodeDOFList.put(localNodeIndex, dofList);
		}
		//2010-10-11 DOF��������Node
		dof.setOwner(this.nodes.at(localNodeIndex));
		dofList.add(dof);
	}
	
	/**
	 * ���һ����������ɶȣ�2D/3D��
	 * @param localEdgeIndex
	 * @param dof
	 */
	public void addEdgeDOF(int localEdgeIndex,DOF dof) {
		if(edgeDOFList == null)
			edgeDOFList = new LinkedHashMap<Integer,DOFList>();
		DOFList dofList = edgeDOFList.get(localEdgeIndex);
		if(dofList == null) {
			dofList = new DOFList();
			edgeDOFList.put(localEdgeIndex, dofList);
		}
		//DOF��������Edge
		dof.setOwner(this.edges().at(localEdgeIndex));
		dofList.add(dof);		
	}
	
	/**
	 * ���һ����������ɶȣ�3D��
	 * @param localFaceIndex
	 * @param dof
	 */
	public void addFaceDOF(int localFaceIndex,DOF dof) {
		if(faceDOFList == null)
			faceDOFList = new LinkedHashMap<Integer,DOFList>();
		DOFList dofList = faceDOFList.get(localFaceIndex);
		if(dofList == null) {
			dofList = new DOFList();
			faceDOFList.put(localFaceIndex, dofList);
		}
		//DOF��������Face
		dof.setOwner(this.faces().at(localFaceIndex));
		dofList.add(dof);
	}
	
	/**
	 * Alias of addElementDOF()
	 * @param dof
	 */
	public void addVolumeDOF(DOF dof) {
		if(volumeDOFList == null)
			volumeDOFList = new DOFList();
		//DOF��������Element
		//TODO???�����ɶȵ�owner��this.geoEntity???
		dof.setOwner(this.geoEntity);
		volumeDOFList.add(dof);
	}
	
	/**
	 * ���һ����Ԫ������ɶȣ�1D/2D/3D����
	 * ����1D��Ϊ��Ԫ���ϵ����ɶ�
	 * ����2D��Ϊ��Ԫ���ϵ����ɶ�
	 * ����3D��Ϊ��Ԫ���ϵ����ɶ�
	 * @param dof
	 */
	public void addElementDOF(DOF dof) {
		addVolumeDOF(dof);
	}
	
	
	/**
	 * ��ȡ��Ԫ���localNodeIndex��Ӧ�����ɶ��б�
	 * @param localNodeIndex
	 * @return
	 */
	public DOFList getNodeDOFList(int localNodeIndex) {
		if(nodeDOFList == null) return null;
		DOFList dofList = nodeDOFList.get(localNodeIndex);
		return dofList;
	}
	
	/**
	 * ��ȡ��Ԫ��localEdgeIndex��Ӧ�����ɶ��б�
	 * @param localEdgeIndex
	 * @return
	 */
	public DOFList getEdgeDOFList(int localEdgeIndex) {
		if(edgeDOFList == null) return null;
		DOFList dofList = edgeDOFList.get(localEdgeIndex);
		return dofList;
	}	
	/**
	 * ��ȡ��Ԫ��localFaceIndex��Ӧ�����ɶ��б�
	 * @param localFaceIndex
	 * @return
	 */
	public DOFList getFaceDOFList(int localFaceIndex) {
		if(faceDOFList == null) return null;
		DOFList dofList = faceDOFList.get(localFaceIndex);
		return dofList;
	}
	/**
	 * ��ȡ��Ԫ���Ӧ�����ɶ��б�
	 * @return
	 */
	public DOFList getVolumeDOFList() {
		return volumeDOFList;
	}
	/**
	 * ==getVolumeDOFList()
	 * @return
	 */
	public DOFList getElementDOFList() {
		return volumeDOFList;
	}
	
	public DOFList getAllNodeDOFList(DOFOrder order) {
		DOFList rlt = new DOFList();
		if(nodeDOFList != null) {
			for(Entry<Integer,DOFList> entry : nodeDOFList.entrySet()) {
				rlt.addAll(entry.getValue());
			}
		}
		return rlt;
	}
	public DOFList getAllEdgeDOFList(DOFOrder order) {
		DOFList rlt = new DOFList();
		if(edgeDOFList != null) {
			for(Entry<Integer,DOFList> entry : edgeDOFList.entrySet()) {
				rlt.addAll(entry.getValue());
			}
		}
		return rlt;
	}
	public DOFList getAllFaceDOFList(DOFOrder order) {
		DOFList rlt = new DOFList();
		if(faceDOFList != null) {
			for(Entry<Integer,DOFList> entry : faceDOFList.entrySet()) {
				rlt.addAll(entry.getValue());
			}
		}
		return rlt;
	}
	public DOFList getAllVolumeDOFList(DOFOrder order) {
		DOFList rlt = new DOFList();
		if(volumeDOFList != null) {
			rlt.addAll(volumeDOFList);
		}
		return rlt;
	}
	
	/**
	 * ��ȡ��Ԫ�����е����ɶ�
	 * Ĭ�Ϲ���
	 * ���յ㡢�ߡ��桢���˳�򽫾ֲ����ɶȱ�Ŵ�С��������
	 * @param ���ɶ�����ʽ
	 * @return
	 */
	public DOFList getAllDOFList(DOFOrder order) {
		DOFList rlt = new DOFList();
		if(nodeDOFList != null) {
			for(Entry<Integer,DOFList> entry : nodeDOFList.entrySet()) {
				rlt.addAll(entry.getValue());
			}
		}
		if(edgeDOFList != null) {
			for(Entry<Integer,DOFList> entry : edgeDOFList.entrySet()) {
				rlt.addAll(entry.getValue());
			}
		}
		if(faceDOFList != null) {
			for(Entry<Integer,DOFList> entry : faceDOFList.entrySet()) {
				rlt.addAll(entry.getValue());
			}
		}
		if(volumeDOFList != null) {
			rlt.addAll(volumeDOFList);
		}
		return rlt;
	}
	
	public int getNodeDOFNumber() {
		if(nodeDOFList == null) return 0;
		int nTotal = 0;
		for(Entry<Integer,DOFList> entry : nodeDOFList.entrySet()) {
			nTotal += entry.getValue().size();
		}
		return nTotal;
	}
	public int getEdgeDOFNumber() {
		if(edgeDOFList == null) return 0;
		int nTotal = 0;
		for(Entry<Integer,DOFList> entry : edgeDOFList.entrySet()) {
			nTotal += entry.getValue().size();
		}
		return nTotal;
	}
	public int getFaceDOFNumber() {
		if(faceDOFList == null) return 0;
		int nTotal = 0;
		for(Entry<Integer,DOFList> entry : faceDOFList.entrySet()) {
			nTotal += entry.getValue().size();
		}
		return nTotal;
	}
	/**
	 * Alias of getElementDOFNumber()
	 * @return
	 */
	public int getVolumeDOFNumber() {
		if(volumeDOFList == null) return 0;
		return volumeDOFList.size();
	}
	/**
	 * ��ȡ��Ԫ������ɶ�������1D/2D/3D����
	 * ����1D��Ϊ��Ԫ���ϵ����ɶ�����
	 * ����2D��Ϊ��Ԫ���ϵ����ɶ�����
	 * ����3D��Ϊ��Ԫ���ϵ����ɶ�����
	 * @return
	 */
	public int getElementDOFNumber() {
		if(volumeDOFList == null) return 0;
		return volumeDOFList.size();
	}

	/**
	 * ��ȡ���н�㡢�ߡ�������ϵ����ɶ�����
	 * @return
	 */
	public int getAllDOFNumber() {
		int nTotal = 0;
		nTotal += this.getNodeDOFNumber();
		nTotal += this.getEdgeDOFNumber();
		nTotal += this.getFaceDOFNumber();
		nTotal += this.getVolumeDOFNumber();
		return nTotal;
	}
	
	/**
	 * �ֲ����ɶȱ����ȫ�����ɶȱ��ת��
	 * Ĭ�Ϲ���
	 * ���յ㡢�ߡ��桢���˳�򽫾ֲ����ɶȱ�Ŵ�С��������
	 * @param local
	 * @return
	 */
	public int local2GlobalDOFIndex(int local) {
		int base = 0;
		if(nodeDOFList != null) {
			for(int j=1;j<=nodes.size();j++) {
				DOFList list = nodeDOFList.get(j);
				if(list != null) {
			 		for(int i=1;i<=list.size();i++) {
			 			DOF dof = list.at(i);
						if(dof.getLocalIndex() == local-base)
							return dof.getGlobalIndex();
					}
		 		}
			}
			base += this.getNodeDOFNumber();
		}
		if(edgeDOFList != null) {
			ObjList<EdgeLocal> edges = this.edges();
			for(int j=1;j<=edges.size();j++) {
				DOFList list = edgeDOFList.get(j);
				if(list != null) {
			 		for(int i=1;i<=list.size();i++) {
			 			DOF dof = list.at(i);
						if(dof.getLocalIndex() == local-base)
							return dof.getGlobalIndex();
					}
		 		}
			}
			base += this.getEdgeDOFNumber();
		}
		if(faceDOFList != null) {
			ObjList<FaceLocal> faces = this.faces();
			for(int j=1;j<=faces.size();j++) {
				DOFList list = faceDOFList.get(j);
				if(list != null) {
			 		for(int i=1;i<=list.size();i++) {
			 			DOF dof = list.at(i);
						if(dof.getLocalIndex() == local-base)
							return dof.getGlobalIndex();
					}
		 		}
			}
			base += this.getFaceDOFNumber();
		}
		if(volumeDOFList != null) {
	 		for(int i=1;i<=volumeDOFList.size();i++) {
	 			DOF dof = volumeDOFList.at(i);
				if(dof.getLocalIndex() == local-base)
					return dof.getGlobalIndex();
			}
		}
		return 0;
	}
	
	public void clearAllDOF() {
		nodeDOFList.clear();
	}
	
	////////////////////////////////////////////////////////////////////
	
	/**
	 * For 1D element
	 * @return
	 */
	public double getElementLength() {
		return Utils.computeLength(nodes.at(1), 
				nodes.at(nodes.size()));
	}
	/**
	 * For 2D element
	 * @return
	 */
	public double getElementArea() {
		double area = 0.0;
		if(this.eleDim == 2) {
			//��ʹ��Node������Vertex������Ҫ��֤������ʱ��
			VertexList vertices = this.vertices();
			if(vertices.size() == 3) {
				area = Utils.getTriangleArea(vertices);
			} else if(vertices.size() == 4) {
				area = Utils.getRectangleArea(vertices);
			} else {
				area = Utils.getPolygonArea(vertices);
			}
		}
		return area;
	}
	
	/**
	 * For 3D element
	 * @return
	 */
	public double getElementVolume() {
		VertexList vertices = this.vertices();
		if(this.eleDim == 3 && vertices.size() == 4)
			return Utils.getTetrahedronVolume(vertices);
		else
			//TODO
		return 0.0;
	}
	
	/**
	 * Return true if exists an edge of this element that is on the border of the domain
	 * �ж��Ƿ�߽絥Ԫ�������ٴ��ڵ�Ԫ��һ��λ������߽���
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public boolean isBorderElement() {
		if(this.eleDim == 2) {
			GeoEntity2D<EdgeLocal,NodeLocal> entity = 
				(GeoEntity2D<EdgeLocal,NodeLocal>)this.geoEntity;
			ObjList<EdgeLocal> edges = entity.getEdges();
			for(int i=1;i<=edges.size();i++) {
				if(edges.at(i).isBorderEdge())
					return true;
			}
		} else if(this.eleDim == 3) {
			GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal> entity = 
				(GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal>)this.geoEntity;
			ObjList<FaceLocal> faces = entity.getFaces();
			for(int i=1;i<=faces.size();i++) {
				if(faces.at(i).isBorderFace())
					return true;
			}
		}
		return false;
 	}
	
	public NodeList getNodesByType(NodeType nodeType) {
		NodeList l = new NodeList();
		for(int i=1;i<=nodes.size();i++) {
			if(nodes.at(i).getNodeType() == nodeType) {
				l.add(nodes.at(i));
			}
		}
		return l;
	}
	
	/**
	 * ������nodeΪ���㣬�䵥Ԫ�������������֮�γɵļнǽǶ�
	 * ����Ҫ��nodeΪ��Ԫ�ϵ�һ�����
	 * 
	 * @param node
	 * @return
	 */
	public double getAngleInElement2D(Node node) {
		int li = getLocalIndex(node);
		int vn = this.geoEntity.getVertices().size();
		if(li <= vn) {
			Node l = nodes.at(li-1<1?vn:li-1);
			Node r = nodes.at(li+1>vn?1:li+1);
			return Utils.computeAngle2D(l, node, r, node);
		} else if(this.nodes.size()/vn == 2){
			Node l = nodes.at(li - vn);
			//TODO ����ģ�nodes.at(li - vn + 1)
			Node r = nodes.at( (li - vn + 1)>vn?1:(li - vn + 1));
			return Utils.computeAngle2D(l, node, r, node);
		} else {
			return 0.0; //TODO
		}
		
	}
	
	/**
	 * �����������嵥Ԫ
	 * 
	 * ������nodeΪ���㣬�䵥Ԫ�������������֮�γɵĵ�λ���������ε������
	 * �������ж��Ƿ��ڵ㣬����ĳ����������е�Ԫ�ϵĽ����֮�γɵĵ�λ���������ε����ֻ��=4*PIʱΪ�ڵ�
	 * �������壺4*PI*r^2���������(4/3)*PI*r^3��
	 * ����Ҫ��nodeΪ��Ԫ�ϵ�һ�����
	 * 
	 * @param node
	 * @return
	 */	
	public double getUnitSphereTriangleArea(Node node) {
		final int [][] ary = {{0,0,0},{2,3,4},{3,4,1},{4,1,2},{1,2,3}};
		int li = getLocalIndex(node);
		int vn = this.geoEntity.getVertices().size();
		if(li <= vn) {
			return Utils.getSphereTriangleArea(1, node, 
					nodes.at(ary[li][0]), 
					nodes.at(ary[li][1]), 
					nodes.at(ary[li][2]));
		} else {
			return 0.0; //TODO
		}
	}
	
	public void updateJacobinLinear1D() {
		String[] fromVars = {"x","y"};
		String[] toVars = {"r"};
		//Coordinate transform and Jacbian on this border element
		CoordinateTransform transBorder = new CoordinateTransform(fromVars,toVars);
		transBorder.transformLinear1D(this);
		jac = (Function) transBorder.getJacobian1D();
		//TODO ��Ҫ��������޸ĺ�������
		jac = FC.c(transBorder.getJacobian1D().value(null));
	}

	public void updateJacobinLinear2D() {
		//Coordinate transform and Jacbian on this element
		CoordinateTransform trans = new CoordinateTransform(2);
		trans.transformLinear2D(this);
		//jac = (Function) trans.getJacobian2D();
		//TODO ��Ҫ��������޸ĺ�������
		jac = FC.c(trans.getJacobian2D().value(null));

//TODO adaptive��ʱ������		
//		List<FunctionDerivable> funs = trans.getTransformFunction(
//				trans.getTransformShapeFunctionByElement(this)
//					);
//		trans.setTransformFunction(funs);
//		jac = trans.getJacobian2D();
	}
	
	public void updateJacobinLinear3D() {
		//Coordinate transform and Jacbian on this element
		CoordinateTransform trans = new CoordinateTransform(3);
		
		//trans.transformLinear3D(this);
		//jac = (Function) trans.getJacobian3D();
		
		jac = trans.getJacobian3DFast(this);
		
		//System.out.println(jac.value(null));
		//System.out.println(trans.getJacobian3DFast(this).value(null));
	}
	
	/**
	 * �жϵ�Ԫά�ȣ����»�������任��Jacobin����
	 */
	public void updateJacobin() {
		if(this.eleDim == 2)
			this.updateJacobinLinear2D();
		else if(this.eleDim ==3 )
			this.updateJacobinLinear3D();
		else if(this.eleDim == 1)
			this.updateJacobinLinear1D();
		else {
			FutureyeException ex = new FutureyeException("updateJacobin: dim="+this.eleDim);
			ex.printStackTrace();
			System.exit(-1);
		}
	}
	
	/**
	 * Call updateJacobin() if null returned
	 * 
	 * @return
	 */
	public Function getJacobin() {
		return jac;
	}
	
	/**
	 * ��ȡ�߽絥Ԫ��������Ȼ�߽�ı߽����
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public ElementList getBorderElements() {
		ElementList el = new ElementList();
		if(this.eleDim == 2) {
			GeoEntity2D<EdgeLocal,NodeLocal> entity = 
				(GeoEntity2D<EdgeLocal,NodeLocal>)this.geoEntity;
			ObjList<EdgeLocal> edges = entity.getEdges();
			for(int i=1;i<=edges.size();i++) {
				if(edges.at(i).isBorderEdge())
					el.add(edges.at(i).changeToElement());
			}
		} else if(this.eleDim == 3) {
			GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal> entity = 
				(GeoEntity3D<FaceLocal,EdgeLocal,NodeLocal>)this.geoEntity;
			ObjList<FaceLocal> faces = entity.getFaces();
			for(int i=1;i<=faces.size();i++) {
				if(faces.at(i).isBorderFace())
					el.add(faces.at(i).changeToElement());
			}
		}
		return el;
	}
	
	/**
	 * ��ȡ�߽�������
	 * @return
	 */
	public NodeType getBorderNodeType() {
		if(this.eleDim == 2) {
			//��һ����ά��Ԫ���湹�������Element����ļ���ʵ��
			//��һ����ȫ�����󣬻�ȡ�������ı߽�����
			Face face = (Face)this.geoEntity;
			return face.getBorderType();
		} else if(this.eleDim == 1) {
			//��һ����ά��Ԫ�ı߹��������Element����ļ���ʵ��
			//��һ����ȫ�ֶ��󣬻�ȡ�ñ߶���ı߽�����
			Edge edge = (Edge)this.geoEntity;
			return edge.getBorderType();
		} else {
			FutureyeException ex = new FutureyeException("");
			ex.printStackTrace();
			System.exit(-1);
		}
		return null;
	}
	
	/**
	 * ����ȫ�ֽ��node���ظý���ڵ�Ԫ�ľֲ���������ţ���
	 * ����ý�㲻�ڵ�Ԫ�У�����0
	 * 
	 * ע�⣺����ı��˵�Ԫ��geoEntity���������applyChange()��
	 * ����ú������ܷ��ش���Ľ��
	 * 
	 * @param node
	 * @return
	 */
	public int getLocalIndex(Node node) {
		for(int i=1;i<=nodes.size();i++) {
			if(node.coordEquals(nodes.at(i)))
				return i;
		}
		return 0;
	}
	
	public Node getNode(Point p) {
		for(int i=1;i<=nodes.size();i++) {
			if(p.coordEquals(nodes.at(i)))
				return nodes.at(i);
		}
		return null;		
	}
	
	/**
	 * �жϽ��node�Ƿ����ڸõ�Ԫ
	 * 
	 * @param node
	 * @return
	 */
	public boolean isBelongToElement(Node node) {
		return this.getLocalIndex(node)>0;
	}
	
	/**
	 * �ж�һ��������Ƿ��ڵ�Ԫ�ڲ���������Ԫ�߽磩������������ά��
	 * 
	 * @param coord
	 * @return
	 */
	public boolean isCoordInElement(double[] coord) {
		Vertex v = new Vertex().set(0, coord);
		if(this.eleDim == 1) {
			return Utils.isPointOnLine(
						this.vertices().at(1), 
						this.vertices().at(2), 
						new Vertex().set(0,coord)
					);
		} else if(this.eleDim == 2) {
			//������ coord Ϊ���㣬�ֱ��Ե�Ԫ����Ϊ����ļнǣ�����ܺ�Ϊ360�ȣ������ڵ㡣
			double angle = 0.0;
			ObjList<EdgeLocal> edges = this.edges();
			for(int i=1;i<=edges.size();i++) {
				EdgeLocal edge = edges.at(i);
				angle += Utils.computeAngle2D(
									edge.beginNode(), v, 
									edge.endNode(),v
									);
			}
			if(Math.abs(angle-Math.PI*2) < Constant.eps)
				return true;
		} else if(this.eleDim == 3) {
			ObjList<FaceLocal> faces = this.faces();
			double angle = 0.0;
			for(int i=1;i<=faces.size();i++) {
				FaceLocal face = faces.at(i);
				VertexList vs = face.getVertices();
				//�������������ֽ�Ϊ������������ĺ�
				//e.g. �����SphereArea(1,2,3,4,5) = SA(1,2,3)+SA(1,3,4)+SA(1,4,5)
				for(int j=3;j<=vs.size();j++) {
					angle += Utils.getSphereTriangleArea(1, v, 
							vs.at(1), vs.at(j-1), vs.at(j));
				}
			}
			if(Math.abs(angle-4*Math.PI) <= Constant.eps)
				return true;
		} else {
			FutureyeException ex = new FutureyeException("Error: isCoordInElement");
			ex.printStackTrace();
			System.exit(0);
		}
		return false;
	}
	
	public String toString() {
		String s = "GE";
		if(globalIndex > 0)
			s += globalIndex;
		s += "( ";
		for(int i=1;i<=nodes.size();i++) {
			String st = "_";
			NodeType nodeType = nodes.at(i).getNodeType();
			if(nodeType == NodeType.Inner)
				st = "I";
			else if(nodeType == NodeType.Dirichlet)
				st = "D";
			else if(nodeType == NodeType.Neumann)
				st = "N";
			else if(nodeType == NodeType.Robin)
				st = "R";
			s += nodes.at(i).globalIndex + st + " ";
		}
		return s+")";
	}
	
	/**
	 *������ŵ���Ϊ��ʱ��˳��
	 */
	//2011-02-19
//	public void adjustVerticeToCounterClockwise() {
//		VertexList list = this.vertices();
//		int dim = list.at(1).dim();
//		if(dim == 2) {
//			if(list.size() == 3 || list.size() == 4) {
//				Vertex v1 = list.at(1);
//				Vertex v2 = list.at(2);
//				Vertex v3 = list.at(3);
//				SpaceVector v12 = null, v13 = null, cp = null;
//				if(v1.dim == 2) {
//					v12 = new SpaceVector(
//							v2.coord(1)-v1.coord(1), v2.coord(2)-v1.coord(2), 0.0);
//					v13 = new SpaceVector(
//							v3.coord(1)-v1.coord(1), v3.coord(2)-v1.coord(2), 0.0);
//		
//				} else if(v1.dim == 3) {
//					v12 = new SpaceVector(
//							v2.coord(1)-v1.coord(1), v2.coord(2)-v1.coord(2), v2.coord(3)-v1.coord(3)
//							);
//					v13 = new SpaceVector(
//							v3.coord(1)-v1.coord(1), v3.coord(2)-v1.coord(2), v3.coord(3)-v1.coord(3)
//							);			
//				}
//				cp = v12.crossProduct(v13);
//				//���С��0�������˳ʱ�룬��Ҫ��Ϊ��ʱ��
//				if(cp.get(3) < 0.0) {
//					VertexList tmp = new VertexList();
//					int n = list.size();
//					for(int i=n;i>=1;i--) {
//						list.at(i).localIndex = n-i+1;
//						tmp.add(list.at(i));
//					}
//					this.geoEntity.addAllVertices(tmp);
//					//TODO?
//					applyChange();
//				}
//			}
//		} else if(dim == 3) {
//			//TODO
//		}
//	}
	public void adjustVerticeToCounterClockwise() {
		VertexList vertices = this.vertices();
		int dim = vertices.at(1).dim();
		if(dim == 2) {
			double area = this.getElementArea();
			if(area < 0) {
				VertexList tmp = new VertexList();
				int n = vertices.size();
				for(int i=n;i>=1;i--) {
					vertices.at(i).setAllLocalIndex(n-i+1);
					tmp.add(vertices.at(i));
				}
				this.geoEntity.addAllVertices(tmp);
				this.applyChange();
			}
		} else if(dim == 3) {
			double volume = this.getElementVolume();
			if(volume < 0) {
				VertexList tmp = new VertexList();
				int n = vertices.size();
				for(int i=n;i>=1;i--) {
					vertices.at(i).setAllLocalIndex(n-i+1);
					tmp.add(vertices.at(i));
				}
				this.geoEntity.addAllVertices(tmp);
				this.applyChange();
			}
		}
	}	
	
	public void addNeighborElement(Element nb) {
		for(int i=1;i<=this.neighbors.size();i++) {
			//TODO ??? nb.globalIndex ???
			if(nb.equals(this.neighbors.at(i)))
				return;
		}
		this.neighbors.add(nb);
	}	
	
	////////////////////////////////////////////////////////////////////
	/**
	 * ����Ӧ���������������Ӹõ�Ԫ���ܳ�����������Ԫ
	 */
	public ElementList childs = null;
	public Element parent = null;
	//���ܲ��
	protected int level = 1;
	
	
	/**
	 * �жϵ�Ԫ�Ƿ����
	 * @return
	 */
	public boolean isRefined() {
		return this.childs != null;
	}
	
	public int getLevel() {
		return this.level;
	}
	
	public void setLevel(int level) {
		this.level = level;
	}
	////////////////////////////////////////////////////////////////////
	
}
