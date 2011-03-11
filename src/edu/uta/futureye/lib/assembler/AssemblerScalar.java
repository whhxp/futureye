package edu.uta.futureye.lib.assembler;

import java.util.Map;
import java.util.Map.Entry;

import edu.uta.futureye.algebra.SparseMatrix;
import edu.uta.futureye.algebra.SparseVector;
import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.DOF;
import edu.uta.futureye.core.DOFOrder;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.Mesh;
import edu.uta.futureye.core.Node;
import edu.uta.futureye.core.NodeRefined;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.core.intf.Assembler;
import edu.uta.futureye.core.intf.WeakForm;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.util.list.DOFList;
import edu.uta.futureye.util.list.ElementList;
import edu.uta.futureye.util.list.NodeList;

public class AssemblerScalar implements Assembler {
	protected Mesh mesh;
	protected WeakForm weakForm;
	protected Matrix globalStiff;
	protected SparseVector globalLoad;

	public AssemblerScalar(Mesh mesh, WeakForm weakForm) {
		this.mesh = mesh;
		this.weakForm = weakForm;
		
		int dim = mesh.getNodeList().size();
		globalStiff = new SparseMatrix(dim,dim);
		globalLoad = new SparseVector(dim);

	}
	
	@Override
	public Matrix getStiffnessMatrix() {
		return this.globalStiff;
	}
	
	@Override
	public Vector getLoadVector() {
		return this.globalLoad;
	}
	
	@Override
	public void assemble() {
		ElementList eList = mesh.getElementList();
		int nEle = eList.size();
		for(int i=1; i<=nEle; i++) {
			eList.at(i).adjustVerticeToCounterClockwise();
			assembleGlobal(eList.at(i),	globalStiff,globalLoad);
			if(i%3000==0)
				System.out.println("Assemble..."+
						String.format("%.0f%%", 100.0*i/nEle));
		}
		procHangingNode(mesh);
	}
	
	@Override
	public void imposeDirichletCondition(Function diri) {
		NodeList nList = mesh.getNodeList();
		for(int i=1;i<=nList.size();i++) {
			Node n = nList.at(i);
			if(n.getNodeType() == NodeType.Dirichlet) {
				Variable v = Variable.createFrom(diri, n, n.globalIndex);
				this.globalStiff.set(n.globalIndex, n.globalIndex, 1.0);
				this.globalLoad.set(n.globalIndex, diri.value(v));
				for(int j=1;j<=this.globalStiff.getRowDim();j++) {
					if(j!=n.globalIndex) {
						//this.globalMatrix.set(j, n.globalIndex, 0.0);
						this.globalStiff.set(n.globalIndex, j, 0.0);
					}
				}
			}
		}
	}
	
