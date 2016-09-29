package edu.uta.futureye.lib.weakform;

import java.util.HashMap;
import java.util.Map;

import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.DOF;
import edu.uta.futureye.core.DOFOrder;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.operator.FOIntegrate;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.DOFList;
import edu.uta.futureye.util.container.ElementList;

/**
 * <blockquote><pre>
 * Solve
 *   -k*Laplace(u) + c*u = f, in \Omega
 *   u = u0,                  on \Gamma1
 *   d*u + k*u_n = g,         on \Gamma2
 *=>
 *   A(u, v) = (f, v)
 * 
 * where
 *   A(u, v) = (k*u_x, v_x) + (k*u_y, v_y) - (g-d*u,v)_\Gamma2 + (c*u, v)
 *=>
 *   A(u, v) = (k*u_x, v_x) + (k*u_y, v_y) + (d*u-g,v)_\Gamma2 + (c*u, v)
 *
 *   \Gamma1: Dirichlet boundary of \Omega
 *   \Gamma2: Neumann(Robin) boundary of \Omega
 *   u_n: \frac{\pratial{u}}{\partial{n}}
 *   n: unit norm vector of \Omega
 *   k = k(x,y)
 *   c = c(x,y)
 *   d = d(x,y)
 *   g = g(x,y)
 * </blockquote></pre>  
 * 
 * @author liuyueming
 *
 */
public class WeakFormLaplace2D extends AbstractScalarWeakForm {
	protected Function g_f = null;
	protected Function g_k = null;
	protected Function g_c = null;
	protected Function g_g = null; 
	protected Function g_d = null;
	
	protected Function ff = null;
	protected Function fk = null;
	protected Function fc = null;
	protected Function fg = null;
	protected Function fd = null;

	public void setF(Function f) {
		this.g_f = f;
	}
	
	//Robin:  d*u + k*u_n = g 
	//2011-08-02
	//E.g.1 Nature boundary condition: u_n + u = 0  =>  d=k, q=0
	//E.g.2 u_n = g                                 =>  d=0, g=k*g  
	public void setParam(Function k,Function c,Function g,Function d) {
		this.g_k = k;
		this.g_c = c;
		this.g_g = g; //2/3/12 q=>g
		this.g_d = d;
	}
	
	@Override 
	public void preProcess(Element e) {
		if(e.dim() == 2) {
			if(g_k != null) fk = Utils.interpolateOnElement(g_k, e);
			if(g_c != null) fc = Utils.interpolateOnElement(g_c, e);
			if(g_f != null) ff = Utils.interpolateOnElement(g_f, e);
		} else if(e.dim() == 1) {
			if(g_d != null) fd = Utils.interpolateOnElement(g_d, e);
			if(g_g != null) fg = Utils.interpolateOnElement(g_g, e);
		}
	}

	@Override
	public Function leftHandSide(Element e, ItemType itemType) {
		if(itemType==ItemType.Domain)  {
			//Integrand part of Weak Form on element e
			Function integrand = null;
			if(g_k == null) {
				integrand = u._d("x").M(v._d("x")) .A (u._d("y").M(v._d("y")));
			} else {
				integrand = fk.M(
								u._d("x").M(v._d("x")) .A (u._d("y").M(v._d("y")))
							).A(
								fc.M(u.M(v))
							);
			}
			return integrand;
		}
		else if(itemType==ItemType.Border) {//Neumann border integration on LHS
			if(g_d != null) {
				Function borderIntegrand = fd.M(u.M(v));
				return borderIntegrand;
			}
		}
		return null;
	}

	@Override
	public Function rightHandSide(Element e, ItemType itemType) {
		if(itemType==ItemType.Domain)  {
			Function integrand = ff.M(v);
			return integrand;
		} else if(itemType==ItemType.Border) {
			if(g_g != null) {
				Function borderIntegrand = fg.M(v);
				return borderIntegrand;
			}
		}
		return null;	
	}

