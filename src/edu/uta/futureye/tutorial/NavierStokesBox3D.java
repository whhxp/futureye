package edu.uta.futureye.tutorial;

import java.util.HashMap;

import edu.uta.futureye.algebra.SchurComplementStokesSolver;
import edu.uta.futureye.algebra.SparseVector;
import edu.uta.futureye.algebra.intf.BlockMatrix;
import edu.uta.futureye.algebra.intf.BlockVector;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.application.DataReader;
import edu.uta.futureye.core.EdgeLocal;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.Mesh;
import edu.uta.futureye.core.Node;
import edu.uta.futureye.core.NodeLocal;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.core.Vertex;
import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.basic.SpaceVectorFunction;
import edu.uta.futureye.function.basic.Vector2Function;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.VectorFunction;
import edu.uta.futureye.io.MeshReader;
import edu.uta.futureye.lib.assembler.AssemblerVector;
import edu.uta.futureye.lib.element.FEBilinearV_ConstantP;
import edu.uta.futureye.lib.element.FEQuadraticV_LinearP;
import edu.uta.futureye.lib.element.FETrilinearV_ConstantP;
import edu.uta.futureye.lib.element.FiniteElementType;
import edu.uta.futureye.lib.weakform.WeakFormNavierStokes2D;
import edu.uta.futureye.lib.weakform.WeakFormNavierStokes3D;
import edu.uta.futureye.util.Constant;
import edu.uta.futureye.util.FutureyeException;
import edu.uta.futureye.util.container.ElementList;
import edu.uta.futureye.util.container.NodeList;
import edu.uta.futureye.util.container.ObjIndex;
import edu.uta.futureye.util.container.ObjList;


/**
 * Problem: 3D Navier-Stokes, lid-driven cavity
 *
 * Ref.
 * 1. T.E. Teaduyar Stabilized Finite Element Formulations for Incompressible Flow Computations
 * 
 * 2. Rajiv Sampath and Nicholas Zabaras Design and object-oriented implementation of a 
 *    preconditioned-stabilized incompressible NaiverStokes solver using equal-order-interpolation 
 *    velocity pressure elements
 * @author liuyueming
 *
 */
public class NavierStokesBox3D {
	protected String outputFolder = "tutorial/NavierStokesBox3D";
	protected String file = null;
	
	protected Mesh mesh = null;
	protected Mesh meshOld = null;
	
	//Navier-Stokes Weak Form (For Picard Iteration)
	protected WeakFormNavierStokes3D weakForm = new WeakFormNavierStokes3D();
	//Assembler
	protected AssemblerVector assembler = null;
	//Dirichlet boundary condition
	protected VectorFunction diri = null;
	//Previous Velocity
	protected VectorFunction U = new SpaceVectorFunction(3);
	
	//delta t
	protected double dt = 0.02;
	//viscosity
	protected double mu = 0.001; 
	
	FiniteElementType fe = null;
	
	int maxNonlinearIter = 30;
	double nonlinearError = 1e-2;
	int maxTimeStep = 1000;
	