	/**
	 * �ӵ�Ԫe�ϳ�ȫ�־��������
	 * @param e
	 * @param stiff
	 * @param load
	 */
	public void assembleGlobal(Element e, Matrix stiff, Vector load) {
		DOFList DOFs = e.getAllDOFList(DOFOrder.NEFV);
		int nDOFs = DOFs.size();
		
		//Update Jacobin on e
		e.updateJacobin();
		
		//�κ���������Ҫ�͵�Ԫ����
		for(int i=1;i<=nDOFs;i++) {
			DOFs.at(i).getSSF().asignElement(e);
		}
		
		//�������ɶ�˫ѭ��
		for(int i=1;i<=nDOFs;i++) {
			DOF dofI = DOFs.at(i);
			ScalarShapeFunction sfI = dofI.getSSF();
			int nLocalRow = dofI.getLocalIndex();
			int nGlobalRow = dofI.getGlobalIndex();
			for(int j=1;j<=nDOFs;j++) {
				DOF dofJ = DOFs.at(j);
				ScalarShapeFunction sfJ = dofJ.getSSF();
				int nLocalCol = dofJ.getLocalIndex();
				int nGlobalCol = dofJ.getGlobalIndex();
				//Local stiff matrix
				//ע��˳����ѭ��test�������䣬trial����ѭ��
				weakForm.setShapeFunction(sfJ,nLocalCol, sfI,nLocalRow); 
				Function lhs = weakForm.leftHandSide(e, WeakForm.ItemType.Domain);
				double lhsVal = weakForm.integrate(e, lhs);
				stiff.add(nGlobalRow, nGlobalCol, lhsVal);
			}
			//Local load vector
			weakForm.setShapeFunction(null,0,sfI,nLocalRow);
			Function rhs = weakForm.rightHandSide(e, WeakForm.ItemType.Domain);
			double rhsVal = weakForm.integrate(e, rhs);
			load.add(nGlobalRow, rhsVal);
		}
		
		if(e.isBorderElement()) {
			ElementList beList = e.getBorderElements();
			for(int n=1;n<=beList.size();n++) {
				Element be = beList.at(n);
				//Check node type
				NodeType nodeType = be.getBorderNodeType();
				if(nodeType == NodeType.Neumann || nodeType == NodeType.Robin) {
					be.updateJacobin();
					DOFList beDOFs = be.getAllDOFList(DOFOrder.NEFV);
					int nBeDOF = beDOFs.size();
					
					//�κ���������Ҫ�͵�Ԫ����
					for(int i=1;i<=nBeDOF;i++) {
						beDOFs.at(i).getSSF().asignElement(e);
					}
					
					//�������ɶ�˫ѭ��
					for(int i=1;i<=nBeDOF;i++) {
						DOF dofI = beDOFs.at(i);
						ScalarShapeFunction sfI = dofI.getSSF();
						int nLocalRow = dofI.getLocalIndex();
						int nGlobalRow = dofI.getGlobalIndex();
						for(int j=1;j<=nBeDOF;j++) {
							DOF dofJ = beDOFs.at(j);
							ScalarShapeFunction sfJ = dofJ.getSSF();
							int nLocalCol = dofJ.getLocalIndex();
							int nGlobalCol = dofJ.getGlobalIndex();
							//Local stiff matrix for border
							//ע��˳����ѭ��test�������䣬trial����ѭ��
							weakForm.setShapeFunction(sfJ,nLocalCol, sfI,nLocalRow);
							Function lhsBr = weakForm.leftHandSide(be, WeakForm.ItemType.Border);
							double lhsBrVal = weakForm.integrate(be, lhsBr);
							stiff.add(nGlobalRow, nGlobalCol, lhsBrVal);
						}
						//Local load vector for border
						weakForm.setShapeFunction(null,0,sfI,nLocalRow);
						Function rhsBr = weakForm.rightHandSide(be, WeakForm.ItemType.Border);
						double rhsBrVal = weakForm.integrate(be, rhsBr);
						load.add(nGlobalRow, rhsBrVal);
					}
				}
			}
		}
	}
	
