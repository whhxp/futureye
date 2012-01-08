package edu.uta.futureye.application;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import no.uib.cipr.matrix.sparse.SparseVector;
import edu.uta.futureye.algebra.SparseBlockMatrix;
import edu.uta.futureye.algebra.SparseBlockVector;
import edu.uta.futureye.algebra.intf.BlockMatrix;
import edu.uta.futureye.algebra.intf.BlockVector;
import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.algebra.solver.Solver;
import edu.uta.futureye.algebra.solver.external.SolverJBLAS;
import edu.uta.futureye.core.DOF;
import edu.uta.futureye.core.DOFOrder;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.Mesh;
import edu.uta.futureye.core.Node;
import edu.uta.futureye.core.NodeRefined;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.core.Refiner;
import edu.uta.futureye.core.geometry.GeoEntity;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.basic.Vector2Function;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.operator.FMath;
import edu.uta.futureye.io.MeshReader;
import edu.uta.futureye.io.MeshWriter;
import edu.uta.futureye.lib.assembler.AssemblerScalar;
import edu.uta.futureye.lib.element.FEBilinearRectangle;
import edu.uta.futureye.lib.element.FELinearTriangle;
import edu.uta.futureye.lib.shapefun.SFBilinearLocal2D;
import edu.uta.futureye.lib.shapefun.SFLinearLocal2D;
import edu.uta.futureye.lib.weakform.WeakFormLaplace2D;
import edu.uta.futureye.util.PairDoubleInteger;
import edu.uta.futureye.util.Utils;
import edu.uta.futureye.util.container.DOFList;
import edu.uta.futureye.util.container.ElementList;
import edu.uta.futureye.util.container.NodeList;

/**
 * Implementation of the paper: 
 *   'A Framework For The Adaptive Finite Element Solution Of Large-Scale Inverse Problems'
 * 
 * Lagrange Multiplier Method based on the model:
 * 
 * -\nabla{(1/(a+k))*\nabla{u}} + a*u = 0
 * 
 * where 
 *   k = 3*mu_s'
 *   a = a(x) = 3*mu_a(x)
 *
 * Measurements take place on the boundary of \Omega (=\Gamma)
 *   
 * @author liuyueming
 *
 */
public class VariationGaussNewtonDOTPlusBoundary {
	public Mesh mesh;
	public Mesh meshBig;
	
	
	protected static String outputFolder = "Lagrangian_SuLiuBoundary";
	public boolean debug = false;

	//Back ground model
	ModelDOTPlus modelBk = new ModelDOTPlus();
	
	//Real inclusion model
	ModelDOTPlus modelReal = new ModelDOTPlus();

	//Real inclusion model
	ModelDOTPlus modelGuess = new ModelDOTPlus();

	//3*mu_s'
	Function model_k = FC.c(50.0);
	//background of a(x)=3*mu_a(x)=0.3
	double aBackground = 0.3;
	
    Function diri = null;
    
    //对应每个光源s_i的参数，包括测量数据
    public static class ParamOfLightSource {
    	int s_i;
    	Vector g; //= u|_\Gamma
    }

    //true : u_g在整个区域上都已知
    //false: u_g在边界上已知
	boolean bTestWholdDomain = false;
    
    //mu_a
    Vector aGlob = null;//GCM方法得到的a_glob(x)
    
    //正则化参数
    double beta = 1900;
    
    int iterNum = 0;
    int refineNum = 0;
    
    //光源x坐标位置数组
	double[] LS;

	FileOutputStream out = null;
	PrintWriter br = null;

    /**
     * 构造函数，设定包含物位置
     */
    public VariationGaussNewtonDOTPlusBoundary() {
		//Number of moving light sources
		int N=1; 
		LS = new double[N];
		double h = 0.2;
		LS[0] = 3.0;
		for(int i=1; i<N; i++)
			LS[i] = LS[0] + i*h;
		
		//背景mu_a
		modelBk.setMu_a(0.0, 0.0, 0.0, 
				0.1, //mu_a=0.1 mu_s(=model.k)=0.02 => a(x)=5
				1);
		//有包含物mu_a，真实模型
		modelReal.setMu_a(3.0, 2.50, 0.6,
				0.8, //peak value of mu_a
				1); //Number of inclusions
		//有包含物mu_a，猜测模型
		modelGuess.setMu_a(3.1, 2.50, 0.6,
				0.8, //peak value of mu_a
				1); //Number of inclusions

    }
    
    /**
     * 
     * @param s_i：Light source No. (0,1,2...)
     */
    public void reinitModelLight(int s_i) {
		modelBk.setDelta(LS[s_i], 3.5);
		modelBk.lightNum = s_i;
		modelReal.setDelta(LS[s_i], 3.5);
		modelReal.lightNum = s_i;
		modelGuess.setDelta(LS[s_i], 3.5);
		modelGuess.lightNum = s_i;
    }
    
	public static void plotVector(Mesh mesh, Vector v, String fileName) {
	    MeshWriter writer = new MeshWriter(mesh);
	    if(!outputFolder.isEmpty()) {
		    File file = new File("./"+outputFolder);
			if(!file.exists()) {
				file.mkdirs();
			}
	    }
	    writer.writeTechplot("./"+outputFolder+"/"+fileName, v);
	}

	public static void plotFunction(Mesh mesh, Function fun, String fileName) {
	    NodeList list = mesh.getNodeList();
	    int nNode = list.size();
		Variable var = new Variable();
		Vector v = new SparseVector(nNode);
	    for(int i=1;i<=nNode;i++) {
	    	Node node = list.at(i);
	    	var.setIndex(node.globalIndex);
	    	var.set("x", node.coord(1));
	    	var.set("y", node.coord(2));
	    	v.set(i, fun.value(var));
	    }
	    plotVector(mesh,v,fileName);
	}	
	