	/**
	 * 
	 * @param testCaseNo
	 */
	public void init(int testCaseNo) {
		//Read mesh from input file
		//file = "stokes_cavity3d";//[0,1]*[0,1] 8*8*8
		file = "stokes_cavity3d2";//[0,1]*[0,1] 10*10*10
		
		MeshReader reader = new MeshReader(file+".grd");
		MeshReader reader2 = new MeshReader(file+".grd");
		mesh = reader.read3DMesh();
		meshOld = reader2.read3DMesh();
		mesh.nVertex = mesh.getNodeList().size();

		//Geometry relationship
		mesh.computeNodeBelongsToElements();
		mesh.computeGlobalEdge();
		
		ElementList eList = mesh.getElementList();
		NodeList nodes = mesh.getNodeList();
//		for(int i=1;i<=eList.size();i++) {
//			System.out.println(i+"  " + eList.at(i));
//		}
		
		//Mark border type
		HashMap<NodeType, Function> mapNTF_uv = new HashMap<NodeType, Function>();
		mapNTF_uv.put(NodeType.Dirichlet, null);
		
		HashMap<NodeType, Function> mapNTF_p = new HashMap<NodeType, Function>();
		mapNTF_p.put(NodeType.Neumann, null);
		
		mesh.markBorderNode(new ObjIndex(1,2,3),mapNTF_uv);
		mesh.markBorderNode(4,mapNTF_p);

		//Use element library to assign degree of freedom (DOF) to element
		fe = new FETrilinearV_ConstantP();
		fe.initDOFIndexGenerator(mesh);
		for(int i=1;i<=eList.size();i++) {
			fe.assignTo(eList.at(i));
			//eList.at(i).printDOFInfo();
		}

		diri = new SpaceVectorFunction(4);
		diri.set(1, new AbstractFunction("x","y","z") {
					@Override
					public double value(Variable v) {
						double z = v.get("z");
						if(Math.abs(z-1.0)<Constant.meshEps)
							return 1.0;
						else
							return 0.0;
					}
				});
		diri.set(2, FC.c0);
		diri.set(3, FC.c0);
		diri.set(4, FC.c0);
		
	}
	
	public BlockVector nonlinearIter(int time, int nIter, SpaceVectorFunction uk) {
		//Right hand side(RHS): f = (0,0)'
		if(time==0)
			weakForm.setF(new SpaceVectorFunction(FC.c0,FC.c0,FC.c0));
		else
			weakForm.setF(new SpaceVectorFunction(
					uk.get(1).D(dt),
					uk.get(2).D(dt),
					uk.get(3).D(dt)
					));
		
		weakForm.setParam(FC.c(mu),U,FC.c(1.0/dt));
		
		assembler = new AssemblerVector(mesh, weakForm,fe);
		System.out.println("Begin Assemble...");
		assembler.assemble();
		BlockMatrix stiff = (BlockMatrix)assembler.getStiffnessMatrix();
		BlockVector load = (BlockVector)assembler.getLoadVector();
		assembler.imposeDirichletCondition(diri);
		System.out.println("Assemble done!");
		
		SchurComplementStokesSolver solver = 
			new SchurComplementStokesSolver(stiff,load);
		//solver.setCGInit(0.5);
		//solver.debug = true;
		return solver.solve3D();
		
	}	
	
	public BlockVector nonlinearIterSteady(int nIter, SpaceVectorFunction uk) {
		//Right hand side(RHS): f = (0,0)'
		weakForm.setF(new SpaceVectorFunction(FC.c0,FC.c0,FC.c0));
		weakForm.setParam(FC.c(mu),U,FC.c0);
		
		assembler = new AssemblerVector(mesh, weakForm,fe);
		System.out.println("Begin Assemble...");
		assembler.assemble();
		BlockMatrix stiff = (BlockMatrix)assembler.getStiffnessMatrix();
		BlockVector load = (BlockVector)assembler.getLoadVector();
		assembler.imposeDirichletCondition(diri);
		System.out.println("Assemble done!");
		
		SchurComplementStokesSolver solver = 
			new SchurComplementStokesSolver(stiff,load);
		//solver.setCGInit(0.5);
		//solver.debug = true;
		return solver.solve3D();
		
	}	
	