	/**
  	 * �ӵ�Ԫe�ϳɾֲ����������
  	 * @param e
	 * @param localStiff (I/O): 
	 *   local stiff matrix corresponds to the integration part
	 *   in element e
	 * @param localStiffBorder (I/O): 
	 *   local stiff matrix corresponds to the integration part 
	 *   on the border of element e
	 * @param localLoad (I/O): 
	 *   local load vector
	 * @param localLoadBorder (I/O): 
	 *   local load vector for border
	 */
	public void assembleLocal(Element e, 
			Matrix localStiff, Matrix localStiffBorder, 
			Vector localLoad, Vector localLoadBorder) {
		
		localStiff.clear();
		localLoad.clear();
		
		DOFList DOFs = e.getAllDOFList(DOFOrder.NEFV);
		int nDOFs = DOFs.size();
		
		localStiff.setRowDim(nDOFs);
		localStiff.setColDim(nDOFs);
		localLoad.setDim(nDOFs);
		
		//Update Jacobin on e
		e.updateJacobin();
		
		//�κ���������Ҫ�͵�Ԫ����
		for(int i=1;i<=nDOFs;i++) {
			DOFs.at(i).getSSF().asignElement(e);
		}
		
		//�������ɶ�˫ѭ��
		for(int i=1;i<=nDOFs;i++) {
			DOF dofI = DOFs.at(i);
			ScalarShapeFunction sfI = dofI.getSSF();
			int nLocalRow = dofI.getLocalIndex();
			for(int j=1;j<=nDOFs;j++) {
				DOF dofJ = DOFs.at(j);
				ScalarShapeFunction sfJ = dofJ.getSSF();
				int nLocalCol = dofJ.getLocalIndex();
				//Local stiff matrix
				//ע��˳����ѭ��test�������䣬trial����ѭ��
				weakForm.setShapeFunction(sfJ,nLocalCol, sfI,nLocalRow);
				Function lhs = weakForm.leftHandSide(e, WeakForm.ItemType.Domain);
				double lhsVal = weakForm.integrate(e, lhs);
				localStiff.add(nLocalRow, nLocalCol, lhsVal);
			}
			//Local load vector
			weakForm.setShapeFunction(null,0,sfI,nLocalRow);
			Function rhs = weakForm.rightHandSide(e, WeakForm.ItemType.Domain);
			double rhsVal = weakForm.integrate(e, rhs);
			localLoad.add(nLocalRow, rhsVal);
		}
		
		if(e.isBorderElement()) {
			ElementList beList = e.getBorderElements();
			for(int n=1;n<=beList.size();n++) {
				Element be = beList.at(n);
				//Check node type
				NodeType nodeType = be.getBorderNodeType();
				if(nodeType == NodeType.Neumann || nodeType == NodeType.Robin) {
					be.updateJacobin();
					DOFList beDOFs = be.getAllDOFList(DOFOrder.NEFV);
					int nBeDOF = beDOFs.size();
					
					localStiffBorder.setRowDim(nDOFs);
					localStiffBorder.setColDim(nDOFs);
					//�κ���������Ҫ�͵�Ԫ����
					for(int i=1;i<=nBeDOF;i++) {
						beDOFs.at(i).getSSF().asignElement(e);
					}
					
					//�������ɶ�˫ѭ��
					for(int i=1;i<=nBeDOF;i++) {
						DOF dofI = beDOFs.at(i);
						ScalarShapeFunction sfI = dofI.getSSF();
						int nLocalRow = dofI.getLocalIndex();
						for(int j=1;j<=nBeDOF;j++) {
							DOF dofJ = beDOFs.at(j);
							ScalarShapeFunction sfJ = dofJ.getSSF();
							int nLocalCol = dofJ.getLocalIndex();
							//Local stiff matrix for border
							//ע��˳����ѭ��test�������䣬trial����ѭ��
							weakForm.setShapeFunction(sfJ,nLocalCol, sfI,nLocalRow);
							Function lhsBr = weakForm.leftHandSide(be, WeakForm.ItemType.Border);
							double lhsBrVal = weakForm.integrate(be, lhsBr);
							localStiffBorder.add(nLocalRow, nLocalCol, lhsBrVal);
						}
						//Local load vector for border
						weakForm.setShapeFunction(null,0,sfI,nLocalRow);
						Function rhsBr = weakForm.rightHandSide(be, WeakForm.ItemType.Border);
						double rhsBrVal = weakForm.integrate(be, rhsBr);
						localLoadBorder.add(nLocalRow, rhsBrVal);						
					}
				}
			}
		}
	}
	
	//��ά���նȾ�������hanging nodeԼ��ϵ��
	// nh - 0.5*n1 - 0.5*n2 = 0
	public void procHangingNode(Mesh mesh) {
		
		for(int i=1;i<=mesh.getNodeList().size();i++) {
			Node node = mesh.getNodeList().at(i);
			if(node instanceof NodeRefined) {
				NodeRefined nRefined = (NodeRefined)node;
				if(nRefined.isHangingNode()) {
					globalStiff.set(nRefined.globalIndex, nRefined.globalIndex, 1.0);
					globalStiff.set(nRefined.globalIndex,
							nRefined.constrainNodes.at(1).globalIndex,-0.5);
					globalStiff.set(nRefined.globalIndex,
							nRefined.constrainNodes.at(2).globalIndex,-0.5);
				}
			}
		}
	}

	public void plusToGlobalLoad(Element e, Vector local) {
		for(int i=1; i<=local.getDim(); i++) {
			int ngRow = e.local2GlobalDOFIndex(i);
			this.globalLoad.add(ngRow, local.get(i));
		}
	}
	
	public void plusToGlobalStriff(Element e, Matrix local) {
		Map<Integer,Map<Integer,Double>> m = local.getAll();
		for(Entry<Integer,Map<Integer,Double>> er : m.entrySet()) {
			int nRow = er.getKey();
			int ngRow = e.local2GlobalDOFIndex(nRow);
			Map<Integer,Double> row = er.getValue();
			for(Entry<Integer,Double> ec : row.entrySet()) {
				int nCol = ec.getKey();
				double val = ec.getValue();
				int ngCol = e.local2GlobalDOFIndex(nCol);
//				System.out.println(
//						"Local("+nRow+","+nCol+") -> Global("+ngRow+","+ngCol+")"
//						);
				this.globalStiff.add(ngRow, ngCol, val);
			}
		}
	}
}
