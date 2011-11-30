package edu.uta.futureye.lib.weakform;

import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.DOF;
import edu.uta.futureye.core.DOFOrder;
import edu.uta.futureye.core.Edge;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.Face;
import edu.uta.futureye.core.Node;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.intf.VectorFunction;
import edu.uta.futureye.function.operator.FMath;
import edu.uta.futureye.lib.shapefun.SFConstant0;
import edu.uta.futureye.util.MathEx;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.DOFList;

/**
 * <blockquote><pre>
 * Problem:
 * -\nabla{k*\nabla{\vec{u}} 
 * 		+ \vec{U}\cdot\nabla\vec{u}
 * 		+ c*\vec{u} 
 * 		+ \nabla{p} 
 * 		= \vec{f}
 * div{\vec{u}} = 0
 * 
 * Weak form:
 *   find \vec{u} \in H_0^1(div;\Omega), p \in L_2(\Omega)
 *   such that, for all \vec{v} \in H_0^1(div;\Omega), q \in L_2(\Omega)
 *   
 *   (\nabla\vec{v},k*\nabla\vec{u})
 *   		+ (\vec{U}\cdot\nabla\vec{u},\vec{v})
 *   		+ (c*\vec{u},\vec{v})
 *   		- (div{\vec{v}},p)
 *  		+ (q,div{\vec{u}}) 
 *			= (\vec{v},\vec{f})
 *
 *   k* [(v1_x,u1_x) + (v1_y,u1_y) + (v1_z,u1_z) + 
 *       (v2_x,u2_x) + (v2_y,u2_y) + (v2_z,u2_z) +
 *       (v3_x,u3_x) + (v3_y,u3_y) + (v3_z,u3_z) ] +
 *       
 *   	[(U1*u1_x,v1)+(U2*u1_y,v1)+(U3*u1_z,v1)] + 
 *      [(U1*u2_x,v2)+(U2*u2_y,v2)+(U3*u2_z,v2)] +
 *      [(U1*u2_x,v2)+(U2*u2_y,v2)+(U3*u3_z,v3)] +
 *      
 *   		+ c*[(u1,v1)+(u2,v2)+(u3,v3)]
 *   
 *			- (v1_x+v2_y+v3_z,p)
 *			+ (q,u1_x+u2_y+u3_z)
 *
 *			= (v1,f1)+(v2,f2)+(v3,f3)
 *
 * where
 *   \vec{u}=(u1,u2,u3): velocity vector field
 *   \vec{f}=(f1,f2,f3): body force
 *   \vec{U}=(U1,U2,U3): previous velocity
 * </blockquote></pre>
 * 
 * @author liuyueming
 *
 */
public class WeakFormNavierStokes3D extends AbstractVectorWeakForm {
	protected VectorFunction g_f = null;
	protected Function g_k = null;
	protected VectorFunction g_U = null;
	protected Function g_c = null;
	//Robin:  k*u_n + d*u - p\vec{n} = 0
	protected VectorFunction g_d = null;

	public void setF(VectorFunction f) {
		this.g_f = f;
	}
	
	public void setParam(Function k, VectorFunction U, Function c) {
		this.g_k = k;
		this.g_U = U;
		this.g_c = c;
	}
	
	//Robin:  k*u_n + d*u - p\vec{n} = 0
	public void setRobin(VectorFunction d) {
		this.g_d = d;
	}
	
