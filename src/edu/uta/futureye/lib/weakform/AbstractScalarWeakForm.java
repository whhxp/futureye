package edu.uta.futureye.lib.weakform;

import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.intf.WeakForm;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.intf.ShapeFunction;
import edu.uta.futureye.function.operator.FOIntegrate;
import edu.uta.futureye.util.FutureyeException;

public abstract class AbstractScalarWeakForm implements WeakForm {
	protected ScalarShapeFunction u = null;
	protected ScalarShapeFunction v = null;
	protected int uDOFLocalIndex;
	protected int vDOFLocalIndex;

	@Override
	public void assembleElement(Element e, 
			Matrix globalStiff,	Vector globalLoad) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Function leftHandSide(Element e, ItemType itemType) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Function rightHandSide(Element e, ItemType itemType) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setShapeFunction(ShapeFunction trial, int trialDofLocalIndex,
			ShapeFunction test, int testDofLocalIndex) {
		this.u = (ScalarShapeFunction)trial;
		this.v = (ScalarShapeFunction)test;
		this.uDOFLocalIndex = trialDofLocalIndex;
		this.vDOFLocalIndex = testDofLocalIndex;
	}

	@Override
	public double integrate(Element e, Function fun) {
		if(fun == null) return 0.0;
		if(e.dim() == 2) {
			if(e.vertices().size() == 3) {
				//三角形单元
				return FOIntegrate.intOnTriangleRefElement(
						fun.M(e.getJacobin()),4
						);
			} else if (e.vertices().size() == 4) {
				//四边形单元
				return FOIntegrate.intOnRectangleRefElement(
						fun.M(e.getJacobin()),5 //TODO
						);
			}
		} else if(e.dim() == 3) {
			if(e.vertices().size() == 4) {
				//四面体单元
				return FOIntegrate.intOnTetrahedraRefElement(
						fun.M(e.getJacobin()),2
					);
			} else if(e.vertices().size() == 8) {
				//六面体单元
				return FOIntegrate.intOnHexahedraRefElement(
						fun.M(e.getJacobin()),2
					);
			}
		} else if(e.dim() == 1) {
			//一维单元
			return FOIntegrate.intOnLinearRefElement(
					fun.M(e.getJacobin()),5
				);
		} else {
			throw new FutureyeException(
					"Can NOT integrate on e" + e.vertices());
		}
		throw new FutureyeException("Error");
	}
}
