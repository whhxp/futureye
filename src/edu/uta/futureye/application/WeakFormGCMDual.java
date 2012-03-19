package edu.uta.futureye.application;

import edu.uta.futureye.core.Element;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.operator.FMath;
import edu.uta.futureye.lib.weakform.AbstractScalarWeakForm;
import edu.uta.futureye.util.Utils;

/**
 * Solve Weak form
 *  (k*\nabla{u}, \nabla{v}) + (u*\vec{b}, \nabla{v}) + (c*u, v) = (f, v)
 * =>
 *  (k*u_x, v_x) + (k*u_y, v_y) + (b1*v_x, u) + (b2*v_y, u) + (c*u, v)= (f, v)
 * 
 * @author liuyueming
 *
 */
public class WeakFormGCMDual extends AbstractScalarWeakForm {
	protected Function g_f = null;
	
	protected Function g_k = null;
	protected Function g_c = null;
	protected Function g_b1 = null;
	protected Function g_b2 = null;
	
	protected Function g_q = null;
	protected Function g_d = null;

	public void setF(Function f) {
		this.g_f = f;
	}
	
	public void setParam(Function k,Function c,Function b1,Function b2) {
		this.g_k = k;
		this.g_c = c;
		this.g_b1 = b1;
		this.g_b2 = b2;
	}
	
	//Robin:  d*u + k*u_n= q (自然边界：d==k, q=0)
	public void setRobin(Function q,Function d) {
		this.g_q = q;
		this.g_d = d;
	}	

	@Override
	public Function leftHandSide(Element e, ItemType itemType) {
		if(itemType==ItemType.Domain)  {
			//Integrand part of Weak Form on element e
			Function integrand = null;

			Function fk = Utils.interpolateOnElement(g_k,e);
			Function fc = Utils.interpolateOnElement(g_c,e);
			Function fb1 = Utils.interpolateOnElement(g_b1,e);
			Function fb2 = Utils.interpolateOnElement(g_b2,e);
			
			integrand = FMath.sum(
						fk.M(
								u._d("x").M(v._d("x")).A(
								u._d("y").M(v._d("y"))
						)),
						fb1.M(v._d("x").M(u)),
						fb2.M(v._d("y").M(u)),
						fc.M(u.M(v))
					);
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
			Element be = e;
			Function fq = Utils.interpolateOnElement(g_q, be);
			Function borderIntegrand = fq.M(v);
			return borderIntegrand;
		} 
		return null;
	}
}
