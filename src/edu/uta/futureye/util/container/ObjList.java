package edu.uta.futureye.util.container;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.uta.futureye.util.FutureyeException;

/**
 * Object list container
 * 
 * * index starts from 1
 * * <tt>null</tt> element is not allowed,
 * * auto size increment.
 * 
 * @author liuyueming
 *
 * @param <T>
 */
public class ObjList<T> implements Iterable<T>{
	protected List<T> objs = new ArrayList<T>();
	
	public ObjList() {
	}
	
	@SafeVarargs
	public ObjList(T ...es) {
		for(T e : es) this.add(e);
	}

	/**
	 * @param index starts from 1,2,3...
	 * @return
	 */
	public T at(int index) {
		if(index < 1)
			throw new FutureyeException("ERROR: ObjList index should >=1, index="+index);
		return objs.get(index-1);
	}

	public ObjList<T> add(T e) {
		this.objs.add(e);
		return this;
	}
	
	/**
     * Replaces the element at the specified position in this ObjList with 
     * the specified element <tt>e</tt>.
     * 
	 * @param index
	 * @param e
	 * @return the whole new ObjList
	 */
	public ObjList<T> set(int index,T e) {
		if(index < 1)
			throw new FutureyeException("ERROR: ObjList index should >=1, index="+index);
		this.objs.set(index-1, e);
		return this;
	}
	
	public ObjList<T> addAll(ObjList<T> list) {
		if(list == null) return this;
		this.objs.addAll(list.objs);
		return this;
	}
	
	public int size() {
		return objs.size();
	}
	
	public void clear() {
		objs.clear();
	}
	
	public T remove(int index) {
		return objs.remove(index-1);
	}
	
	public boolean remove(T e) {
		return objs.remove(e);
	}
	
	public ObjList<T> subList(int begin,int end) {
		ObjList<T> rlt = new ObjList<T>();
		for(int i=begin;i<=end;i++)
			rlt.add(this.at(i));
		return rlt;
	}
	
	public ObjList<T> subList(int begin,int end,int step) {
		ObjList<T> rlt = new ObjList<T>();
		for(int i=begin;i<=end;i+=step)
			rlt.add(this.at(i));
		return rlt;
	}
	
	public ObjList<T> subList(List<Integer> set) {
		ObjList<T> rlt = new ObjList<T>();
		for(Integer i : set)
			rlt.add(this.at(i));
		return rlt;
	}
	
	public List<T> toList() {
		return objs;
	}
	public Object[] toArray() {
		return objs.toArray();
	}
	public <T2> T2[] toArray(T2[] a) {
		return objs.toArray(a);
	}
	
	/**
	 * 从一个下标为0,1,2,...的list<T>构建
	 * 为一个下标为1,2,3,...的ObjList<T>
	 * @param list
	 */
	public ObjList<T> fromList(List<T> list) {
		objs.clear();
		objs.addAll(list);
		return this;
	}
	public ObjList<T> fromArray(T[] array) {
		objs.clear();
		for(int i=0;i<array.length;i++)
			objs.add((T)array[i]);
		return this;
	}
	
	public String toString() {
		return objs.toString();
	}

	@Override
	public Iterator<T> iterator() {
		return objs.iterator();
	}
	
	public boolean contains(T o) {
		return objs.contains(o);
	}
}
