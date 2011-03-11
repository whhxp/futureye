package edu.uta.futureye.core.geometry;

import edu.uta.futureye.core.Vertex;
import edu.uta.futureye.util.list.VertexList;

/**
 * 0D(Vertices) Geometry Entity
 * ����ʵ��ĸ��࣬���漸��ʵ��Ķ���
 * 
 * @author liuyueming
 *
 */
public class GeoEntity0D implements GeoEntity {
	protected VertexList vertices = new VertexList();
	
	public void addVertex(Vertex vertex) {
		this.vertices.add(vertex);
	}

	public void addAllVertices(VertexList vertices) {
		this.vertices.clear();
		this.vertices.addAll(vertices);
	}

	public VertexList getVertices() {
		return vertices;
	}
	
	public void clearVertices() {
		this.vertices.clear();
	}
	
	public String toString() {
		return this.vertices.toString();
	}
}