	/**
	 * Optimized for fast assemble, 10% speedup
	 */
	@Override
	public void assembleElement(Element e, 
		Matrix globalStiff,	Vector globalLoad) {
	
		DOFList DOFs = e.getAllDOFList(DOFOrder.NEFV);
		int nDOFs = DOFs.size();
		
		//Update Jacobin on e
		e.updateJacobinLinear2D();
		
		//形函数计算需要和单元关联，并提前计算导数
		Map<Integer, Function> mapShape_x = new HashMap<Integer, Function>();
		Map<Integer, Function> mapShape_y = new HashMap<Integer, Function>();
		for(int i=1;i<=nDOFs;i++) {
			DOF dof = DOFs.at(i);
			ScalarShapeFunction sf = dof.getSSF();
			dof.getSSF().assignElement(e);
			mapShape_x.put(dof.getLocalIndex(), sf._d("x"));
			mapShape_y.put(dof.getLocalIndex(), sf._d("y"));
		}

		Function fk = null;
		if(g_k != null) fk = Utils.interpolateOnElement(g_k,e);
		Function fc = null;
		if(g_c != null) fc = Utils.interpolateOnElement(g_c,e);

		//所有自由度双循环
		for(int i=1;i<=nDOFs;i++) {
			DOF dofI = DOFs.at(i);
			ScalarShapeFunction sfI = dofI.getSSF();
			int nLocalRow = dofI.getLocalIndex();
			int nGlobalRow = dofI.getGlobalIndex();
			for(int j=1;j<=nDOFs;j++) {
				DOF dofJ = DOFs.at(j);
				int nLocalCol = dofJ.getLocalIndex();
				int nGlobalCol = dofJ.getGlobalIndex();
				//Integrand part of Weak Form on element e
				Function integrand = null;
				if(g_k == null) {
					integrand = mapShape_x.get(nLocalRow).M(mapShape_x.get(nLocalCol))
								.A(
								mapShape_y.get(nLocalRow).M(mapShape_y.get(nLocalCol))
								);
				} else {
					integrand = fk.M(
									mapShape_x.get(nLocalRow).M(mapShape_x.get(nLocalCol))
									.A(
									mapShape_y.get(nLocalRow).M(mapShape_y.get(nLocalCol))
									)
								.A(
									fc.M(dofI.getSSF().M(dofJ.getSSF())))
								);
				}
				//Numerical integration on element e
				double lhsVal = 0.0;
				if(e.vertices().size() == 3) {
					lhsVal = FOIntegrate.intOnTriangleRefElement(
							integrand.M(e.getJacobin()),4
							);
				} else if (e.vertices().size() == 4) {
					lhsVal = FOIntegrate.intOnRectangleRefElement(
							integrand.M(e.getJacobin()),2 //TODO
							);
				}
				globalStiff.add(nGlobalRow, nGlobalCol, lhsVal);
			}
			//Load vector
			Function ff = Utils.interpolateOnElement(g_f, e);
			Function integrand = ff.M(sfI);
			double rhsVal = 0.0;
			if(e.vertices().size() == 3) {
				rhsVal = FOIntegrate.intOnTriangleRefElement(
						integrand.M(e.getJacobin()),4
						);
			} else if (e.vertices().size() == 4) {
				rhsVal = FOIntegrate.intOnRectangleRefElement(
						integrand.M(e.getJacobin()),2 //TODO
						);
			}
			globalLoad.add(nGlobalRow, rhsVal);
		}
		
		//Robin:  d*u + k*u_n= q (自然边界：d==k, q=0)
		//if(g_d != null && e.isBorderElement()) {
		if(e.isBorderElement()) {

			ElementList beList = e.getBorderElements();
			for(int n=1;n<=beList.size();n++) {
				Element be = beList.at(n);
				
				Function fd = null;
				if(g_d != null) fd = Utils.interpolateOnElement(g_d, be);
				//Check node type
				NodeType nodeType = be.getBorderNodeType();
				if(nodeType == NodeType.Neumann || nodeType == NodeType.Robin) {
					DOFList beDOFs = be.getAllDOFList(DOFOrder.NEFV);
					int nBeDOF = beDOFs.size();
					
					//Update Jacobin on be
					be.updateJacobinLinear1D();
					
					//形函数计算需要和单元关联
					for(int i=1;i<=nBeDOF;i++) {
						beDOFs.at(i).getSSF().assignElement(be);
					}
					
					//所有自由度双循环
					for(int i=1;i<=nBeDOF;i++) {
						DOF dofI = beDOFs.at(i);
						ScalarShapeFunction sfI = dofI.getSSF();
						int nGlobalRow = dofI.getGlobalIndex();
						if(g_d != null) {
							for(int j=1;j<=nBeDOF;j++) {
								DOF dofJ = beDOFs.at(j);
								ScalarShapeFunction sfJ = dofJ.getSSF();
								int nGlobalCol = dofJ.getGlobalIndex();
								//Stiff matrix for border
								Function borderIntegrand = fd.M(sfI.M(sfJ));
								//Numerical integrate the border 'be' of element 'e'
								double lhsBrVal = FOIntegrate.intOnLinearRefElement(
										borderIntegrand.M(be.getJacobin()),5
									);
								globalStiff.add(nGlobalRow, nGlobalCol, lhsBrVal);
							}
						}
						//Load vector for border
						if(g_g != null) {
							Function fq = Utils.interpolateOnElement(g_g, be);
							Function borderIntegrand = fq.M(sfI);
							double rhsBrVal = FOIntegrate.intOnLinearRefElement(
									borderIntegrand.M(be.getJacobin()),5
								);
							globalLoad.add(nGlobalRow, rhsBrVal);
						}
					}
				}
			}
		}
	}
}
