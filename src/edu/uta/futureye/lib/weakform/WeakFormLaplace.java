package edu.uta.futureye.lib.weakform;

import edu.uta.futureye.core.Element;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.util.Utils;
import static edu.uta.futureye.function.operator.FMath.*;

/**
 * <blockquote><pre>
 * Solve the following problem(2D or 3D):
 *   -k*Laplace(u) + c*u = f, in \Omega
 *   u = u0,                  on \Gamma1
 *   d*u + k*u_n = g,         on \Gamma2
 *   
 *=>Weak formulation:
 *   A(u, v) = (f, v)
 *   
 * where
 *   A(u, v) = (k*Grad{u}, Grad{v}) - (g-d*u,v)_\Gamma2 + (c*u, v)
 *   \Gamma1: Dirichlet boundary of \Omega
 *   \Gamma2: Neumann(Robin) boundary of \Omega
 *   u_n: \frac{\pratial{u}}{\partial{n}}
 *   \vec{n}: unit norm vector of \Omega
 *   k = k(\vec{x})
 *   c = c(\vec{x})
 *   d = d(\vec{x})
 *   g = q(\vec{x})
 *
 * Remark:
 * *For nature boundary condition (自然边界条件)
 *   k*u_n + d*u = 0, set d=k
 * =>
 *   u_n + u = 0 
 * </blockquote></pre>  
 *   
 * @author liuyueming
 *
 */
public class WeakFormLaplace extends AbstractScalarWeakForm {
	protected Function g_f = null;
	protected Function g_k = null;
	protected Function g_c = null;
	protected Function g_g = null;
	protected Function g_d = null;

	//right hand side function (source term)
	public void setF(Function f) {
		this.g_f = f;
	}
	
	//Robin: d*u +  k*u_n = g
	public void setParam(Function k,Function c,Function g,Function d) {
		this.g_k = k;
		this.g_c = c;
		this.g_g = g;
		this.g_d = d;
	}

	@Override
	public Function leftHandSide(Element e, ItemType itemType) {
		if(itemType==ItemType.Domain)  {
			//Integrand part of Weak Form on element e
			Function integrand = null;
			if(g_k == null) {
				integrand = grad(u,u.innerVarNames()).dot(
							grad(v,v.innerVarNames()));
			} else {
				Function fk = Utils.interpolateOnElement(g_k,e);
				Function fc = Utils.interpolateOnElement(g_c,e);
				integrand = fk.M(
					grad(u,u.innerVarNames()).dot(
					grad(v,v.innerVarNames()))).A(
					fc.M(u.M(v)));
			}
			return integrand;
		}
		else if(itemType==ItemType.Border) {
			if(g_d != null) {
				Element be = e;
				Function fd = Utils.interpolateOnElement(g_d, be);
				Function borderIntegrand = fd.M(u.M(v));
				return borderIntegrand;
			}
		}
		return null;
	}

	@Override
	public Function rightHandSide(Element e, ItemType itemType) {
		if(itemType==ItemType.Domain)  {
			Function ff = Utils.interpolateOnElement(g_f, e);
			Function integrand = ff.M(v);
			return integrand;
		} else if(itemType==ItemType.Border) {
			if(g_g != null) {
				Element be = e;
				Function fq = Utils.interpolateOnElement(g_g, be);
				Function borderIntegrand = fq.M(v);
				return borderIntegrand;
			}
		}
		return null;		
	}
}
