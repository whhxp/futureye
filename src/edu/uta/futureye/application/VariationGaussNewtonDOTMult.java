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
import java.util.Map;

import edu.uta.futureye.algebra.SchurComplementLagrangianSolver;
import edu.uta.futureye.algebra.Solver;
import edu.uta.futureye.algebra.SolverJBLAS;
import edu.uta.futureye.algebra.SparseBlockMatrix;
import edu.uta.futureye.algebra.SparseBlockVector;
import edu.uta.futureye.algebra.SparseMatrix;
import edu.uta.futureye.algebra.SparseVector;
import edu.uta.futureye.algebra.intf.BlockMatrix;
import edu.uta.futureye.algebra.intf.BlockVector;
import edu.uta.futureye.algebra.intf.Matrix;
import edu.uta.futureye.algebra.intf.Vector;
import edu.uta.futureye.core.DOF;
import edu.uta.futureye.core.DOFOrder;
import edu.uta.futureye.core.Element;
import edu.uta.futureye.core.Mesh;
import edu.uta.futureye.core.Node;
import edu.uta.futureye.core.NodeRefined;
import edu.uta.futureye.core.NodeType;
import edu.uta.futureye.core.Refiner;
import edu.uta.futureye.core.geometry.GeoEntity;
import edu.uta.futureye.function.AbstractFunction;
import edu.uta.futureye.function.Variable;
import edu.uta.futureye.function.basic.DuDn;
import edu.uta.futureye.function.basic.DuDx;
import edu.uta.futureye.function.basic.FC;
import edu.uta.futureye.function.basic.Vector2Function;
import edu.uta.futureye.function.intf.Function;
import edu.uta.futureye.function.intf.ScalarShapeFunction;
import edu.uta.futureye.function.intf.VectorFunction;
import edu.uta.futureye.function.operator.FMath;
import edu.uta.futureye.io.MeshReader;
import edu.uta.futureye.io.MeshWriter;
import edu.uta.futureye.lib.assembler.AssemblerScalar;
import edu.uta.futureye.lib.element.FEBilinearRectangle;
import edu.uta.futureye.lib.element.FEBilinearRectangleRegular;
import edu.uta.futureye.lib.element.FELinearTriangle;
import edu.uta.futureye.lib.shapefun.SFBilinearLocal2D;
import edu.uta.futureye.lib.shapefun.SFLinearLocal2D;
import edu.uta.futureye.lib.weakform.WeakFormL22D;
import edu.uta.futureye.lib.weakform.WeakFormLaplace2D;
import edu.uta.futureye.util.FutureyeException;
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
 * -\nabla{(1/(a*k))*\nabla{u}} + u = \delta/a
 * 
 * where 
 *   k = 3*mu_s'
 *   a = a(x) = mu_a(x)
 * 
 * Measurements take place on the whole domain of \Omega
 * 
 * @author liuyueming
 *
 */
public class VariationGaussNewtonDOTMult {
	public Mesh mesh;
	public Mesh meshBig;
	
	
	protected static String outputFolder = "Lagrangian_SuLiu_Mult_2LS";
	public boolean debug = false;

	//Back ground model
	ModelDOTMult modelBk = new ModelDOTMult();
	
	//Real inclusion model
	ModelDOTMult modelReal = new ModelDOTMult();

	//Real inclusion model
	ModelDOTMult modelGuess = new ModelDOTMult();
	ModelDOTMult modelInit = new ModelDOTMult();

	//k = 3*mu_s'
	Function model_k = FC.c(50.0);
	//Background of a(x) = mu_a(x) = 0.1
	double aBackground = 0.1;
	
    Function diri = null;
    
    //对应每个光源s_i的参数，包括测量数据
    public static class ParamOfLightSource {
    	int s_i;
    	Vector g; //= u|_\Gamma
    }
	//Test: u_g在整个区域上都已知
	boolean bTestWholdDomain = false;
	boolean bTestWholeDomainDirichletBoundary = false;
	boolean bTestBoundaryAsWholdDomain = false;
    
    //mu_a
    Vector aGlob = null;//GCM方法得到的a_glob(x)
    
    //正则化参数
    //double beta = 1.0; //bTestWholdDomain = true;
    double beta = 0.1; //bTestWholdDomain = true;
    //整个区域
    //double beta = 100000; //bTestWholdDomain = false;
    
    int iterNum = 0;
    int refineNum = 0;
    
    //光源x坐标位置数组
	double[] LSx;
	double[] LSy;

	FileOutputStream out = null;
	PrintWriter br = null;
	
	double errorNorm = 0.0;
	double stepLength = 3.0;

	
    /**
     * 构造函数，设定包含物位置
     */
    public VariationGaussNewtonDOTMult() {
		//Number of moving light sources
		double[] xx = {4.8};
		double[] yy = {2.0};
//		double[] xx = {0.2};
//		double[] yy = {2.0};
//		double[] xx = {0.2,4.8};
//		double[] yy = {2.0,2.0};
//		double[] xx = {2.5,0.5,4.5};
//		double[] yy = {3.8,2.0,2.0};
		
		LSx = xx;
		LSy = yy;
		
//		int N=2; 
//		double h = 0.2;
//		LSx[0] = 2.5;
//		for(int i=1; i<N; i++)
//			LSx[i] = LSx[0] + i*h;
		
		
		//背景mu_a
		modelBk.setMu_a(ModelDOTMult.getMu_a(0.0, 0.0, 0.0, 
				0.1, //mu_a=0.1 mu_s(=model.k)=0.02 => a(x)=5
				1));
//test9		
//		//有包含物mu_a，真实模型
//		modelReal.setMu_a(2.40, 2.60, 0.6,
//				0.4, //peak value of mu_a
//				1); //Number of inclusions
//		//有包含物mu_a，猜测模型
//		modelGuess.setMu_a(2.40, 2.60, 0.6,
//				0.2, //peak value of mu_a
//				1); //Number of inclusions
//		//有包含物mu_a，迭代初始值
//		modelInit.setMu_a(2.40, 2.60, 0.6,
//				0.2, //peak value of mu_a
//				1); //Number of inclusions

//test9_1
		//有包含物mu_a，真实模型
		modelReal.setMu_a(ModelDOTMult.getMu_a(2.20, 2.60, 0.6,
				0.4, //peak value of mu_a
				1)); //Number of inclusions
		//有包含物mu_a，猜测模型
		modelGuess.setMu_a(ModelDOTMult.getMu_a(2.40, 2.60, 0.6,
				0.4, //peak value of mu_a
				1)); //Number of inclusions
		//有包含物mu_a，迭代初始值
		modelInit.setMu_a(ModelDOTMult.getMu_a(2.40, 2.60, 0.6,//0.8
				0.4, //peak value of mu_a
				1)); //Number of inclusions

//test9_2
//		//有包含物mu_a，真实模型
//		modelReal.setMu_a(2.40, 2.40, 0.6,
//				0.4, //peak value of mu_a
//				1); //Number of inclusions
//		//有包含物mu_a，猜测模型
//		modelGuess.setMu_a(2.40, 2.60, 0.6,
//				0.4, //peak value of mu_a
//				1); //Number of inclusions		

//test9_3
//		//有包含物mu_a，真实模型
//		modelReal.setMu_a(2.40, 2.60, 0.6,
//				0.4, //peak value of mu_a
//				1); //Number of inclusions
//		//有包含物mu_a，猜测模型
//		modelGuess.setMu_a(2.40, 2.40, 0.6,
//				0.4, //peak value of mu_a
//				1); //Number of inclusions				
    }
    