	public void run(int startTimeStep, int testCaseNo, boolean bSteady) {
		init(testCaseNo);
		if(bSteady) startTimeStep=0;
		
		if(startTimeStep>0) {
			Vector vecU = DataReader.readVector(String.format("./%s/%s_uvw_final_t%03d.dat",
					outputFolder,file,startTimeStep),3);
			Vector vecV = DataReader.readVector(String.format("./%s/%s_uvw_final_t%03d.dat",
					outputFolder,file,startTimeStep),4);
			Vector vecW = DataReader.readVector(String.format("./%s/%s_uvw_final_t%03d.dat",
					outputFolder,file,startTimeStep),5);
			U.set(1, new Vector2Function(vecU));
			U.set(2, new Vector2Function(vecV));
			U.set(3, new Vector2Function(vecW));
		} else {
			U.set(1, FC.c0);
			U.set(2, FC.c0);
			U.set(3, FC.c0);
		}
		
		BlockVector u = null;
		if(bSteady) System.out.println(">>>>>>>>>>>>>>>>>>>steady");
		SpaceVectorFunction uk = new SpaceVectorFunction(3);
		for(int time=startTimeStep+1;time<this.maxTimeStep;time++) {
			if(!bSteady) System.out.println(">>>>>>>>>>>>>>>>>>>time="+time);

			uk.set(1, U.get(1));
			uk.set(2, U.get(2));
			uk.set(3, U.get(3));
			for(int iter=0;iter<this.maxNonlinearIter;iter++) {
				if(bSteady)
					u = nonlinearIterSteady(iter, uk);
				else
					u = nonlinearIter(time, iter, uk);
				
				//Compute norm of delta_u (not including delta_v)
				int dim = u.getBlock(1).getDim();
				SparseVector delta_u = new SparseVector(dim);
				for(int i=1;i<=dim;i++)
					delta_u.set(i, 
							u.getBlock(1).get(i)-
							U.get(1).value(new Variable().setIndex(i)));
				
				U.set(1, new Vector2Function(u.getBlock(1)));
				U.set(2, new Vector2Function(u.getBlock(2)));
				U.set(3, new Vector2Function(u.getBlock(3)));
	
				System.out.println("Iter="+iter+" Error Norm2 (||u1_k+1 - u1_k||) = "+delta_u.norm2());
				
				if(delta_u.norm2() < this.nonlinearError) {
					String s = "_t%03d";
					if(bSteady) s = "_steady";
					Tools.plotVector(mesh, outputFolder, String.format("%s_uvw_final"+s+".dat",file,time), 
							u.getBlock(1), u.getBlock(2), u.getBlock(3));
					Tools.plotVector(meshOld, outputFolder, String.format("%s_p_final"+s+".dat",file,time), 
							Tools.valueOnElement2Node(mesh,u.getBlock(4)));
					if(bSteady)
						return;
					else
						break;
				} else {
					Tools.plotVector(mesh, outputFolder, String.format("%s_uvw%02d_%02d.dat",file,time,iter), 
							u.getBlock(1), u.getBlock(2), u.getBlock(3));
					Tools.plotVector(meshOld, outputFolder, String.format("%s_p%02d_%02d.dat",file,time,iter), 
							Tools.valueOnElement2Node(mesh, u.getBlock(4)));
				}
			}
		}
	}
	
	/**
	 * args[0]: test case number
	 * args[1]: is steady
	 * args[2]: delta t
	 * args[3]: start time step
	 * args[4]: max start time step
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		NavierStokesBox3D NSB = new NavierStokesBox3D();
		NSB.dt = 0.02;
		int startTimeStep = 0;
		int testCaseNo = 3;
		boolean bSteady = false;
		if(args.length >= 1)
			testCaseNo = Integer.parseInt(args[0]);
		if(args.length >= 2)
			bSteady = Boolean.parseBoolean(args[1]);
		if(args.length >= 3)
			NSB.dt = Double.parseDouble(args[2]);
		if(args.length >= 4)
			startTimeStep = Integer.parseInt(args[3]);
		if(args.length >= 5)
			NSB.maxTimeStep = Integer.parseInt(args[4]);
		
		System.out.println("testCaseNo="+testCaseNo);
		System.out.println("bSteady="+bSteady);
		System.out.println("dt="+NSB.dt);
		System.out.println("timeStep="+startTimeStep);
		System.out.println("maxTimeStep="+NSB.maxTimeStep);
		System.out.println("default values:");
		System.out.println("mu="+NSB.mu);
		System.out.println("maxNonlinearIter="+NSB.maxNonlinearIter);
		System.out.println("nonlinearError="+NSB.nonlinearError);
		
		NSB.run(startTimeStep,testCaseNo,bSteady);
	}
}