	public void beginLog() {
		try {
			File file = new File(".\\"+outputFolder+"\\output.txt");
			out = new FileOutputStream(file);
			OutputStreamWriter writer = new OutputStreamWriter(out, "UTF-8");
			br = new PrintWriter(writer);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public void endLog() {
		try {
			if(br != null)
				br.close();
			if(out != null)
				out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void readMeshTriangle(){
		String gridFileBig = "prostate_test3_ex.grd";
		String gridFileSmall = "prostate_test3.grd";

        MeshReader readerBig = new MeshReader(gridFileBig);
        MeshReader readerSmall = new MeshReader(gridFileSmall);
        meshBig = readerBig.read2DMesh();
        mesh = readerSmall.read2DMesh();
        meshBig.computeNodeBelongsToElements();
        mesh.computeNodeBelongsToElements();
        mesh.computeNeighborNodes();

        //2.Mark border types
        HashMap<NodeType, Function> mapNTF =
                new HashMap<NodeType, Function>();
        mapNTF.put(NodeType.Dirichlet, null);
        mesh.markBorderNode(mapNTF);
        
        //3.Use element library to assign degrees of
        //  freedom (DOF) to element
        ElementList eList = mesh.getElementList();
//        for(int i=1;i<=eList.size();i++) {
//        	System.out.println(eList.at(i));
//        }
        FELinearTriangle feLT = new FELinearTriangle();
        for(int i=1;i<=eList.size();i++)
            feLT.assignTo(eList.at(i));
  
        ElementList eListBig = meshBig.getElementList();
		for(int i=1;i<=eListBig.size();i++)
			feLT.assignTo(eListBig.at(i));

	}
	
	public void readMeshRectangle(){
		String gridFileBig = "prostate_test7_ex.grd";
		String gridFileSmall = "prostate_test7.grd";
//		String gridFileBig = "prostate_test8_ex.grd";
//		String gridFileSmall = "prostate_test8.grd";
		
        MeshReader readerBig = new MeshReader(gridFileBig);
        MeshReader readerSmall = new MeshReader(gridFileSmall);
        meshBig = readerBig.read2DMesh();
        mesh = readerSmall.read2DMesh();
        meshBig.computeNodeBelongsToElements();
        mesh.computeNodeBelongsToElements();
        mesh.computeNeighborNodes();
      
        //2.Mark border types
        HashMap<NodeType, Function> mapNTF =
                new HashMap<NodeType, Function>();
        mapNTF.put(NodeType.Dirichlet, null);
        mesh.markBorderNode(mapNTF);
        
        //3.Use element library to assign degrees of
        //  freedom (DOF) to element
        ElementList eList = mesh.getElementList();
        FEBilinearRectangle feLT = new FEBilinearRectangle();
        for(int i=1;i<=eList.size();i++)
            feLT.assignTo(eList.at(i));
  
        ElementList eListBig = meshBig.getElementList();
		for(int i=1;i<=eListBig.size();i++)
			feLT.assignTo(eListBig.at(i));

	}	
	
	public void refineMesh(ElementList eToRefine) {
        meshBig.computeNodeBelongsToElements();
        meshBig.computeNeighborNodes();
        meshBig.computeGlobalEdge();
        meshBig.computeNeighborElements();
        mesh.computeNodeBelongsToElements();
        mesh.computeNeighborNodes();
		mesh.computeGlobalEdge();
		mesh.computeNeighborElements();
		
		ElementList eToRefineBig = new ElementList();
		for(int i=1;i<=eToRefine.size();i++) {
			Element e = meshBig.getElementByNodes(eToRefine.at(i).nodes);
			eToRefineBig.add(e);
		}
		
		System.out.println("Before refine meshBig: Element="+meshBig.getElementList().size()+", Node="+mesh.getNodeList().size());
		Refiner.refineOnce(meshBig, eToRefineBig);
		System.out.println("After refine: Element="+meshBig.getElementList().size()+", Node="+mesh.getNodeList().size());

		System.out.println("Before refine mesh: Element="+mesh.getElementList().size()+", Node="+mesh.getNodeList().size());
		Refiner.refineOnce(mesh, eToRefine);
		System.out.println("After refine: Element="+mesh.getElementList().size()+", Node="+mesh.getNodeList().size());
		
		assignLinearShapFunction(meshBig);
		assignLinearShapFunction(mesh);
	}
	
	public void connectSells(Mesh mesh, Vector v) {
		NodeList nodes = mesh.getNodeList();
		for(int i=1;i<=nodes.size();i++) {
			Node node = nodes.at(i);
			if(node instanceof NodeRefined) {
				NodeRefined nRefined = (NodeRefined)node;
				if(nRefined.isHangingNode()) {
					v.set(node.globalIndex,
							v.get(nRefined.constrainNodes.at(1).globalIndex)*0.5+
							v.get(nRefined.constrainNodes.at(2).globalIndex)*0.5);
				}
			}
		}
	}
	
	public void zeroHangingNode(Mesh mesh, Vector v) {
		NodeList nodes = mesh.getNodeList();
		for(int i=1;i<=nodes.size();i++) {
			Node node = nodes.at(i);
			if(node instanceof NodeRefined) {
				NodeRefined nRefined = (NodeRefined)node;
				if(nRefined.isHangingNode()) {
					v.set(node.globalIndex, 0.0);
				}
			}
		}
	}
	
	/**
	 * Get stiff matrix and load vector for equation of 'lambda'
	 * 
	 * L_u(\lambda)=0:
	 *   ((1/(a+k))*\nabla{\psi},\nabla{\lambda}) + (a*\psi,\lambda) = -(u-g,psi)_\Gamma
	 * 
	 * Boundary Condition: Robin
	 *   (1/(a+k))*\partial_{n}{\lambda} = u - g, on \Gamma
	 * 
	 * where 
	 *    \Gamma = \partial\Omega
	 *    g = uReal|_\Gamma
	 * 
	 * Note:
	 *    bTestWholdDomain == false
	 *    
	 * @param s_i
	 * @param u: uk
	 * @param g: uReal|_\Gamma
	 * @return
	 */
	public Equation getEqnLambda(int s_i, Vector a, Vector u, Vector g) {
		WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
		//(u - g)_\Gamma
		Vector u_g = FMath.axpy(-1.0, g, u);
		NodeList nodes = mesh.getNodeList();
		for(int j=1;j<=nodes.size();j++) {
			if(nodes.at(j).isInnerNode())
				u_g.set(j,0.0);
		}
		Function fu_g = new Vector2Function(u_g);
		plotFunction(mesh, fu_g, String.format("M%02d_Lagrangian_u_g%02d.dat",s_i,this.iterNum));
		Function fa = new Vector2Function(a);

		if(bTestWholdDomain)
			weakForm.setF(fu_g.M(-1.0));//!!!!!!1.0=>
		else
			weakForm.setF(FC.c(0.0));

		//d*u + k*u_n = q
		//自然边界(u_n+u=0)+边界测量(-(u-g,\psi)_\Gamma)
		//
		weakForm.setParam(
				FC.C1.D(fa.A(model_k)),
				fa.D(3.0),
				fu_g.M(-1.0), //q=-(u-g) //bugfix: q=-(u-g)/(1/(a+k))=(g-u)*(a+k)
				FC.C1.D(fa.A(model_k)) //d=k
			);
		
//Error:		
//		weakForm.setParam(
//				FC.c1.D(fa.A(model_k)),
//				fa.D(3.0),
//				//null, //bugfix
//				//FC.c1.D(fa.A(model_k)).M(fu_g.M(-1.0)), bugfix
//				fu_g.M(-1.0),
//				null
//			);
		
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(bTestWholdDomain)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
		AssemblerScalar assembler = new AssemblerScalar(mesh, weakForm);
		System.out.println("Begin Assemble...lambda");
		assembler.assemble();
		Matrix stiff = assembler.getStiffnessMatrix();
		Vector load = assembler.getLoadVector();
		if(bTestWholdDomain)
			assembler.imposeDirichletCondition(FC.C0);
		System.out.println("Assemble done!");

		Equation eqn = new Equation();
		eqn.A = stiff;
		eqn.f = load;
		
		return eqn;
	}	
	
	/**
	 * Get stiff matrix and load vector for equation of 'u'
	 * 
	 * L_{\lambda}(u)=0:
	 *   ((1/(a+k))*\nabla{u},\nabla{\phi}) + (a*u,\phi) = 0
	 *  
	 * Boundary Condition: Robin
	 *   (1/(a+k))*\partial_{n}{u}  + (1/(a+k))*u = 0, on \Gamma
	 *   =>（实际计算的时候不能，参数中不能消去1/(a+k)）
	 *   (1/(a+k))*(\partial_{n}{u}  + u) = 0, on \Gamma
	 * 
	 * @param a
	 * @param g: 可选项
	 *     g==null, 以自然边界条件在大区域上计算u，然后截取到小区域上
	 *     g!=null, 以g为Dirichlet边界条件在小区域上计算u
	 * @return
	 */
	public Equation getEqnU(Mesh _mesh, Vector a, Vector g) {
		
		WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
		Function fa = new Vector2Function(a);
		
		//不能忽略光源的影响???
		//if(g == null)
			weakForm.setF(this.modelReal.delta);
		//else
		//	weakForm.setF(FC.c(0.0));
		
		weakForm.setParam(
				FC.C1.D(fa.A(model_k)),
				fa.D(3.0),
				null,
				FC.C1.D(fa.A(model_k)));
		
		_mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(g == null)
			mapNTF.put(NodeType.Robin, null);
		else
			mapNTF.put(NodeType.Dirichlet, null);
		_mesh.markBorderNode(mapNTF);

		AssemblerScalar assembler = new AssemblerScalar(_mesh, weakForm);
		System.out.println("Begin Assemble...u");
		assembler.assemble();
		Matrix stiff = assembler.getStiffnessMatrix();
		Vector load = assembler.getLoadVector();
		if(g != null)
			assembler.imposeDirichletCondition(new Vector2Function(g));
		System.out.println("Assemble done!");

		Equation eqn = new Equation();
		eqn.A = stiff;
		eqn.f = load;
		
		return eqn;
	}	
	
	public Equation getEqnU(Vector a, Vector g) {
		if(g == null) {
			//Robin条件，由于光源在区域外面，在小区域上直接求解会得到0解，因此
			//先在大区域上求解，然后截取解到小区域，作为Dirichlet边界在小区域上求解
			Vector aBig = Tools.extendData(mesh, meshBig, a, this.aBackground);
	        plotVector(meshBig,aBig,String.format("aBig%02d.dat",this.iterNum));
			
			Equation eq = getEqnU(meshBig,aBig,null);
	        Solver sol = new Solver();
	        Vector uBig = sol.solveCGS(eq.A, eq.f);
	        plotVector(meshBig,uBig,String.format("uBig%02d.dat",this.iterNum));
	        Vector uSmall = Tools.extractData(meshBig, mesh, uBig);
	        return getEqnU(mesh,a,uSmall);
		}
		else {
			//在小区域求解Dirichlet问题（得到系数矩阵和右端向量）
			return getEqnU(mesh,a,g);
		}
	}
	
	
	/**
	 * Get stiff matrix and load vector for equation of 'a(x)'
	 * 
	 * L_a(a)=0:
	 * 
	 * \beta(a-aGlob,\chi) - 
	 * 					\sum_{i=1,N}{
	 * 						 ((ak+k)^{-2}*\chi\nabla{u_i},\nabla{\lambda_i})
	 * 						-(\chi*u_i,\lambda_i)
	 * 					} = 0
	 * =>
	 * (a,\chi) = (aGlob,\chi) + (1/\beta)*
	 *                  \sum_{i=1,N}{
	 * 						 ((ak+k)^{-2}*\chi*\nabla{u_i},\nabla{\lambda_i})
	 * 						-(\chi*u_i,\lambda_i)
	 * 					}
	 */
	public Equation getEqnA(Vector[] u, Vector[] lambda, 
			Vector ak, Vector _aGlob, 
			Function diri) {
        WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
        weakForm.setParam(FC.C0, FC.C1, null, null);
        //stabilize
        //weakForm.setParam(FC.c(0.01), FC.c1, null, null);
        
        Function faGlob = new Vector2Function(_aGlob);
        int N=u.length;
        Vector[] vMlmd = new Vector[N];
        Function[] fuDotlmd = new Function[N];
        Vector[] uDotlmd = new Vector[N];
        for(int i=0; i<N; i++) {
        	//u_i*\lambda_i 
        	//u[i].axMuly(1.0,lambda[i])<=会改变u[i]的值
        	vMlmd[i] = FMath.axMuly(1.0, u[i],lambda[i]);
        	plotVector(mesh,vMlmd[i],
        			String.format("M%02d_La_RHS_v_lmd%02d.dat",i,this.iterNum));
        	
        	//\nabla{u_i} \cdot \nabla{\lambda_i}
//        	Function fu = new Vector2Function(u[i],mesh,"x","y");
//        	Function flmd = new Vector2Function(lambda[i],mesh,"x","y");
//        	VectorFunction grad_fu = FMath.grad(fu);
//        	VectorFunction grad_flmd = FMath.grad(flmd);
//        	
//        	//plotFunction(mesh,grad_fu.get(1),"u_x.dat");
//        	//plotFunction(mesh,grad_fu.get(2),"u_y.dat");
//        	//plotFunction(mesh,grad_flmd.get(1),"lmd_x.dat");
//        	//plotFunction(mesh,grad_flmd.get(2),"lmd_y.dat");
//        	
//        	fuDotlmd[i] = grad_fu.dot(grad_flmd);
//        	plotFunction(mesh,fuDotlmd[i],
//        			String.format("M%02d_La_RHS_v_lmd_Grad%02d.dat",i,this.iterNum));
        	
        	Vector ux = Tools.computeDerivative(mesh, u[i], "x");
        	Vector uy = Tools.computeDerivative(mesh, u[i], "y");
        	Vector lx = Tools.computeDerivative(mesh, lambda[i], "x");
        	Vector ly = Tools.computeDerivative(mesh, lambda[i], "y");
        	uDotlmd[i] = new SparseVector(ux.getDim());
        	uDotlmd[i].add(FMath.axMuly(1.0, ux, lx));
        	uDotlmd[i].add(FMath.axMuly(1.0, uy, ly));
        	plotVector(mesh,uDotlmd[i],
        			String.format("M%02d_La_RHS_v_lmd_Grad%02d.dat",i,this.iterNum));
        	this.connectSells(mesh, uDotlmd[i]);
        	plotVector(mesh,uDotlmd[i],
        			String.format("M%02d_La_RHS_v_lmd_Grad_ConnectSells%02d.dat",i,this.iterNum));
       	
        	        	
        }
//        Function sum1 = new Vector2Function(FMath.sum(vMlmd));
//        plotFunction(mesh,sum1,String.format("La_RHS_v_lmd_sum1_%02d.dat",this.iterNum));
//        //Function sum2 = FMath.sum(fuDotlmd);
//        Function sum2 = new Vector2Function(FMath.sum(uDotlmd));
//        plotFunction(mesh,sum2,String.format("La_RHS_v_lmd_sum2_%02d.dat",this.iterNum));
//        
//        Function akpk_2 = FMath.pow(new Vector2Function(ak).A(model_k),-2.0);
//        plotFunction(mesh,akpk_2,String.format("La_RHS_akpk_2_%02d.dat",this.iterNum));
//        //bugfix1
//        //Function rhs = akpk_2.M(-1.0/beta).M(sum2.A(sum1));
//        //bugfix2
//        //Function rhs = akpk_2.M(sum2).A(sum1).M(-1.0/beta);
//        Function rhs = akpk_2.M(sum2).A(sum1).M(1.0/beta);
//        plotFunction(mesh,rhs,String.format("La_RHS_rhs%02d.dat",this.iterNum));
//        Function f2 = faGlob.S(rhs);
//        plotFunction(mesh,f2,String.format("La_RHS_all%02d.dat",this.iterNum));
//        weakForm.setF(f2);   
        
		Vector sum1 = FMath.sum(vMlmd);
		this.connectSells(mesh, sum1);
		plotVector(mesh,sum1,String.format("La_RHS_v_lmd_sum1_%02d.dat",this.iterNum));
		Vector sum2 = FMath.sum(uDotlmd);
		this.connectSells(mesh, sum2);
		plotVector(mesh,sum2,String.format("La_RHS_v_lmd_sum2_%02d.dat",this.iterNum));
		  
		Function akpk_2 = FMath.pow(new Vector2Function(ak).A(model_k),-2.0);
		plotFunction(mesh,akpk_2,String.format("La_RHS_akpk_2_%02d.dat",this.iterNum));
		Vector sum12 = FMath.axpy(1.0, sum1, sum2);
		plotVector(mesh,sum12,String.format("La_RHS_v_lmd_sum12_%02d.dat",this.iterNum));
		  
		Function rhs = akpk_2.M(new Vector2Function(sum2)).A(new Vector2Function(sum1)).M(1.0/beta);
		plotFunction(mesh,rhs,String.format("La_RHS_rhs%02d.dat",this.iterNum));
		Function f2 = faGlob.S(rhs);
		plotFunction(mesh,f2,String.format("La_RHS_all%02d.dat",this.iterNum));
		weakForm.setF(f2);

        //需要重新标记边界条件
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		mapNTF.put(NodeType.Dirichlet, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...a(x)");
        assembler.assemble();
        Matrix stiff = assembler.getStiffnessMatrix();
        Vector load = assembler.getLoadVector();
        
        //Boundary condition
        //Note For Boundary: 不加边界条件
        //assembler.imposeDirichletCondition(diri);
        
        System.out.println("Assemble done!");

		Equation eqn = new Equation();
		eqn.A = stiff;
		eqn.f = load;
		
		return eqn;
	}
	
	public Equation getEqnA(Vector u, Vector lambda, 
			Vector ak, Vector _aGlob, 
			Function diri) {
		Vector[] vu = new Vector[1];
		Vector[] vlmd = new Vector[1];
		vu[0] = u;
		vlmd[0] = lambda;
		return getEqnA(vu, vlmd, ak, _aGlob, diri);
	}
	
	
	/**
	 * Residual of state equation L_{\lambda}(u)
	 * 
	 * 获取状态方程的余量
	 * @return
	 */
	public Vector getResLlmd(Vector a, Vector u, Vector g) {
		Equation eq = this.getEqnU(a, g);
        Vector res = new SparseVector(eq.f.getDim());
        eq.A.mult(u, res);
        res.add(-1.0, eq.f);
        //connectSells(mesh, res);
        zeroHangingNode(mesh,res);
        return res;
	}
	
	/**
	 * Residual of adjoint equation L_u(\lambda)
	 * 
	 * @return
	 */
	public Vector getResLu(int s_i,
			Vector a, Vector u, Vector g,
			Vector lambda) {
		Equation eq = this.getEqnLambda(s_i, a, u, g);
        Vector res = new SparseVector(eq.f.getDim());
        eq.A.mult(lambda, res);
        res.add(-1.0, eq.f);
        //res.axpy(-1.0, eq.f);
        //connectSells(mesh, res);
        zeroHangingNode(mesh,res);
        return res;
    }
	
	/**
	 * Residual of parameter regularization L_{q}
	 * 
	 * @param v
	 * @param lambda
	 * @param a_glob
	 * @return
	 */
	public Vector getResLq(Vector u, Vector lambda,
			Vector ak, Vector _aGlob, Function diri) {
		Equation eq = this.getEqnA(u, lambda, ak, _aGlob, diri);
        Vector res = new SparseVector(eq.f.getDim());
        eq.A.mult(ak, res);
        res.add(-1.0, eq.f);
        //connectSells(mesh, res);
        //zeroHangingNode(mesh,res);
        plotVector(mesh,res,String.format("Res_La%02d.dat",this.iterNum));
        
        //光滑余量
        //res = Utils.gaussSmooth(mesh, res, 2, 0.5);
        //plotVector(mesh,res,String.format("Res_LaSmooth%02d.dat",this.iterNum));
        
        Solver sol = new Solver();
        Vector a_solve = sol.solveCGS(eq.A, eq.f);
        plotVector(mesh,a_solve,String.format("a_solve%02d.dat",this.iterNum));
        return res;
	}
	
	public Vector getResLq(Vector[] u, Vector[] lambda,
			Vector ak, Vector _aGlob, Function diri) {
		Equation eq = this.getEqnA(u, lambda, ak, _aGlob, diri);
        Vector res = new SparseVector(eq.f.getDim());
        eq.A.mult(ak, res);
        plotVector(mesh,res,String.format("Res_La_mult%02d.dat",this.iterNum));
        //this.connectSells(mesh, res);
        //plotVector(mesh,res,String.format("Res_La_mult_connectSells%02d.dat",this.iterNum));
        
        plotVector(mesh,eq.f,String.format("Res_La_f%02d.dat",this.iterNum));
        //this.connectSells(mesh, eq.f);
        //plotVector(mesh,eq.f,String.format("Res_La_f_connectSells%02d.dat",this.iterNum));

        res.add(-1.0, eq.f);
        plotVector(mesh,res,String.format("Res_La%02d.dat",this.iterNum));
        //this.connectSells(mesh, res);
        //plotVector(mesh,res,String.format("Res_La_connectSells%02d.dat",this.iterNum));
        
        zeroHangingNode(mesh,res);
        plotVector(mesh,res,String.format("Res_La_zeroHangingNode%02d.dat",this.iterNum));

        
        //光滑余量
        //res = Utils.gaussSmooth(mesh, res, 1, 0.5);
        //plotVector(mesh,res,String.format("Res_LaSmooth%02d.dat",this.iterNum));
        
        //直接求解a(x)
        Solver sol = new Solver();
        Vector a_solve = sol.solveCGS(eq.A, eq.f);
        plotVector(mesh,a_solve,String.format("a_solve%02d.dat",this.iterNum));
        return res;
	}
	
	
	/////////////////////////////////////////////////////////////////////////
	//Begin*************Construction of search direction matrix**************
	/*
	 *  ( M  A'  0 )(du)     (Lu)
	 *  ( A  0   C )(dl) = - (Ll)
	 *  ( 0  C' bR )(dq)     (Lq)
	 */
	//***********************************************************************
	
	/**
	 * Weak form of 'M'
	 * 
	 * 整体测量：(du,\phi)_\Omega
	 * 边界测量：(du,\phi)_\Gamma
	 * 
	 */
	public Matrix getM() {
        WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
        
        //weakForm.setParam(FC.c0, FC.c1, null, null);
        //stabilize
        //Robin:  d*u + k*u_n = q
//        weakForm.setParam(FC.c(0.003), FC.c1, null, null);
        weakForm.setParam(
        		FC.c(0.0), //null==(k=1)
        		FC.c(0.0), 
        		null, //q=0
        		FC.C1 //d=1
        		);
        //Right hand side(RHS): f(x) = 0
        weakForm.setF(FC.C0);

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		//Note for Boundary:
		mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);

        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...M");
        assembler.assemble(false);
        Matrix stiff = assembler.getStiffnessMatrix();
        //Boundary condition 
        //边界条件需要在大矩阵中设置
        //assembler.imposeDirichletCondition(FC.c0);
        System.out.println("Assemble done!");
        
        return stiff;
    }
	
	/**
	 * Weak form of 'A'
	 * 
	 * ((1/(a+k)*\nabla{du},\nabla{\psi}) + ((a*du,\phi)
	 * 
	 * where 
	 *   du=\delta{u}
	 */
	public Matrix getA(Vector ak, boolean procHangingNode) {
		return getA(ak,null,null,procHangingNode).A;
	}
	public Equation getA(Vector ak, Function f, Function diri,boolean procHangingNode) {
		WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
		Function fa = new Vector2Function(ak);
		
		if(f==null)
			weakForm.setF(FC.c(0.0));
		else
			weakForm.setF(f);
		weakForm.setParam(
				FC.C1.D(fa.A(model_k)),
				fa.D(3.0),
				null,
				FC.C1.D(fa.A(model_k)));

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		//Note for boundary: Robin
		mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...A");
        assembler.assemble(procHangingNode);
        Matrix stiff = assembler.getStiffnessMatrix();
        Vector load = null;
        if(f != null)
        	load = assembler.getLoadVector();
        if(diri != null)//'A'的边界条件需要在大矩阵中设置
        	assembler.imposeDirichletCondition(diri);
        System.out.println("Assemble done!");
        
		Equation eqn = new Equation();
		eqn.A = stiff;
		eqn.f = load;
		
		return eqn;
	}
	
	/**
	 * Weak form of 'AT'
	 * 
	 * ((1/(a+k)*\nabla{\phi},\nabla{dl}) + ((a*\phi,dl)
	 * 
	 * where 
	 *   dl=\delta{\lambda}
	 */
	public Matrix getAT(Vector ak) {
		return getA(ak,true).trans();
	}
	
	/**
	 * Weak form of 'C'
	 * 
	 * ((-(a+k)^{-2}*da*\nabla{u},\nabla{\psi}) + ((da*u,\psi)
	 * 
	 * @param ak
	 * @param uk
	 * @return
	 */
	public Matrix getC(Vector ak, Vector uk) {
        WeakFormGCMDual weakForm = new WeakFormGCMDual();
        Function fuk = new Vector2Function(uk);
        Function fa = new Vector2Function(ak);
        Function b1 = new Vector2Function(Tools.computeDerivative(mesh, uk, "x"));
        Function b2 = new Vector2Function(Tools.computeDerivative(mesh, uk, "y"));
        Function _apk_2 = FMath.pow(fa.A(model_k),-2).M(-1.0);
        
        weakForm.setParam(FC.C0, fuk, b1.M(_apk_2), b2.M(_apk_2));
        //stabilize
        //weakForm.setParam(FC.c(0.0001), fuk, b1.M(_apk_2), b2.M(_apk_2));
        weakForm.setF(FC.C0);
       //??? weakForm.setRobin(q, d)

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		//Note for boundary: Robin? Dirichlet相当于Robin
		mapNTF.put(NodeType.Dirichlet, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...C");
        assembler.assemble(false);
        Matrix stiff = assembler.getStiffnessMatrix();
        //Boundary condition
        //边界条件需要在大矩阵中设置
        //assembler.imposeDirichletCondition(FC.c0);
        System.out.println("Assemble done!");
        
        return stiff;
	}
	
	/**
	 * Weak form of 'CT'
	 * 
	 * ((-(a+k)^{-2}*\chi*\nabla{u},\nabla{dl}) + ((\chi*u,dl)
	 * 
	 * where
	 * 
	 *   dl=\delta{\lambda}
	 * 
	 * @param ak
	 * @param uk
	 * @return
	 */
	public Matrix getCT(Vector ak, Vector uk) {
        WeakFormGCM weakForm = new WeakFormGCM();
        Function fuk = new Vector2Function(uk);
        Function fa = new Vector2Function(ak);
        Function b1 = new Vector2Function(Tools.computeDerivative(mesh, uk, "x"));
        Function b2 = new Vector2Function(Tools.computeDerivative(mesh, uk, "y"));
        Function _apk_2 = FMath.pow(fa.A(model_k),-2).M(-1.0);
        
        weakForm.setParam(FC.C0, fuk, b1.M(_apk_2), b2.M(_apk_2));
        //stabilize
        //weakForm.setParam(FC.c(0.0001), fuk, b1.M(_apk_2), b2.M(_apk_2));
        weakForm.setF(FC.C0);
        //??? weakForm.setRobin(q, d)

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		//Note for boundary: Robin? Dirichlet相当于Robin
		mapNTF.put(NodeType.Dirichlet, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...C");
        assembler.assemble(false);
        Matrix stiff = assembler.getStiffnessMatrix();
        //Boundary condition
        //边界条件需要在大矩阵中设置
        //assembler.imposeDirichletCondition(FC.c0);
        System.out.println("Assemble done!");
        
        return stiff;		
	}
	
	/**
	 * Weak form of '\beta*R'
	 * 
	 * \beta*(da,\chi)

	 * @return
	 */
	public Matrix getBR() {
        WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
        weakForm.setParam(FC.C0, FC.c(beta), null, null);
        //stabilize
        //weakForm.setParam(FC.c(0.01), FC.c(beta), null, null);
        
        weakForm.setF(FC.C0);

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		mapNTF.put(NodeType.Dirichlet, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...R");
        assembler.assemble();
        Matrix stiff = assembler.getStiffnessMatrix();
        //Boundary condition
        //边界条件需要在大矩阵中设置
        //assembler.imposeDirichletCondition(FC.c0);
        System.out.println("Assemble done!");
        
        return stiff;
	}
	
	//End*************Construction of search direction matrix**************
	/////////////////////////////////////////////////////////////////////////
	
	/**
	 * 真解u
	 * 
	 * @return
	 */
	public Vector solveRealU(int s_i) {
		Vector uBig = modelReal.solveNeumann(meshBig);
		plotVector(meshBig, uBig, String.format("M%02d_uRealBig.dat",s_i));
		
		//截取meshBig的部分解到mesh上
		Vector uSamll = Tools.extractData(meshBig, mesh, uBig);
		plotVector(mesh, uSamll, String.format("M%02d_uReal.dat",s_i));
		
		NodeList nodes = mesh.getNodeList();
		Vector aReal = new SparseVector(nodes.size());
		for(int j=1;j<=nodes.size();j++) {
			double mu_a = modelReal.mu_a.value(
							Variable.createFrom(modelReal.mu_a, nodes.at(j), j));
			aReal.set(j, 3.0*mu_a);
		}
		Vector uRealDiri = solveStateEquation(aReal, uSamll);
		plotVector(mesh, uRealDiri, String.format("M%02d_uRealDiri.dat",s_i));
		
		plotVector(mesh, FMath.axpy(-1.0, uRealDiri,uSamll), 
				String.format("M%02d_uReal_uRealDiri.dat",s_i));
		
        return uSamll;
	}
	
	/**
	 * Solve du=\delat{u} based on the second search direction equation
	 * 
	 *     A*du + C*da = -ResLlmd
	 *   =>
	 *     A*du = -ResLlmd-C*da
	 *   =>
	 *     du = inv(A)*(-ResLlmd-C*da)
	 *     
	 * where 
	 *  ResLlmd = residual of L_{\lambda}
	 * 
	 * @param ak
	 * @param _resLlmd_da: -ResLlmd-C*\delta{a}
	 * @param uk
	 * @return
	 */
	public Vector solveDeltaU(Vector ak, Vector _resLlmd_da, Vector uk) {
		Equation eq = getA(ak,new Vector2Function(_resLlmd_da),FC.C0,true);
        Solver sol = new Solver();
        Vector x = sol.solveCGS(eq.A, eq.f);
        return x;
	}

	/**
	 * 求解关于u的状态方程
	 * 
	 * @param a 
	 * @return
	 */
	public Vector solveStateEquation(Vector a, Vector g) {
		Equation eq = this.getEqnU(a, g);
        Solver solver = new Solver();
        Vector u = solver.solveCGS(eq.A, eq.f);
        return u;
	}
	
	/**
	 * 求解关于lambda的伴随方程
	 * 
	 * @param s_i
	 * @param a
	 * @param u
	 * @param g: u|_\Gamma
	 * @return
	 */
	public Vector solveAdjointEquation(int s_i,  
			Vector a, Vector u, Vector g) {
		Equation eq = this.getEqnLambda(s_i,a, u, g);
        Solver solver = new Solver();
        Vector lmd_solve = solver.solveCGS(eq.A, eq.f);
        return lmd_solve;
    }
	
	protected void setDirichlet(BlockMatrix BM, BlockVector BV,
			int matIndex, double value) {
		int row = matIndex;
		int col = matIndex;
		BM.set(row, col, 1.0);
		BV.set(row,value);
		for(int r=1;r<=BM.getRowDim();r++) {
			if(r != row) {
				BV.add(r,-BM.get(r, col)*value);
				BM.set(r, col, 0.0);
			}
		}
		for(int c=1;c<=BM.getColDim();c++) {
			if(c != col) {
				BM.set(row, c, 0.0);
			}
		}
	}
	
	public void imposeDirichletCondition(BlockMatrix BM, BlockVector BV,
			Function diri) {
		ElementList eList = mesh.getElementList();
		int nNode = mesh.getNodeList().size();
		for(int i=1;i<=eList.size();i++) {
			Element e = eList.at(i);
			DOFList DOFs = e.getAllDOFList(DOFOrder.NEFV);
			for(int j=1;j<=DOFs.size();j++) {
				DOF dof = DOFs.at(j);
				GeoEntity ge = dof.getOwner();
				if(ge instanceof Node) {
					Node n = (Node)ge;
					if(n.getNodeType() == NodeType.Dirichlet) {
						Variable v = Variable.createFrom(diri, n, 0);
						setDirichlet(BM,BV,dof.getGlobalIndex(),diri.value(v));
						//setDirichlet(BM,BV,nNode+dof.getGlobalIndex(),diri.value(v));
						//setDirichlet(BM,BV,nNode*2+dof.getGlobalIndex(),diri.value(v));
					}
				}
			}
		}
	}
	
	public void imposeDirichletCondition(BlockMatrix BM, BlockVector BV,
			int nDataBlock, Function diri) {
		ElementList eList = mesh.getElementList();
		int nNode = mesh.getNodeList().size();
		for(int i=1;i<=eList.size();i++) {
			Element e = eList.at(i);
			DOFList DOFs = e.getAllDOFList(DOFOrder.NEFV);
			for(int j=1;j<=DOFs.size();j++) {
				DOF dof = DOFs.at(j);
				GeoEntity ge = dof.getOwner();
				if(ge instanceof Node) {
					Node n = (Node)ge;
					if(n.getNodeType() == NodeType.Dirichlet) {
						Variable v = Variable.createFrom(diri, n, 0);
						for(int k=0;k<nDataBlock;k++) {
							setDirichlet(BM,BV,k*nNode+dof.getGlobalIndex(),diri.value(v));
							setDirichlet(BM,BV,(nDataBlock+k)*nNode+dof.getGlobalIndex(),diri.value(v));
						}
						setDirichlet(BM,BV,nDataBlock*2*nNode+dof.getGlobalIndex(),diri.value(v));
					}
				}
			}
		}
	}
	
	/**
	 * 
	 *  (M  A'  0)
	 *  (A  0   C)
	 *  (0  C'  R)
	 * 
	 */
	public BlockMatrix getSearchDirectionMatrix(Vector ak, Vector uk) {
		BlockMatrix BM = new SparseBlockMatrix(3,3);
		Matrix M = this.getM();
		Matrix A = this.getA(ak,true);
		Matrix AT = this.getAT(ak);
		Matrix C = this.getC(ak,uk);
		Matrix CT = this.getCT(ak,uk);
		Matrix R = this.getBR();
		
		Matrix BM13 = new SparseMatrix(M.getRowDim(),R.getColDim());
		Matrix BM22 = new SparseMatrix(A.getRowDim(),A.getColDim());
		Matrix BM31 = new SparseMatrix(R.getRowDim(),M.getColDim());
		
		BM.setBlock(1, 1, M);
		BM.setBlock(1, 2, AT);
		BM.setBlock(1, 3, BM13);
		
		BM.setBlock(2, 1, A);
		BM.setBlock(2, 2, BM22);
		BM.setBlock(2, 3, C);
		
		BM.setBlock(3, 1, BM31);
		BM.setBlock(3, 2, CT);
		BM.setBlock(3, 3, R);
		
		return BM;
	}
	
	/**
	 * 
	 * (M1        |AT1          | 0  )
	 * (  M2      |   AT2       | 0  )
	 * (    ...   |      ...    | .. )
	 * (       MN |         ATN | 0  )
	 *  ----------------------------
	 * (A1        |0            | C1 )
	 * (  A2      |    0        | C2 )
	 * (    ...   |      ...    | .. )
	 * (       AN |          0  | CN )
	 *  ----------------------------
	 * (0 0 ... 0 |CT1 CT2 . CTN| R  )
	 * 
	 */	
	public BlockMatrix getSearchDirectionMatrix(Vector ak, Vector[] uk,
			List<ParamOfLightSource> paramList) {
		
		int nDataBlock = uk.length;
		BlockMatrix BM = new SparseBlockMatrix(nDataBlock*2+1,nDataBlock*2+1);
		
		Matrix R = this.getBR();
		BM.setBlock(nDataBlock*2+1, nDataBlock*2+1, R.setName("R"));
		
		for(int i=1;i<=nDataBlock;i++) {
			Matrix M = this.getM();
			Matrix A = this.getA(ak,true);
			Matrix AT = this.getAT(ak);
			Matrix C = this.getC(ak,uk[i-1]);
			Matrix CT = this.getCT(ak,uk[i-1]);
			for(int j=1;j<=nDataBlock;j++) {
				if(i==j) {
					BM.setBlock(i, j, M.setName("M"+i));
					BM.setBlock(i, nDataBlock+j, AT.setName("AT"+i));
					BM.setBlock(nDataBlock+i, j, A.setName("A"+i));
					BM.setBlock(nDataBlock+i, nDataBlock*2+1, C.setName("C"+i));
					BM.setBlock(nDataBlock*2+1, nDataBlock+i, CT.setName("CT"+i));
					
					BM.setBlock(nDataBlock+i, nDataBlock+i, 
							new SparseMatrix(A.getRowDim(),AT.getColDim()));
					BM.setBlock(i, nDataBlock*2+1, 
							new SparseMatrix(M.getRowDim(),R.getColDim()));
					BM.setBlock(nDataBlock*2+1, i, 
							new SparseMatrix(R.getRowDim(),M.getColDim()));
					
				} else {
					
					Matrix M0 = new SparseMatrix(M.getRowDim(),M.getColDim());
					Matrix AT0 = new SparseMatrix(AT.getRowDim(),AT.getColDim());
					Matrix A0 = new SparseMatrix(A.getRowDim(),A.getColDim());
					Matrix ATA0 = new SparseMatrix(A.getRowDim(),AT.getColDim());
					BM.setBlock(i, j, M0);
					BM.setBlock(i, nDataBlock+j, AT0);
					BM.setBlock(nDataBlock+i, j, A0);
					BM.setBlock(nDataBlock+i, nDataBlock+j, ATA0);
				}
			}		
		}
		return BM;
	}
	
	/**
	 * Return a new BolckMatrix object that share the same sub matrix objects
	 * 
	 * @param BM
	 * @param col1
	 * @param col2
	 * @return
	 */
	public BlockMatrix changeBlockColumn(BlockMatrix BM, int col1, int col2) {
		int blockRow = BM.getRowBlockDim();
		int blockCol = BM.getColBlockDim();
		
		BlockMatrix newBM = new SparseBlockMatrix(blockRow, blockCol);
		for(int i=1;i<=blockRow;i++) {
			for(int j=1;j<=blockCol;j++) {
				newBM.setBlock(i, j, BM.getBlock(i, j));
			}
		}
		
		for(int i=1;i<=BM.getRowBlockDim();i++) {
			newBM.setBlock(i, col1, BM.getBlock(i, col2));
			newBM.setBlock(i, col2, BM.getBlock(i, col1));
		}
		return newBM;
	}	
	
	public BlockMatrix changeBlockColumn(BlockMatrix BM, int nDataBlock) {
		int blockRow = BM.getRowBlockDim();
		int blockCol = BM.getColBlockDim();
		
		BlockMatrix newBM = new SparseBlockMatrix(blockRow, blockCol);
		for(int i=1;i<=blockRow;i++) {
			for(int j=1;j<=blockCol;j++) {
				newBM.setBlock(i, j, BM.getBlock(i, j));
			}
		}
		
		for(int i=1;i<=BM.getRowBlockDim();i++) {
			for(int col=1;col<=nDataBlock;col++) {
				newBM.setBlock(i, col, BM.getBlock(i, nDataBlock+col));
				newBM.setBlock(i, nDataBlock+col, BM.getBlock(i, col));
			}
		}
		return newBM;
	}		
	
	public void testRefine() {
		//ElementList eToRefine = computeRefineElement(mesh, alpha_avg_smooth, 0.06);
		ElementList eToRefine = new ElementList();
		eToRefine.add(mesh.getElementList().at(1));
		eToRefine.add(mesh.getElementList().at(2));
		eToRefine.add(mesh.getElementList().at(3));
		eToRefine.add(mesh.getElementList().at(15));
		eToRefine.add(mesh.getElementList().at(16));
		eToRefine.add(mesh.getElementList().at(17));
		this.refineMesh(eToRefine);
	}

	public void testRefine2() {
		ElementList eList = mesh.getElementList();
		ElementList eToRefine = new ElementList();

		for(int i=1;i<=eList.size();i++) {
			Element e = eList.at(i);
			NodeList nodes = e.nodes;
			for(int j=1;j<=nodes.size();j++) {
				Node node = nodes.at(j);
				if(node.coord(1)>2.4 && node.coord(1)<3.8 &&
						node.coord(2)>1.3) {
					eToRefine.add(e);
					break;
				}
			}
		}
		
		this.refineMesh(eToRefine);
	}

	
	public void refine(Vector ak) {
		ElementList eToRefine = computeRefineElement(mesh, ak, 0.06);
		this.refineMesh(eToRefine);
	}	
	
	public void assignLinearShapFunction(Mesh mesh) {
		ScalarShapeFunction[] shapeFun = null;
		ScalarShapeFunction[] shapeFunHalf = null;
		ScalarShapeFunction[] shapeFunRect = null;
		ScalarShapeFunction[] shapeFunRectHalf = null;
		
		//Assign degree of freedom to element
		shapeFun = new SFLinearLocal2D[3];
		shapeFunHalf = new SFLinearLocal2D[3];
		for(int i=0;i<3;i++) {
			shapeFun[i] = new SFLinearLocal2D(i+1);
			shapeFunHalf[i] = new SFLinearLocal2D(i+1,0.5);
		}
		
		shapeFunRect = new SFBilinearLocal2D[4];
		shapeFunRectHalf = new SFBilinearLocal2D[4];
		for(int i=0;i<4;i++) {
			shapeFunRect[i] = new SFBilinearLocal2D(i+1);
			shapeFunRectHalf[i] = new SFBilinearLocal2D(i+1,0.5);
		}
		
		//Assign shape function to DOF
		for(int i=1;i<=mesh.getElementList().size();i++) {
			Element e = mesh.getElementList().at(i);
			e.clearAllDOF();
			if(e.nodes.size() == 4) {
				//Asign degree of freedom to element
				int nDofLocalIndexCounter = 0;
				for(int j=1;j<=e.nodes.size();j++) {
					//Asign shape function to DOF
					if(e.nodes.at(j) instanceof NodeRefined) {
						NodeRefined nRefined = (NodeRefined)e.nodes.at(j);
						if(nRefined.isHangingNode()) {
							DOF dof  = new DOF(++nDofLocalIndexCounter,nRefined.constrainNodes.at(1).globalIndex,
									shapeFunRectHalf[j-1]);
							e.addNodeDOF(j, dof);
							DOF dof2 = new DOF(++nDofLocalIndexCounter,nRefined.constrainNodes.at(2).globalIndex,
									shapeFunRectHalf[j-1]);
							e.addNodeDOF(j, dof2);
						} else {
							DOF dof = new DOF(++nDofLocalIndexCounter,e.nodes.at(j).globalIndex,shapeFunRect[j-1]);
							e.addNodeDOF(j, dof);				
						}
					} else {
						DOF dof = new DOF(++nDofLocalIndexCounter,e.nodes.at(j).globalIndex,shapeFunRect[j-1]);
						e.addNodeDOF(j, dof);
					}
				}
			} else if(e.nodes.size() == 3) {
				int nDofLocalIndexCounter = 0;
				for(int j=1;j<=e.nodes.size();j++) {
					//Asign shape function to DOF
					if(e.nodes.at(j) instanceof NodeRefined) {
						NodeRefined nRefined = (NodeRefined)e.nodes.at(j);
						if(nRefined.isHangingNode()) {
							DOF dof  = new DOF(++nDofLocalIndexCounter,nRefined.constrainNodes.at(1).globalIndex,
									shapeFunHalf[j-1]);
							e.addNodeDOF(j, dof);
							DOF dof2 = new DOF(++nDofLocalIndexCounter,nRefined.constrainNodes.at(2).globalIndex,
									shapeFunHalf[j-1]);
							e.addNodeDOF(j, dof2);
						} else {
							DOF dof = new DOF(++nDofLocalIndexCounter,e.nodes.at(j).globalIndex,shapeFun[j-1]);
							e.addNodeDOF(j, dof);				
						}
					} else {
						DOF dof = new DOF(++nDofLocalIndexCounter,e.nodes.at(j).globalIndex,shapeFun[j-1]);
						e.addNodeDOF(j, dof);
					}
				}
				
			} else {
				System.out.println("Error: e.nodes.size()="+e.nodes.size());
			}
		}
	}
	
	
	public ElementList computeRefineElement(Mesh mesh, Vector v, double persent) {
	    Vector v_smooth = Utils.gaussSmooth(mesh, v, 1, 0.5);

		ElementList eList = mesh.getElementList();
		ElementList eToRefine = new ElementList();

		List<PairDoubleInteger> list = new ArrayList<PairDoubleInteger>();
		for(int i=1;i<=eList.size();i++) {
			Element e = eList.at(i);
			Vector v1 = new SparseVector(e.nodes.size());
			for(int j=1;j<=e.nodes.size();j++) {
				Node node = e.nodes.at(j);
				v1.set(j, v.get(node.globalIndex)-v_smooth.get(node.globalIndex));
			}
			list.add(new PairDoubleInteger(v1.norm2(),i));
		}
		Collections.sort(list, new Comparator<PairDoubleInteger>() {
			@Override
			public int compare(PairDoubleInteger o1, PairDoubleInteger o2) {
				return o1.d < o2.d ? 1 : -1;
			}
		});
		int nThreshold = (int) Math.floor(persent*eList.size());
		for(int i=1;i<=nThreshold;i++) {
			eToRefine.add(eList.at(list.get(i).i));
		}
		return eToRefine;
	}	
	
	public Vector gaussNewtonIterateMulti(List<ParamOfLightSource> paramList) {
		int nDataBlock = paramList.size();
		
		//*************************Initial Values********************
		//a0=a_glob
		Vector a0 = this.aGlob.copy();//copy很重要
		plotVector(mesh, a0, "a0.dat");
		
		//u0: 求解状态方程得到
		Vector[] u0 = new SparseVector[nDataBlock];
		//lambda0: 求解伴随方程得到
		Vector[] lambda0 = new SparseVector[nDataBlock];
		
		for(int i=0;i<nDataBlock;i++) {
			//!!!
			this.reinitModelLight(i);
			
			ParamOfLightSource param =  paramList.get(i);
			
			//根据初值a0计算u0，边界条件为自然边界条件，这样会与测量边界有一个差值
			u0[i] = this.solveStateEquation(a0,null);
			plotVector(mesh, u0[i], String.format("M%02d_u0.dat",i));
			//输出差值
			plotVector(mesh, FMath.axpy(-1.0, param.g, u0[i]), String.format("M%02d_u_g.dat",i));
			
			//伴随方程需要施加一个Neumann边界条件：(u-g,\psi)_\Gamma
			lambda0[i] = this.solveAdjointEquation(param.s_i, a0, u0[i], param.g);
			plotVector(mesh, lambda0[i], String.format("M%02d_lambda0.dat",i));
		}
		//************************************************************
		
		
		//迭代求解
		BlockVector f = new SparseBlockVector(nDataBlock*2+1);
		for(int iter=0;iter<1;iter++) {
			
			this.iterNum = iter;
			
			//步长矩阵
			BlockMatrix BM = this.getSearchDirectionMatrix(a0, u0, paramList);
			
			//步长右端向量
			for(int i=1;i<=nDataBlock;i++) {
				//!!!
				this.reinitModelLight(i-1);

				ParamOfLightSource param =  paramList.get(i-1);
				//状态方程余量
				//Note for boundary:稀疏矩阵中的边界采用Neumann边界
				Vector resLlmd = this.getResLlmd(a0, u0[i-1],null).scale(-1.0);
		        plotVector(mesh,resLlmd,
		        		String.format("M%02d_Res_Llambda%02d.dat",i-1,this.iterNum));
				f.setBlock(nDataBlock+i, resLlmd);			
				
				//伴随方程余量
				Vector resLu = this.getResLu(param.s_i, 
					a0, u0[i-1], param.g,lambda0[i-1]).scale(-1.0);//？1.0
		        plotVector(mesh,resLu,
		        		String.format("M%02d_Res_Lv%02d.dat",i-1,this.iterNum));

				f.setBlock(i, resLu);
			}
			//正则化参数方程余量
			//Note for boundary: Dirichlet边界条件（=背景）
			f.setBlock(nDataBlock*2+1, this.getResLq( 
					u0, lambda0, a0, aGlob,
					FC.c(this.aBackground) //a(x)的边界条件: back ground of a(x)
					).scale(-1.0));

			
			//设置边界条件并求解
			//需要交换矩阵BM的第1, 2列，然后设置边界条件，这样使得矩阵A, A'可逆
			//BlockMatrix newBM = this.changeBlockColumn(BM, nDataBlock);
			//this.imposeDirichletCondition(newBM, f, nDataBlock, FC.c0);
			//SchurComplementLagrangianSolver solver = 
			//	new SchurComplementLagrangianSolver(BM, f,mesh);
			//BlockVector x = solver.solveMulti();

			//this.imposeDirichletCondition(BM, f, FC.c0);
			//Note for Boundary: 不加边界条件
			//this.imposeDirichletCondition(BM, f, nDataBlock,FC.c0);
			SolverJBLAS sol = new SolverJBLAS();
			BlockVector x = (BlockVector)sol.solveDGESV(BM, f);
			
			for(int i=1;i<=nDataBlock;i++) {
				plotVector(mesh, x.getBlock(i), String.format("M%02d_delta_v%02d.dat",i-1,iter));
				plotVector(mesh, x.getBlock(nDataBlock+i), String.format("M%02d_delta_lambda%02d.dat",i-1,iter));
			}
			
			Vector delta_a = x.getBlock(nDataBlock*2+1);
			//截取一定范围的结果，其他地方都置零
			//!!!只截取delta_a还不够，还需要重新计算v和lambda
//			for(int j=1;j<=nodes.size();j++) {
//				Node node = nodes.at(j);
//				if(node.coord(1)<2 || node.coord(1)>4)
//					delta_a.set(j, 0.0);
//				else if(node.coord(2)<1.5)
//					delta_a.set(j, 0.0);
//					
//			}
			plotVector(mesh, delta_a, String.format("delta_a%02d.dat",iter));
			
//			for(int i=0;i<nDataBlock;i++) {
//				//!!!
//				this.reinitModelLight(i);
//				
//				ParamOfLightSource param =  paramList.get(i);
//				//????????????????????delta_a
//				Vector dvs = solveDeltaU(a0, delta_a, u0[i]);
//				plotVector(mesh, dvs, String.format("M%02d_rlt_delta_v_solve%02d.dat",i,iterNum));
//			}
			
			//自动选取步长
			double stepLength = 1.0;
			double error = 0.0;
			for(int i=1;i<=nDataBlock;i++) {
				ParamOfLightSource param =  paramList.get(i-1);
				Vector v_z = FMath.axpy(-1.0, param.g, u0[i-1]);
				error = v_z.norm2();
				br.format("Error norm2(u-g)=%f \n", error);
				Vector delta_v = x.getBlock(i);
				//stepLength*||delta_v|| < ||v_z||
				double tmp = error/delta_v.norm2();
				if(stepLength>tmp)
					stepLength = tmp;
			}
			System.out.println("=================================>stepLength="+stepLength);
			br.println("stepLength="+stepLength);
			
			//这一步更新u0,lambda0实际没有使用，后面是根据更新的a0计算新的u0,lambda0
			for(int i=1;i<=nDataBlock;i++) {
				u0[i-1].add(stepLength, x.getBlock(i));
				//注意：-stepLength应该是正的
				lambda0[i-1].add(stepLength, x.getBlock(nDataBlock+i));
			}
			a0.add(stepLength, x.getBlock(nDataBlock*2+1));
			
			for(int i=1;i<=nDataBlock;i++) {
				plotVector(mesh, u0[i-1], String.format("M%02d_rlt_v%02d.dat",i-1,iter));
				plotVector(mesh, lambda0[i-1], String.format("M%02d_rlt_lambda%02d.dat",i-1,iter));
			}
			plotVector(mesh, a0, String.format("rlt_a%02d_refine%02d.dat",iter,refineNum));
			
			//计算新a0对应的v，再计算lmd，用来验证
			for(int i=0;i<nDataBlock;i++) {
				//!!!
				this.reinitModelLight(i);
				
				ParamOfLightSource param =  paramList.get(i);
				//Note for boundary:
				Vector vsol = solveStateEquation(a0,null);
				plotVector(mesh, vsol, String.format("M%02d_rlt_v_solve%02d.dat",i,iterNum));
				plotVector(mesh, FMath.axpy(-1.0, vsol, u0[i]), String.format("M%02d_rlt_v_diff%02d.dat",i,iterNum));
				//直接计算u0+du
				u0[i] = vsol;
				
				Vector lamsol = this.solveAdjointEquation(param.s_i, a0, vsol, param.g);
				plotVector(mesh, lamsol, String.format("M%02d_rlt_lambda_solve%02d.dat",i,iterNum));
				plotVector(mesh, FMath.axpy(-1.0, lamsol, lambda0[i]), String.format("M%02d_rlt_lambda_diff%02d.dat",i,iterNum));
				//直接计算lambda0+dl
				lambda0[i] = lamsol;
			}
			
			br.flush();
			if(error < 10.0) break;
		}
		return a0;

		
	}
	
	public static void main(String[] args) {
		VariationGaussNewtonDOTPlusBoundary vgn = new VariationGaussNewtonDOTPlusBoundary();
		vgn.debug = true;
//		vgn.readMeshTriangle();
		vgn.readMeshRectangle();
		
		//vgn.testRefine();
		//vgn.testRefine2();
		
		plotFunction(vgn.meshBig, vgn.modelReal.mu_a.M(3.0), String.format("aReal.dat"));
		vgn.beginLog();
		
		String tmpOutputFolder = outputFolder;
		for(int k=0;k<1;k++) {
			vgn.refineNum = k;
			
			List<ParamOfLightSource> paramList = 
							new ArrayList<ParamOfLightSource>();
			NodeList nodes = vgn.mesh.getNodeList();
			
			for(int i=0; i<vgn.LS.length; i++) {
				//初始化模型“光源位置” 和 “包含物位置”
				vgn.reinitModelLight(i);
				
				ParamOfLightSource para = new ParamOfLightSource();
				para.s_i = i;
				para.g = vgn.solveRealU(i);
				//只保留边界上的值，区域内部的值置为零
				for(int j=1;j<=nodes.size();j++) {
					if(nodes.at(j).isInnerNode())
						para.g.set(j,0.0);
				}
				plotVector(vgn.mesh, para.g, String.format("M%02d_g.dat",i));
				
				paramList.add(para);
			}
			
			//a(x)参考值（GCM方法得到的结果）
			vgn.aGlob = new SparseVector(nodes.size());
			for(int j=1;j<=nodes.size();j++) {
				double mu_a = vgn.modelGuess.mu_a.value(
								Variable.createFrom(vgn.modelGuess.mu_a, nodes.at(j), j));
				vgn.aGlob.set(j, 3.0*mu_a);
			}
			plotVector(vgn.mesh, vgn.aGlob, "aGlob.dat");
			
			Vector ak = vgn.gaussNewtonIterateMulti(paramList);
			//ak = Utils.gaussSmooth(vgn.mesh, ak, 1, 0.5);
			//plotVector(vgn.mesh, ak, String.format("rlt_a_smooth_refine%02d.dat",k));
			
			vgn.refine(ak);
			outputFolder = String.format(tmpOutputFolder+"%02d", k);
		}
		vgn.endLog();
	}
}