    /**
     * 
     * @param s_i：Light source No. (0,1,2...)
     */
    public void reinitModelLight(int s_i) {
		modelBk.setLightPosition(LSx[s_i], LSy[s_i]);
		modelReal.setLightPosition(LSx[s_i], LSy[s_i]);
		modelGuess.setLightPosition(LSx[s_i], LSy[s_i]);
		modelInit.setLightPosition(LSx[s_i], LSy[s_i]);
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
			File file = new File(".\\"+outputFolder+"\\output_log.txt");
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
//		String gridFileBig = "prostate_test7_ex.grd";//粗
//		String gridFileSmall = "prostate_test7.grd";
//		String gridFileBig = "prostate_test8_ex.grd";//细
//		String gridFileSmall = "prostate_test8.grd";
//		String gridFileBig = "prostate_test9_ex.grd";//细2
//		String gridFileSmall = "prostate_test9.grd";
//		String gridFileBig = "prostate_test10_ex.grd";//细
//		String gridFileSmall = "prostate_test10.grd";
//		String gridFileBig = "prostate_test11_ex.grd";//粗
//		String gridFileSmall = "prostate_test11.grd";
		String gridFileBig = "prostate_test12_ex.grd"; //细2
		String gridFileSmall = "prostate_test12.grd";

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
//        FEBilinearRectangle feLT = new FEBilinearRectangle();
        FEBilinearRectangleRegular feLT = new FEBilinearRectangleRegular();
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
		
		Tools.assignLinearShapFunction(meshBig);
		Tools.assignLinearShapFunction(mesh);
	}
	
