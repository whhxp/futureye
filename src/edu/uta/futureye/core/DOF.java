package edu.uta.futureye.core;

import edu.uta.futureye.core.geometry.GeoEntity;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.intf.ShapeFunction;
import edu.uta.futureye.function.intf.VectorShapeFunction;

/**
 * Degree Of Freedom
 * @author liuyueming
 *
 */
public class DOF {
	protected int localIndex;
	protected int globalIndex;
	protected ShapeFunction shapeFun;
	protected GeoEntity owner;
	
	public DOF(int localIndex, int globalIndex, ShapeFunction shape) {
		this.localIndex = localIndex;
		this.globalIndex = globalIndex;
		this.shapeFun = shape;
	}
	
	public int getLocalIndex() {
		return localIndex;
	}
	public void setLocalIndex(int localIndex) {
		this.localIndex = localIndex;
	}
	public int getGlobalIndex() {
		return globalIndex;
	}
	public void setGlobalIndex(int globalIndex) {
		this.globalIndex = globalIndex;
	}
	
	
	/**
	 * �����κ���
	 * @return
	 */
	public ShapeFunction getSF() {
		return shapeFun;
	}
	
	/**
	 * ���ر����κ���
	 * @return
	 */
	public ScalarShapeFunction getSSF() {
		return (ScalarShapeFunction)shapeFun;
	}
	
	/**
	 * ���������κ���
	 * @return
	 */
	public VectorShapeFunction getVSF() {
		return (VectorShapeFunction)shapeFun;
	}

	
	public void setShapeFunction(ShapeFunction shapeFunction) {
		this.shapeFun = shapeFunction;
	}
	
	public GeoEntity getOwner() {
		return owner;
	}
	public void setOwner(GeoEntity enty) {
		owner = enty;
	}
}