	@Override
	public Function leftHandSide(Element e, ItemType itemType) {

		if(itemType==ItemType.Domain)  {
			//Integrand part of Weak Form on element e
			Function integrand = null;
			Function fk = Utils.interpolateFunctionOnElement(g_k,e);
			Function fc = Utils.interpolateFunctionOnElement(g_c,e);
			VectorFunction fU = Utils.interpolateFunctionOnElement(g_U, e);
			
			ScalarShapeFunction u1 = (ScalarShapeFunction)u.get(1);
			ScalarShapeFunction u2 = (ScalarShapeFunction)u.get(2);
			ScalarShapeFunction u3 = (ScalarShapeFunction)u.get(3);
			ScalarShapeFunction p  = (ScalarShapeFunction)u.get(4);
			ScalarShapeFunction v1 = (ScalarShapeFunction)v.get(1);
			ScalarShapeFunction v2 = (ScalarShapeFunction)v.get(2);
			ScalarShapeFunction v3 = (ScalarShapeFunction)v.get(3);
			ScalarShapeFunction q  = (ScalarShapeFunction)v.get(4);
			
			//upwind
			DOFList dofs = e.getAllDOFList(DOFOrder.NEFV);
			DOF dof = dofs.at(this.vDOFLocalIndex);
			if(this.vDOFLocalIndex<=24) {
				Node node1 = dof.getNodeOwner();
				//\tidle{v} = v + \tidle{k}*\hat{U}*v,j/\norm{U}
				Vector valU = g_U.value(new Variable().setIndex(node1.globalIndex));
				double normU = valU.norm2();
				Vector valU_hat = valU.scale(1.0/normU);
				double h = e.getElementDiameter();
				double k = g_k.value(Variable.createFrom(g_k, node1, node1.globalIndex));
				double alpha = normU*h/(2*k);
				//double k_tidle = 2*(MathEx.coth(alpha)-1.0/alpha)*normU*h;
				double k_tidle = (MathEx.coth(alpha)-1.0/alpha)*normU*h;
				if(!(v1 instanceof SFConstant0)) {
					v1.A(FMath.grad(v1,v1.innerVarNames()).dot(valU_hat).M(k_tidle).D(valU.norm2()));
				}
				if(!(v2 instanceof SFConstant0)) {
					v2.A(FMath.grad(v2,v2.innerVarNames()).dot(valU_hat).M(k_tidle).D(valU.norm2()));
				}
			}
			
			
/**
 *   k* [(v1_x,u1_x) + (v1_y,u1_y) + (v1_z,u1_z) + 
 *       (v2_x,u2_x) + (v2_y,u2_y) + (v2_z,u2_z) +
 *       (v3_x,u3_x) + (v3_y,u3_y) + (v3_z,u3_z) ] +
 *       
 *   	[(U1*u1_x,v1)+(U2*u1_y,v1)+(U3*u1_z,v1)] + 
 *      [(U1*u2_x,v2)+(U2*u2_y,v2)+(U3*u2_z,v2)] +
 *      [(U1*u2_x,v2)+(U2*u2_y,v2)+(U3*u3_z,v3)] +
 *      
 *   		+ c*[(u1,v1)+(u2,v2)+(u3,v3)]
 *   
 *			- (v1_x+v2_y+v3_z,p)
 *			+ (q,u1_x+u2_y+u3_z)
 *
 *			= (v1,f1)+(v2,f2)+(v3,f3)
 * 
 */
			VectorFunction grad_u1 = FMath.grad(u1,u1.innerVarNames());
			VectorFunction grad_u2 = FMath.grad(u2,u2.innerVarNames());
			VectorFunction grad_u3 = FMath.grad(u3,u3.innerVarNames());
			Function uv1 = grad_u1.dot(FMath.grad(v1,v1.innerVarNames()));
			Function uv2 = grad_u2.dot(FMath.grad(v2,v2.innerVarNames()));
			Function uv3 = grad_u3.dot(FMath.grad(v3,v3.innerVarNames()));
			Function div_v = v1._d("x").A(v2._d("y")).A(v3._d("z"));
			Function div_u = u1._d("x").A(u2._d("y")).A(u3._d("z"));
			Function cvect = fU.dot(grad_u1).M(v1).A(fU.dot(grad_u2).M(v2)).A(fU.dot(grad_u3).M(v3));
			Function cuv = fc.M(u1.M(v1).A(u2.M(v2)).A(u3.M(v3)));
			integrand = fk.M( uv1.A(uv2).A(uv3) ).A( cvect ).A( cuv ).S( div_v.M(p) ).A( div_u.M(q) );
			return integrand;
		} else if(itemType==ItemType.Border) {
			if(g_d != null) {
				Element be = e;
				Function fd1 = Utils.interpolateFunctionOnElement(g_d.get(1), be);
				Function fd2 = Utils.interpolateFunctionOnElement(g_d.get(2), be);
				Function fd3 = Utils.interpolateFunctionOnElement(g_d.get(3), be);
				ScalarShapeFunction u1 = (ScalarShapeFunction)u.get(1);
				ScalarShapeFunction u2 = (ScalarShapeFunction)u.get(2);
				ScalarShapeFunction u3 = (ScalarShapeFunction)u.get(3);
				ScalarShapeFunction v1 = (ScalarShapeFunction)v.get(1);
				ScalarShapeFunction v2 = (ScalarShapeFunction)v.get(2);
				ScalarShapeFunction v3 = (ScalarShapeFunction)v.get(3);
				//Robin:  - k*u_n = d*u - p\vec{n}
				//d1*u1 + d2*u2 + d3*u3
				Function borderIntegrand = fd1.M(u1.M(v1)).A(fd2.M(u2.M(v2))).A(fd3.M(u3.M(v3)));
				return borderIntegrand;
			}
		}
		return null;
	}

	@Override
	public Function rightHandSide(Element e, ItemType itemType) {
		if(itemType==ItemType.Domain)  {
			Function f1 = Utils.interpolateFunctionOnElement(g_f.get(1), e);
			Function f2 = Utils.interpolateFunctionOnElement(g_f.get(2), e);
			Function f3 = Utils.interpolateFunctionOnElement(g_f.get(3), e);
			ScalarShapeFunction v1 = (ScalarShapeFunction)v.get(1);
			ScalarShapeFunction v2 = (ScalarShapeFunction)v.get(2);
			ScalarShapeFunction v3 = (ScalarShapeFunction)v.get(3);
			//(v1*f1+v2*f2)
			Function integrand = v1.M(f1).A(v2.M(f2)).A(v3.M(f3));
			return integrand;
		} else if(itemType==ItemType.Border) {
			Element be = e;
			ScalarShapeFunction v1 = (ScalarShapeFunction)v.get(1);
			ScalarShapeFunction v2 = (ScalarShapeFunction)v.get(2);
			ScalarShapeFunction v3 = (ScalarShapeFunction)v.get(3);
			ScalarShapeFunction p  = (ScalarShapeFunction)v.get(4);
			//Robin:  - k*u_n = d*u - p\vec{n}
			//- p\vec{n} = - p*n1*v1 - p*n2*v2 - p*n3*v3
			Face face = (Face)be.getGeoEntity();
			Vector n = face.getNormVector();
			FC n1 = FC.c(-1.0*n.get(1));
			FC n2 = FC.c(-1.0*n.get(2));
			FC n3 = FC.c(-1.0*n.get(3));
			Function borderIntegrand = p.M(v1.M(n1)).A(p.M(v2.M(n2))).A(p.M(v3.M(n3)));
			return borderIntegrand;
		}
		return null;
	}
}