	public void constrainHangingNodes(Mesh mesh, Vector v) {
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
	 *   ((1/(a*k))*\nabla{\psi},\nabla{\lambda}) + (\psi,\lambda) = -(u-g,\psi)_\Omega
	 * 
	 * Boundary Condition: Dirichlet
	 *   \lambda = 0 on \Omega
	 * 
	 * where 
	 *    \Gamma = \partial\Omega
	 *    g = uReal|_\Gamma
	 * 
	 * Note:
	 *    bTestWholdDomain == true
	 *      bTestWholeDomainDirichletBoundary == true:  测量整个区域，Dirichlet边界条件
	 *      bTestWholeDomainDirichletBoundary == false: 测量跟个区域，Neumann边界条件
	 *    bTestWholdDomain == false: 测量边界区域，Neumann边界条件
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
		if(!bTestWholdDomain) {
			for(int j=1;j<=nodes.size();j++) {
				if(nodes.at(j).isInnerNode())
					u_g.set(j,0.0);
			}
		}
		
		Function fu_g = new Vector2Function(u_g);
		plotFunction(mesh, fu_g, String.format("M%02d_Lagrangian_u_g%02d.dat",s_i,this.iterNum));
		Function fa = new Vector2Function(a);

		
		if(bTestWholdDomain)
			weakForm.setF(fu_g.M(-1.0));//!!!!!!1.0=>-1.0???   //???.A(this.modelReal.delta)
		else
			weakForm.setF(FC.c(0.0));

		
		//d*u + k*u_n = q
		//采用自然边界：u_n + u = 0
		if(bTestWholdDomain) {
			weakForm.setParam(
					FC.c1.D(fa.M(model_k)),
					FC.c1,
					null,//q=0
					//FC.c0
					//***
					FC.c1.D(fa.M(model_k))//d=k
				);
		} else {
			//d*u + k*u_n = q
			//自然边界(u_n+u=0)+边界测量(-(u-g,\psi)_\Gamma)
			//
			weakForm.setParam(
					FC.c1.D(fa.M(model_k)),
					FC.c1,
					fu_g.M(-1.0), //q=-(u-g)
					FC.c1.D(fa.M(model_k)) //d=k
				);
		}
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(bTestWholdDomain && bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
		AssemblerScalar assembler = new AssemblerScalar(mesh, weakForm);
		System.out.println("Begin Assemble...lambda");
		assembler.assemble();
		Matrix stiff = assembler.getStiffnessMatrix();
		Vector load = assembler.getLoadVector();
		if(bTestWholdDomain && bTestWholeDomainDirichletBoundary)
			assembler.imposeDirichletCondition(FC.c0);
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
	 *   ((1/(a*k))*\nabla{u},\nabla{\phi}) + (u,\phi) = 0
	 *  
	 * Boundary Condition: Robin
	 *   (1/(a*k))*\partial_{n}{u}  + (1/(a*k))*u = 0, on \Gamma
	 *   =>（实际计算的时候不能，参数中不能消去1/(a*k)）
	 *   (1/(a*k))*(\partial_{n}{u}  + u) = 0, on \Gamma
	 * 
	 * @param a
	 * @param g: 可选项
	 *     g==null, 以自然边界条件在大区域上计算u，然后截取到小区域上
	 *     g!=null, 以g为Dirichlet边界条件在小区域上计算u
	 * @return
	 */
	public Equation getEqnU(Mesh _mesh, Vector a, Vector g, Vector u0_x, Vector u0_y) {
		
		WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
		Function fa = new Vector2Function(a);
		
		//不能忽略光源的影响???
		//if(g == null)
			weakForm.setF(this.modelReal.getDelta());
		//else
		//	weakForm.setF(FC.c(0.0));
		
		DuDn du0dn = new DuDn(new Vector2Function(u0_x),new Vector2Function(u0_y),null);
		
		weakForm.setParam(
				FC.c1.D(fa.M(model_k)),
				FC.c1,
				null,
				FC.c1.D(fa.M(model_k)));
		
//		weakForm.setParam(
//				FC.c1.D(fa.M(model_k)),
//				FC.c1,
//				FC.c1.D(fa.M(model_k)).M(du0dn),
//				FC.c0);
		
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
	
	public Equation getEqnU(Vector a, Vector g, Vector u0_x, Vector u0_y) {
		if(g == null) {
			//Robin条件，由于光源在区域外面，在小区域上直接求解会得到0解，因此
			//先在大区域上求解，然后截取解到小区域，作为Dirichlet边界在小区域上求解
			Vector aBig = Tools.extendData(mesh, meshBig, a, this.aBackground);
	        plotVector(meshBig,aBig,String.format("aBig%02d.dat",this.iterNum));
			
			Equation eq = getEqnU(meshBig,aBig,null, null, null);
			
	        //Solver sol = new Solver();
	        //Vector uBig = sol.solveCGS(eq.A, eq.f);
	        
	        SolverJBLAS sol = new SolverJBLAS();
			Vector uBig = sol.solveDGESV(eq.A, eq.f);
			
	        plotVector(meshBig,uBig,String.format("uBig%02d.dat",this.iterNum));
	        Vector uSmall = Tools.extractData(meshBig, mesh, uBig);
	        return getEqnU(mesh,a,uSmall, null, null);
	        //return getEqnU(mesh,a,null, u0_x, u0_y);
		}
		else {
			//在小区域求解Dirichlet问题（得到系数矩阵和右端向量）
			return getEqnU(mesh,a,g, null, null);
		}
	}
	
	
	/**
	 * Get stiff matrix and load vector for equation of 'a(x)'
	 * 
	 * L_a(a)=0:
	 * 
	 * \beta(a-aGlob,\chi) - 
	 * 					\sum_{i=1,N}{
	 * 						 ((ak*k)^{-2}*k*\chi\nabla{u_i},\nabla{\lambda_i})
	 * 					} = 0
	 * =>
	 * (a,\chi) = (aGlob,\chi) + (1/\beta)*
	 *                  \sum_{i=1,N}{
	 * 						 ((ak*k)^{-2}*k*\chi*\nabla{u_i},\nabla{\lambda_i})
	 * 					}
	 */
	public Equation getEqnA(Vector[] u, Vector[] u_x,Vector[] u_y, Vector[] lambda, 
			Vector ak, Vector _aGlob, 
			Function diri) {
//        WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
//        weakForm.setParam(FC.c0, FC.c1, null, null);
//        //stabilize
//        //weakForm.setParam(FC.c(0.001), FC.c1, null, null);
//        
//        Function faGlob = new Vector2Function(_aGlob);
//        int N=u.length;
//        Vector[] uDotlmd = new Vector[N];
//        for(int i=0; i<N; i++) {
//        	Vector ux = Tools.computeDerivative(mesh, u[i], "x");
//        	Vector uy = Tools.computeDerivative(mesh, u[i], "y");
//        	Vector lx = Tools.computeDerivative(mesh, lambda[i], "x");
//        	Vector ly = Tools.computeDerivative(mesh, lambda[i], "y");
//        	plotVector(mesh, ux, String.format("M%02d_La_RHS_ux%02d.dat",i,this.iterNum));
//        	plotVector(mesh, uy, String.format("M%02d_La_RHS_uy%02d.dat",i,this.iterNum));
//        	plotVector(mesh, lx, String.format("M%02d_La_RHS_lx%02d.dat",i,this.iterNum));
//        	plotVector(mesh, ly, String.format("M%02d_La_RHS_ly%02d.dat",i,this.iterNum));
//        	uDotlmd[i] = new SparseVector(ux.getDim());
//        	uDotlmd[i].add(FMath.axMuly(1.0, ux, lx));
//        	uDotlmd[i].add(FMath.axMuly(1.0, uy, ly));
//        	plotVector(mesh,uDotlmd[i],
//        			String.format("M%02d_La_RHS_v_lmd_Grad%02d.dat",i,this.iterNum));
//        	//this.connectSells(mesh, uDotlmd[i]);
//        	//plotVector(mesh,uDotlmd[i],
//        	//		String.format("M%02d_La_RHS_v_lmd_Grad_ConnectSells%02d.dat",i,this.iterNum));
//        }
//		Vector sum2 = FMath.sum(uDotlmd);
//		//this.connectSells(mesh, sum2);
//		plotVector(mesh,sum2,String.format("La_RHS_v_lmd_sum2_%02d.dat",this.iterNum));
//
//		Function akmk_2mk = FMath.pow(new Vector2Function(ak).M(model_k),-2.0).M(model_k);
//		plotFunction(mesh,akmk_2mk,String.format("La_RHS_akmk_2_%02d.dat",this.iterNum));
//		
//		Function rhs = akmk_2mk.M(new Vector2Function(sum2)).M(1.0/beta);
//		plotFunction(mesh,rhs,String.format("La_RHS_rhs%02d.dat",this.iterNum));
//		Function f2 = faGlob.A(rhs);
//		plotFunction(mesh,f2,String.format("La_RHS_all%02d.dat",this.iterNum));
//		weakForm.setF(f2);
		
		Function faGlob = new Vector2Function(_aGlob);
		Function akmk_2mk = FMath.pow(new Vector2Function(ak).M(model_k),-2.0)
							.M(model_k).M(1.0/beta);
		Function ak_2 = FMath.pow(new Vector2Function(ak),-2.0).M(1.0/beta);
		int NF = u.length;
		Function[] fu = new Function[NF];
		Function[] fl = new Function[NF];
		Function[] fumfl = new Function[NF];
		DuDn[] du0dn = new DuDn[NF];
		Function[] du0dnmfl = new Function[NF];
		for(int k=0;k<NF;k++) {
			fu[k] = new Vector2Function(u[k]);
			fl[k] = new Vector2Function(lambda[k]);
			fumfl[k] = fu[k].M(fl[k]);
        	du0dn[k] = new DuDn(
        		new Vector2Function(u_x[k]),
        		new Vector2Function(u_y[k]),
        		null
        		);
        	du0dnmfl[k] = du0dn[k].M(fl[k]);
        }
		
        WeakFormLa weakForm = new WeakFormLa();
        
        //***
        //weakForm.setRobin(akmk_2mk.M(FMath.sum(fumfl)), FC.c0);
        //plotFunction(mesh, akmk_2mk.M(FMath.sum(fumfl)), "La_robin.dat");
        weakForm.setRobin(FC.c0, FC.c0);
        //weakForm.setRobin(akmk_2mk.M(FMath.sum(du0dnmfl)).M(-1.0), FC.c0);
        
        weakForm.setF(faGlob,//.A(ak_2.M(FMath.sum(fl)).M(modelReal.delta)) 
        		akmk_2mk, FC.c0,
        		fu, fl
        	);
		

        //需要重新标记边界条件
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(this.bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...a(x)");
        assembler.assemble();
        Matrix stiff = assembler.getStiffnessMatrix();
        Vector load = assembler.getLoadVector();
        
        //Boundary condition
        if(this.bTestWholeDomainDirichletBoundary)
        	assembler.imposeDirichletCondition(diri);
        
        System.out.println("Assemble done!");

		Equation eqn = new Equation();
		eqn.A = stiff;
		eqn.f = load;
		
		return eqn;
	}
	
//	public Equation getEqnA(Vector u, Vector lambda, 
//			Vector ak, Vector _aGlob, 
//			Function diri) {
//		Vector[] vu = new Vector[1];
//		Vector[] vlmd = new Vector[1];
//		vu[0] = u;
//		vlmd[0] = lambda;
//		return getEqnA(vu, vlmd, ak, _aGlob, diri);
//	}
	
	
	/**
	 * Residual of state equation L_{\lambda}(u)
	 * 
	 * 获取状态方程的余量
	 * @return
	 */
	public Vector getResLlmd(Vector a, Vector u, Vector g, Vector u0_x, Vector u0_y) {
		Equation eq = this.getEqnU(a, g, u0_x, u0_y);
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
//	public Vector getResLq(Vector u, Vector lambda,
//			Vector ak, Vector _aGlob, Function diri) {
//		Equation eq = this.getEqnA(u, lambda, ak, _aGlob, diri);
//        Vector res = new SparseVector(eq.f.getDim());
//        eq.A.mult(ak, res);
//        res.add(-1.0, eq.f);
//        //connectSells(mesh, res);
//        //zeroHangingNode(mesh,res);
//        plotVector(mesh,res,String.format("Res_La%02d.dat",this.iterNum));
//        
//        //光滑余量
//        //res = Utils.gaussSmooth(mesh, res, 2, 0.5);
//        //plotVector(mesh,res,String.format("Res_LaSmooth%02d.dat",this.iterNum));
//        
//        //Solver sol = new Solver();
//        //Vector a_solve = sol.solveCGS(eq.A, eq.f);
//        SolverJBLAS sol = new SolverJBLAS();
//		Vector a_solve = sol.solveDGESV(eq.A, eq.f);
//		
//        plotVector(mesh,a_solve,String.format("a_solve%02d.dat",this.iterNum));
//        return res;
//	}
	
	public Vector getResLq(Vector[] u,Vector[] u_x,Vector[] u_y, Vector[] lambda,
			Vector ak, Vector _aGlob, Function diri) {
		Equation eq = this.getEqnA(u, u_x, u_y, lambda, ak, _aGlob, diri);
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
        //Solver sol = new Solver();
        //Vector a_solve = sol.solveCGS(eq.A, eq.f);
        SolverJBLAS sol = new SolverJBLAS();
		Vector a_solve = sol.solveDGESV(eq.A, eq.f);
		
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
        
        if(this.bTestWholdDomain) {
            //weakForm.setParam(FC.c0, FC.c1, null, null);
            //stabilize 使结果更光滑一些，不会导致结果有大的变化
            weakForm.setParam(
            		FC.c(0.0), //---同时改
            		FC.c(1.0), 
            		null,
            		FC.c(0.0)); //---同时改
        }
        else {
        	weakForm.setParam(
        		FC.c(0.0), //null==(k=1)
        		FC.c(0.0), 
        		null, //q=0
        		FC.c1 //d=1
        		);
        }

        //Right hand side(RHS): f(x) = 0
        weakForm.setF(FC.c0);

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		
        if(this.bTestWholdDomain) 
        	mapNTF.put(NodeType.Dirichlet, null);
		else
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
        
        //正则化 \eps*I + M
//        System.out.println("M");
//        for(int i=1;i<=stiff.getColDim();i++) {
//        	System.out.println(stiff.get(i, i));
//        	stiff.set(i, i, stiff.get(i, i)*1.9);
//        }
        return stiff;
    }
	
	/**
	 * Weak form of 'A'
	 * 
	 * ((1/(a*k)*\nabla{du},\nabla{\psi}) + (du,\phi)
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
			//weakForm.setF(this.modelReal.delta);
		else
			weakForm.setF(f);
		
		weakForm.setParam(
				FC.c1.D(fa.M(model_k)),
				FC.c1,
				null,
				//***
				FC.c1.D(fa.M(model_k))
				//FC.c0
				);


        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		
		if(this.bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);//bugfix add
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
		
		//stiff.print();
//        //
//		System.out.println("A");
//        for(int i=1;i<=stiff.getColDim();i++) {
//        	System.out.println(stiff.get(i, i));
//        }
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
		return getA(ak,true); //Note for adaptive mesh hanging nodes
		//return getA(ak,true).trans();
	}
	
	/**
	 * Weak form of 'C'
	 * 
	 * ((-(a*k)^{-2}*k*da*\nabla{u},\nabla{\psi})
	 * 
	 * @param ak
	 * @param uk
	 * @return
	 */
	public Matrix getC(Vector ak, Vector uk, Vector uk_x, Vector uk_y) {
//        WeakFormGCMDual weakForm = new WeakFormGCMDual();
//        Function fa = new Vector2Function(ak);
//        Function b1 = new Vector2Function(Tools.computeDerivative(mesh, uk, "x"));
//        Function b2 = new Vector2Function(Tools.computeDerivative(mesh, uk, "y"));
//        Function _amk_2mk = FMath.pow(fa.M(model_k),-2).M(model_k).M(-1.0);
//        
//        weakForm.setParam(FC.c0, FC.c0, 
//        		b1.M(_amk_2mk), 
//        		b2.M(_amk_2mk));
        
        WeakFormC weakForm = new WeakFormC();
        Function fa = new Vector2Function(ak);
        Function fu = new Vector2Function(uk);
        Function _amk_2mk = FMath.pow(fa.M(model_k),-2).M(model_k).M(-1.0);
        
        weakForm.setParam(_amk_2mk, FC.c0, fu);
        //weakForm.setParam(_amk_2mk, fu, fu);
        
        //***
        //Robin:  d*u + k*u_n= q (自然边界：d==k, q=0)
        //weakForm.setRobin(FC.c0, _amk_2mk.M(fu));
        DuDn du0dn = new DuDn(
        		new Vector2Function(uk_x),
        		new Vector2Function(uk_y),
        		null);
        //weakForm.setRobin(FC.c0, _amk_2mk.M(du0dn).M(-1.0));
        weakForm.setRobin(FC.c0, FC.c0);
        
        //stabilize
        //weakForm.setParam(FC.c(0.0001), FC.c0, b1.M(_apk_2), b2.M(_apk_2));
        weakForm.setF(FC.c0);

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(this.bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
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
        
        //stiff.print();
        
//        System.out.println("C");
//        for(int i=1;i<=stiff.getColDim();i++) {
//        	System.out.println(stiff.get(i, i));
//        }
        return stiff;
	}
	
	/**
	 * Weak form of 'CT'
	 * 
	 * ((-(a*k)^{-2}*k*\chi*\nabla{u},\nabla{dl})
	 * 
	 * where
	 * 
	 *   dl=\delta{\lambda}
	 * 
	 * @param ak
	 * @param uk
	 * @return
	 */
	public Matrix getCT(Vector ak, Vector uk, Vector uk_x, Vector uk_y) {
//        WeakFormGCM weakForm = new WeakFormGCM();
//        Function fa = new Vector2Function(ak);
//        Function b1 = new Vector2Function(Tools.computeDerivative(mesh, uk, "x"));
//        Function b2 = new Vector2Function(Tools.computeDerivative(mesh, uk, "y"));
//        Function _amk_2mk = FMath.pow(fa.M(model_k),-2).M(model_k).M(-1.0);
//        
//        weakForm.setParam(FC.c0, FC.c0, 
//        		b1.M(_amk_2mk), 
//        		b2.M(_amk_2mk));
        
        WeakFormCT weakForm = new WeakFormCT();
        Function fa = new Vector2Function(ak);
        Function fu = new Vector2Function(uk);
        Function _amk_2mk = FMath.pow(fa.M(model_k),-2).M(model_k).M(-1.0);
        
        weakForm.setParam(_amk_2mk, FC.c0, fu);
        //weakForm.setParam(_amk_2mk, fu, fu);
        
        //***
        //Robin:  d*u + k*u_n= q (自然边界：d==k, q=0)
        //weakForm.setRobin(FC.c0, _amk_2mk.M(fu));
        DuDn du0dn = new DuDn(
        		new Vector2Function(uk_x),
        		new Vector2Function(uk_y),
        		null);
        //2011/1/18 这两个条件结果差不多（bugfix:是因为没有标记边界条件：忘记调用mesh.markBorderNode(mapNTF);）
        //weakForm.setRobin(FC.c0, _amk_2mk.M(du0dn).M(-1.0));
        weakForm.setRobin(FC.c0, FC.c0);
        
        //stabilize
        //weakForm.setParam(FC.c(0.0001), FC.c0, b1.M(_apk_2), b2.M(_apk_2));
        weakForm.setF(FC.c0);

        //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
        //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(this.bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...CT");
        assembler.assemble(false);
        Matrix stiff = assembler.getStiffnessMatrix();
        //Boundary condition
        //边界条件需要在大矩阵中设置
        //assembler.imposeDirichletCondition(FC.c0);
        System.out.println("Assemble done!");
        
        //stiff.print();
        
        return stiff;		
	}
	
	public Matrix testGetCT(Vector ak, Vector uk, Vector uk_x, Vector uk_y) {
      WeakFormCT weakForm = new WeakFormCT();
      Function fa = new Vector2Function(ak);
      Function fu = new Vector2Function(uk);
      Function _amk_2mk = FMath.pow(fa.M(model_k),-2).M(model_k).M(-1.0);
      
      weakForm.setParam(_amk_2mk, fu, fu);
      //***
      //Robin:  d*u + k*u_n= q (自然边界：d==k, q=0)
      //weakForm.setRobin(FC.c0, _amk_2mk.M(fu));
      DuDn du0dn = new DuDn(
      		new Vector2Function(uk_x),
      		new Vector2Function(uk_y),
      		null);
      //2011/1/18 这两个条件结果差不多（bugfix:是因为没有标记边界条件：忘记调用mesh.markBorderNode(mapNTF);）
      //weakForm.setRobin(FC.c0, _amk_2mk.M(du0dn).M(-1.0));
      weakForm.setRobin(FC.c0, FC.c0);

      //stabilize
      //weakForm.setParam(FC.c(0.0001), FC.c0, b1.M(_apk_2), b2.M(_apk_2));
	  weakForm.setF(FC.c0);

      
      //需要重新标记边界条件，否则在“整体合成”过程会出现错误。
      //虽然边界条件实在大矩阵中设置，这一步也是需要的。
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(this.bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
      //5.Assembly process
      AssemblerScalar assembler =
              new AssemblerScalar(mesh, weakForm);
      System.out.println("Begin Assemble...CT");
      assembler.assemble(false);
      Matrix stiff = assembler.getStiffnessMatrix();
      //Boundary condition
      //边界条件需要在大矩阵中设置
      //assembler.imposeDirichletCondition(FC.c0);
      System.out.println("Assemble done!");
      
      //stiff.print();
      
      return stiff;		
	}	
	public Equation testGetCTLoad(Vector[] u, Vector[] u_x,Vector[] u_y, Vector[] lambda, 
			Vector ak, Function diri) {
		
		Function akmk_2mk = FMath.pow(new Vector2Function(ak).M(model_k),-2.0).M(model_k).M(-1.0/beta);
		int NF = u.length;
		Function[] fu = new Function[NF];
		Function[] fl = new Function[NF];
		for(int k=0;k<NF;k++) {
			fu[k] = new Vector2Function(u[k]);
			fl[k] = new Vector2Function(lambda[k]);
        }
        WeakFormLa weakForm = new WeakFormLa();
        
        weakForm.setRobin(FC.c0, FC.c0);
        weakForm.setF(FC.c0,
        		akmk_2mk, FC.c1,
        		fu, fl
        	);
		

        //需要重新标记边界条件
		mesh.clearBorderNodeMark();
		HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
		if(this.bTestWholeDomainDirichletBoundary)
			mapNTF.put(NodeType.Dirichlet, null);
		else
			mapNTF.put(NodeType.Robin, null);
		mesh.markBorderNode(mapNTF);
		
        //5.Assembly process
        AssemblerScalar assembler =
                new AssemblerScalar(mesh, weakForm);
        System.out.println("Begin Assemble...a(x)");
        assembler.assemble();
        Matrix stiff = assembler.getStiffnessMatrix();
        Vector load = assembler.getLoadVector();
        
        //Boundary condition
        if(this.bTestWholeDomainDirichletBoundary)
        	assembler.imposeDirichletCondition(diri);
        
        System.out.println("Assemble done!");

		Equation eqn = new Equation();
		eqn.A = stiff;
		eqn.f = load;
		
		return eqn;
	}
	
	
	/**
	 * Weak form of '\beta*R'
	 * 
	 * \beta*(da,\chi)

	 * @return
	 */
	public Matrix getBR() {
        WeakFormLaplace2D weakForm = new WeakFormLaplace2D();
        weakForm.setParam(FC.c0, FC.c(beta), null, null);
        //stabilize
        //weakForm.setParam(FC.c(1000), FC.c(beta), null, null);
        
        weakForm.setF(FC.c0);

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
        
//        //
//        System.out.println("\beta R");
//        for(int i=1;i<=stiff.getColDim();i++) {
//        	System.out.println(stiff.get(i, i));
//        }
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
		Vector uRealBig = modelReal.solveNeumann(meshBig);
		plotVector(meshBig, uRealBig, String.format("M%02d_uRealBig.dat",s_i));
		
		//截取meshBig的部分解到mesh上
		Vector uReal = Tools.extractData(meshBig, mesh, uRealBig);
		plotVector(mesh, uReal, String.format("M%02d_uReal.dat",s_i));

//以下验证都成功（2011/8/4）		
//		//验证从大区域截取出来的解与边界施加Dirichlet条件解是否相同
//		Vector aRealVec = Tools.function2vector(mesh, modelReal.mu_a);
//		Vector uRealDiri = solveStateEquation(aRealVec, uReal);
//		plotVector(mesh, uRealDiri, String.format("M%02d_uRealDiri.dat",s_i));
//		plotVector(mesh, FMath.axpy(-1.0, uRealDiri,uReal), String.format("M%02d_uReal_uRealDiri_diff.dat",s_i));
//		
//		//比较解：边界相同uSmall，a(x)不同
//		Vector aGuessVec = Tools.function2vector(mesh, modelGuess.mu_a);
//		Vector uGuessDiriReal = solveStateEquation(aGuessVec, uReal);
//		Vector uGuessDiriReal2 = modelGuess.solveDirichlet(mesh, new Vector2Function(uReal));
//		plotVector(mesh, uGuessDiriReal, String.format("M%02d_uGuessDiriReal.dat",s_i));
//		plotVector(mesh, FMath.axpy(-1.0, uGuessDiriReal,uRealDiri), String.format("M%02d_uGuessDiriReal_uRealDiri_diff.dat",s_i));
//		plotVector(mesh, FMath.axpy(-1.0, uGuessDiriReal2,uRealDiri), String.format("M%02d_uGuessDiriReal2_uRealDiri_diff.dat",s_i));
//		
//		//比较解：a(x)相同aGuessVec，边界不同
//		Vector uGuessBig = modelGuess.solveNeumann(meshBig);
//		plotVector(meshBig, uGuessBig, String.format("M%02d_uGuessBig.dat",s_i));
//		Vector uGuess = Tools.extractData(meshBig, mesh, uGuessBig);
//		plotVector(mesh, uGuess, String.format("M%02d_uGuess.dat",s_i));
//		plotVector(mesh, FMath.axpy(-1.0, uGuessDiriReal,uGuess), String.format("M%02d_uGuessDiriReal_uGuess_diff.dat",s_i));
		
        return uReal;
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
		Equation eq = getA(ak,new Vector2Function(_resLlmd_da),FC.c0,true);
        //Solver sol = new Solver();
        //Vector x = sol.solveCGS(eq.A, eq.f);
        SolverJBLAS sol = new SolverJBLAS();
		Vector x = sol.solveDGESV(eq.A, eq.f);
        return x;
	}

	/**
	 * 求解关于u的状态方程
	 * 
	 * @param a 
	 * @return
	 */
	public Vector solveStateEquation(Vector a, Vector g, Vector u0_x, Vector u0_y) {
		Equation eq = this.getEqnU(a, g, u0_x, u0_y);
        //Solver solver = new Solver();
        //Vector u = solver.solveCGS(eq.A, eq.f);
        
        SolverJBLAS sol = new SolverJBLAS();
		Vector u = sol.solveDGESV(eq.A, eq.f);

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
        //Solver solver = new Solver();
        //Vector lmd_solve = solver.solveCGS(eq.A, eq.f);
        
        SolverJBLAS sol = new SolverJBLAS();
		Vector lmd_solve = sol.solveDGESV(eq.A, eq.f);
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
	
	public void imposeDirichletCondition(BlockMatrix BM, BlockVector BV,
			int nDataBlock, Function[] u, Function[] g, Function[] lambda) {
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
					//NodeType.Robin！！！
					if(n.getNodeType() == NodeType.Robin) {
						Variable v = Variable.createFrom(u[0], n, n.globalIndex);
						//循环每次测量
						for(int k=0;k<nDataBlock;k++) {
							setDirichlet(BM,BV,k*nNode+dof.getGlobalIndex(),
									(g[k].value(v)-u[k].value(v))/beta
									//0.0
									);
//							setDirichlet(BM,BV,(nDataBlock+k)*nNode+dof.getGlobalIndex(),
//									-lambda[k].value(v)
//									//0.0
//									);
						}
						//setDirichlet(BM,BV,nDataBlock*2*nNode+dof.getGlobalIndex(),0.0);
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
//	public BlockMatrix getSearchDirectionMatrix(Vector ak, Vector uk) {
//		BlockMatrix BM = new SparseBlockMatrix(3,3);
//		Matrix M = this.getM();
//		Matrix A = this.getA(ak,true);
//		Matrix AT = this.getAT(ak);
//		Matrix C = this.getC(ak,uk);
//		Matrix CT = this.getCT(ak,uk);
//		Matrix R = this.getBR();
//		
//		Matrix BM13 = new SparseMatrix(M.getRowDim(),R.getColDim());
//		Matrix BM22 = new SparseMatrix(A.getRowDim(),A.getColDim());
//		Matrix BM31 = new SparseMatrix(R.getRowDim(),M.getColDim());
//		
//		BM.setBlock(1, 1, M);
//		BM.setBlock(1, 2, AT);
//		BM.setBlock(1, 3, BM13);
//		
//		BM.setBlock(2, 1, A);
//		BM.setBlock(2, 2, BM22);
//		BM.setBlock(2, 3, C);
//		
//		BM.setBlock(3, 1, BM31);
//		BM.setBlock(3, 2, CT);
//		BM.setBlock(3, 3, R);
//		
//		return BM;
//	}
	
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
			Vector[] uk_x, Vector[] uk_y,
			List<ParamOfLightSource> paramList) {
		
		int nDataBlock = uk.length;
		BlockMatrix BM = new SparseBlockMatrix(nDataBlock*2+1,nDataBlock*2+1);
		
		Matrix R = this.getBR();
		BM.setBlock(nDataBlock*2+1, nDataBlock*2+1, R.setName("R"));
		
		for(int i=1;i<=nDataBlock;i++) {
			Matrix M = this.getM();
			Matrix A = this.getA(ak,true);
			Matrix AT = this.getAT(ak);
			//Matrix C = this.getCT(ak,uk[i-1],uk_x[i-1],uk_y[i-1]).trans();
			Matrix C = this.getC(ak,uk[i-1],uk_x[i-1],uk_y[i-1]);
			Matrix CT = this.getCT(ak,uk[i-1],uk_x[i-1],uk_y[i-1]);
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

	
	public void refine(Vector ak, double factor) {
		ElementList eToRefine = Tools.computeRefineElement(mesh, ak, factor);
		this.refineMesh(eToRefine);
	}	
	

	
	
	public Vector gaussNewtonIterateMulti(List<ParamOfLightSource> paramList, Vector a0) {
		int nDataBlock = paramList.size();
		
		//*************************Initial Values********************
		//u0: 求解状态方程得到
		Vector[] u0 = new SparseVector[nDataBlock];
		Vector[] u0_x = new SparseVector[nDataBlock];
		Vector[] u0_y = new SparseVector[nDataBlock];
		//lambda0: 求解伴随方程得到
		Vector[] lambda0 = new SparseVector[nDataBlock];
		
		for(int i=0;i<nDataBlock;i++) {
			//!!!
			this.reinitModelLight(i);
			
			ParamOfLightSource param =  paramList.get(i);
			
			//在大区域上求解状态方程，然后计算导数，最后截取到小区域上
			Vector uBig = this.modelGuess.solveNeumann(meshBig);
			plotVector(meshBig, uBig, String.format("M%02d_u0Big.dat",i));
			//param.g在small区域上  plotVector(meshBig, FMath.axpy(-1.0, uBig, param.g), String.format("M%02d_u0Big_uRealBig_diff.dat",i));
			Vector uBig_x = Tools.computeDerivativeFast(meshBig, uBig, "x");
			Vector uBig_y = Tools.computeDerivativeFast(meshBig, uBig, "y");
			u0_x[i] = Tools.extractData(meshBig, mesh, uBig_x);
			u0_y[i] = Tools.extractData(meshBig, mesh, uBig_y);
			
			//u0初值的边界条件赋予测量数据
			if(bTestWholeDomainDirichletBoundary)
				u0[i] = this.solveStateEquation(a0,param.g, null, null);
			else
				u0[i] = this.solveStateEquation(a0,null, u0_x[i], u0_y[i]);
			plotVector(mesh, u0[i], String.format("M%02d_u0.dat",i));
			plotVector(mesh, FMath.axpy(-1.0, param.g, u0[i]), String.format("M%02d_u_g.dat",i));
			
			
			lambda0[i] = this.solveAdjointEquation(param.s_i, a0, u0[i], param.g);
			plotVector(mesh, lambda0[i], String.format("M%02d_lambda0.dat",i));
		}
		//************************************************************
		//**************************TEST***************************
		Matrix stiff = this.testGetCT(a0, u0[0], u0_x[0], u0_y[0]);
		Equation load  = this.testGetCTLoad(u0,u0_x,u0_y,lambda0,a0,FC.c0);
        SolverJBLAS sol2 = new SolverJBLAS();
		Vector testLambda = sol2.solveDGESV(stiff, load.f);
		plotVector(mesh, testLambda, String.format("testLambda.dat"));
		//************************************************************
		
		//迭代求解
		int maxIter = 1;
		if(refineNum == 0)
			maxIter = 1;
		else if(refineNum == 1)
			maxIter = 1;
		else if(refineNum == 2)
			maxIter = 1;
		else if(refineNum == 3)
			maxIter = 1;
		else
			maxIter = 1;
		BlockVector f = new SparseBlockVector(nDataBlock*2+1);
		for(int iter=0;iter<maxIter;iter++) {
			
			this.iterNum = iter;
			
			//步长矩阵
			BlockMatrix BM = this.getSearchDirectionMatrix(a0, u0, u0_x, u0_y, paramList);
			
			//步长右端向量
			for(int i=1;i<=nDataBlock;i++) {
				//!!!
				this.reinitModelLight(i-1);

				ParamOfLightSource param =  paramList.get(i-1);
				//状态方程余量
				Vector resLlmd = null;
				if(bTestWholeDomainDirichletBoundary)
					resLlmd = this.getResLlmd(a0, u0[i-1],param.g, null, null).scale(-1.0);
				else
					resLlmd = this.getResLlmd(a0, u0[i-1],null, u0_x[i-1], u0_y[i-1]).scale(-1.0);
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
			f.setBlock(nDataBlock*2+1, this.getResLq( 
					u0, u0_x, u0_y, lambda0, a0, aGlob,
					FC.c(this.aBackground) //a(x)的边界条件: back ground of a(x)
					).scale(-1.0));

			
			//设置边界条件并求解
			//需要交换矩阵BM的第1, 2列，然后设置边界条件，这样使得矩阵A, A'可逆
			//BlockMatrix newBM = this.changeBlockColumn(BM, nDataBlock);
	        //if(this.bTestWholeDomainDirichletBoundary)
			//	this.imposeDirichletCondition(newBM, f, nDataBlock, FC.c0);
			//SchurComplementLagrangianSolver solver = 
			//	new SchurComplementLagrangianSolver(BM, f,mesh);
			//BlockVector x = solver.solveMulti();

			//this.imposeDirichletCondition(BM, f, FC.c0);
	        if(this.bTestWholeDomainDirichletBoundary)
	        	this.imposeDirichletCondition(BM, f, nDataBlock,FC.c0);
//	        else {
//	        	Function [] fu0 = new Function[nDataBlock];
//	        	Function [] fg = new Function[nDataBlock];
//	        	Function [] flambda0 = new Function[nDataBlock];
//				for(int i=0;i<nDataBlock;i++) {
//					ParamOfLightSource param =  paramList.get(i);
//					fu0[i] = new Vector2Function(u0[i]);
//					flambda0[i] = new Vector2Function(lambda0[i]);
//					fg[i] = new Vector2Function(param.g);
//				}
//	        	this.imposeDirichletCondition(BM, f, nDataBlock,fu0, fg, flambda0);
//	        }
	        
	        //BlockMatrix newBM = this.changeBlockColumn(BM, nDataBlock);
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
			stepLength = 1;
			for(int i=1;i<=nDataBlock;i++) {
				ParamOfLightSource param =  paramList.get(i-1);
				Vector v_z = FMath.axpy(-1.0, param.g, u0[i-1]);
				errorNorm = v_z.norm2();
				br.format("Error norm2(u-g)=%f \n", errorNorm);
				Vector delta_v = x.getBlock(i);
				//stepLength*||delta_v|| < ||v_z||
				double tmp = errorNorm/delta_v.norm2();
				if(stepLength > tmp)
					stepLength = tmp;
			}
			if(stepLength * delta_a.normInf() > a0.normInf()) {
				stepLength = (0.5/Math.pow(2, iter))*a0.normInf()/delta_a.normInf();
				//stepLength = (0.1/(iter+1))*a0.normInf()/delta_a.normInf();
				System.out.println("Iter"+iter+"++++++++++++++++++++++++++++++++=>stepLength="+stepLength);
				br.println("stepLength_m2="+stepLength);
			} else {
				System.out.println("Iter"+iter+"=================================>stepLength="+stepLength);
				br.println("stepLength_m1="+stepLength);
			}
			
			
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
			
//			//计算新a0对应的v，再计算lmd，用来验证
//			for(int i=0;i<nDataBlock;i++) {
//				//!!!
//				this.reinitModelLight(i);
//				
//				ParamOfLightSource param =  paramList.get(i);
//				Vector vsol = null;
//				if(bTestWholeDomainDirichletBoundary)
//					vsol = this.solveStateEquation(a0,param.g,null,null);
//				else
//					vsol = this.solveStateEquation(a0,null,u0_x[i],u0_y[i]);
//				plotVector(mesh, vsol, String.format("M%02d_rlt_v_solve%02d.dat",i,iterNum));
//				plotVector(mesh, FMath.axpy(-1.0, vsol, u0[i]), String.format("M%02d_rlt_v_diff%02d.dat",i,iterNum));
//				//直接计算u0+du
//				u0[i] = vsol;
//				
//				Vector lamsol = this.solveAdjointEquation(param.s_i, a0, vsol, param.g);
//				plotVector(mesh, lamsol, String.format("M%02d_rlt_lambda_solve%02d.dat",i,iterNum));
//				plotVector(mesh, FMath.axpy(-1.0, lamsol, lambda0[i]), String.format("M%02d_rlt_lambda_diff%02d.dat",i,iterNum));
//				//直接计算lambda0+dl
//				lambda0[i] = lamsol;
//				
//			}
			
			br.flush();
			if(errorNorm < 1.0) break;
			
		}
		return a0;

		
	}
	
	public static void main(String[] args) {
		String tmpOutputFolder = outputFolder;
		VariationGaussNewtonDOTMult vgn = new VariationGaussNewtonDOTMult();
		vgn.debug = true;
//		vgn.readMeshTriangle();
		vgn.readMeshRectangle();
		
		//vgn.testRefine();
		//vgn.testRefine2();
		//vgn.mesh.printMeshInfo();
		
		
		plotFunction(vgn.meshBig, vgn.modelReal.getMu_a(), String.format("aRealBig.dat"));
		plotFunction(vgn.mesh, vgn.modelReal.getMu_a(), String.format("aReal.dat"));
		//a(x)参考值（GCM方法得到的结果）
		vgn.aGlob = Tools.function2vector(vgn.mesh, vgn.modelGuess.getMu_a());
		plotVector(vgn.mesh, vgn.aGlob, "aGlob.dat");
		//Vector a0 = vgn.aGlob.copy();
		Vector a0 = Tools.function2vector(vgn.mesh, vgn.modelInit.getMu_a());
		plotVector(vgn.mesh, a0, "a0.dat");
		plotVector(vgn.mesh, FMath.axpy(-1.0, a0, 
				Tools.function2vector(vgn.mesh, vgn.modelReal.getMu_a())),"aReal_diff.dat");
		
		vgn.beginLog();
		int totalRefineNum = 1;
		double[] factors={0.15,0.15,0.15,0.15};
		for(int k=0; k<totalRefineNum; k++) {
			vgn.refineNum = k;
			
			List<ParamOfLightSource> paramList = 
							new ArrayList<ParamOfLightSource>();
			NodeList nodes = vgn.mesh.getNodeList();
			
			for(int i=0; i<vgn.LSx.length; i++) {
				//初始化模型“光源位置” 和 “包含物位置”
				vgn.reinitModelLight(i);
				
				ParamOfLightSource para = new ParamOfLightSource();
				para.s_i = i;
				para.g = vgn.solveRealU(i);
				Function gx = new DuDx(vgn.mesh,new Vector2Function(para.g,vgn.mesh,"x","y"),"x");
				Function gy = new DuDx(vgn.mesh,new Vector2Function(para.g,vgn.mesh,"x","y"),"y");
				Tools.plotFunction(vgn.mesh, VariationGaussNewtonDOTMult.outputFolder, 
						String.format("M%02d_g_grad.dat",i), gx, gy);

				if(!vgn.bTestWholdDomain) {
					HashMap<NodeType, Function> mapNTF = new HashMap<NodeType, Function>();
					mapNTF.put(NodeType.Robin, null);
					vgn.mesh.clearBorderNodeMark();
					vgn.mesh.markBorderNode(mapNTF);
					for(int j=1;j<=nodes.size();j++) {
						//System.out.println(nodes.at(j)+"   "+nodes.at(j).getNodeType());
						if(nodes.at(j).getNodeType()==NodeType.Inner)
							para.g.set(j,0.0);
					}
				}
				plotVector(vgn.mesh, para.g, String.format("M%02d_g.dat",i));
				paramList.add(para);
			}
			
			//边界测量结果作为边界条件，aGlob为系数，求解状态方程，作为“测量”解，在整个区域上计算
			//而不是只在边界上计算u-g
			if(vgn.bTestBoundaryAsWholdDomain) {
				for(int i=0; i<vgn.LSx.length; i++) {
					//初始化模型“光源位置” 和 “包含物位置”
					vgn.reinitModelLight(i);
					ParamOfLightSource para = paramList.get(i);
					
					//重新更新para.g
					Vector tmp = para.g;
					para.g = vgn.solveStateEquation(vgn.aGlob, para.g, null, null);
					plotVector(vgn.mesh, para.g, String.format("M%02d_gApproximate.dat",i));
					plotVector(vgn.mesh, FMath.axpy(-1.0, tmp, para.g), String.format("M%02d_g_gApproximate_diff.dat",i));
				}
			}
			
			
			Vector ak = vgn.gaussNewtonIterateMulti(paramList,a0);
			plotVector(vgn.mesh, ak, String.format("rlt_a_smooth_refine%02d.dat",k));
			
			//加密网格，会改变mesh和meshBig
			Mesh oldMesh = vgn.mesh.copy();
			vgn.refine(ak,factors[k]);
			//vgn.mesh.printMeshInfo();
			
			outputFolder = String.format(tmpOutputFolder+"%02d", k);
			//将结果插值到加密后的网格上
			a0 = Tools.interplateFrom(oldMesh,vgn.mesh,new Vector2Function(ak,oldMesh,"x","y"));
			plotVector(vgn.mesh, a0, "a0.dat");
			vgn.aGlob = Tools.function2vector(vgn.mesh, vgn.modelGuess.getMu_a());
			plotVector(vgn.mesh, vgn.aGlob, "aGlob.dat");
			plotFunction(vgn.meshBig, vgn.modelReal.getMu_a(), String.format("aRealBig_refine%02d.dat",k));
			plotFunction(vgn.meshBig, vgn.modelReal.getMu_a(), String.format("aReal_refine%02d.dat",k));
		}
		vgn.endLog();
		
	}
	
}
