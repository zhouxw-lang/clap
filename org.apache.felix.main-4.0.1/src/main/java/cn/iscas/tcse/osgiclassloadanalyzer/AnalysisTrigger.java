/* Clap
 * Copyright (C) 2012 Xiaowei Zhou
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
package cn.iscas.tcse.osgiclassloadanalyzer;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.felix.framework.BundleImpl;
import org.apache.felix.framework.BundleRevisionImpl;
import org.apache.felix.framework.Felix;
import org.apache.felix.framework.StatefulResolver;
import org.apache.felix.framework.cache.Content;
import org.apache.felix.framework.resolver.ResolveException;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.Type;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;
import cn.iscas.tcse.osgiclassloadanalyzer.pointstoanalysis.forsoot.OSGiBundleClassPath;
import cn.iscas.tcse.osgiclassloadanalyzer.pointstoanalysis.forsoot.OSGiEntryPoints;

public class AnalysisTrigger {
	
	private Felix m_felix;
	
	private ClassLoadingAwareTypeChecker typeChecker = new ClassLoadingAwareTypeChecker();
	
	private Set<SootClass> bundleClasses = new HashSet<SootClass>();
	
	private List<Bundle> m_bundleList;
	
	public void beginAnalysis() {
		beginResolve();
		
		int nonFragBundleCnt = 0;
		
		List<Bundle> lstNonFragBundles = new ArrayList<Bundle>();
		
		int bundles = m_bundleList.size();
		
		// initialize class loader and class path for system bundle
		lstNonFragBundles.add(m_felix);
		BundleRevision sysRev = m_felix.adapt(BundleRevision.class);
		OSGiBundleBogoClassLoader sysBundleBogoCL = new OSGiBundleBogoClassLoader(
				nonFragBundleCnt, sysRev.getWiring().getClassLoader(), m_felix);
		ClassLoaders.addClassLoader(nonFragBundleCnt, sysBundleBogoCL);
		OSGiBundleClassPath sysCP = new OSGiBundleClassPath(m_felix);
		Scene.v().addCustomCLCP(nonFragBundleCnt, sysCP);
		Bundles.addBundle(nonFragBundleCnt, m_felix);
		nonFragBundleCnt++;
		
		// initialize class loaders and class paths
		for (int i = 0; i < bundles; i++) {
			BundleImpl bundle = (BundleImpl)m_bundleList.get(i);
			BundleRevision bundleRev = bundle.adapt(BundleRevision.class);
			
			// not a fragment bundle
			if(bundleRev.getTypes() != BundleRevision.TYPE_FRAGMENT) {
				lstNonFragBundles.add(bundle);
				
				// add the bundle's class loader
				BundleWiring wiring = bundleRev.getWiring();
				OSGiBundleBogoClassLoader bundleBogoCL = new OSGiBundleBogoClassLoader(
						nonFragBundleCnt, wiring.getClassLoader(), m_felix);
				ClassLoaders.addClassLoader(nonFragBundleCnt, bundleBogoCL);
				
				// add the bundle's class path
				OSGiBundleClassPath cp = new OSGiBundleClassPath(bundle);
				Scene.v().addCustomCLCP(nonFragBundleCnt, cp);
				
				Bundles.addBundle(nonFragBundleCnt, bundle);
				
				nonFragBundleCnt++;
			}
		}
		
		AppBogoClassLoader appBogoCL = new AppBogoClassLoader(
				ClassLoaders.appCLNum, AnalysisTrigger.class.getClassLoader());
		ClassLoaders.setAppCL(appBogoCL);
		

		
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_keep_line_number(true);
		Options.v().set_whole_program(true);
//		Options.v().setPhaseOption("cg","verbose:true");
		Options.v().setPhaseOption("cg","verbose:false");
		
		Scene cm = Scene.v();
		
		// load all classes in bundles
		
		System.out.println("Loading classes ("+new Date().toString()+")...");
		
		Iterator<Entry<Integer, Bundle>> iterNumToBun = Bundles.getNumToBundle().entrySet().iterator();
		while(iterNumToBun.hasNext()) {
			Entry<Integer, Bundle> entryNumToBun = iterNumToBun.next();
			Integer bundleNum = entryNumToBun.getKey();
			
			// we do not load system bundle's classes
			if(entryNumToBun.equals(0)) {
				continue;
			}
			
			Bundle b = entryNumToBun.getValue();
			BundleRevisionImpl rev = (BundleRevisionImpl) b
					.adapt(BundleRevision.class);
			List<Content> bundleContents = rev.getContentPath();
			for (Content content : bundleContents) {
				Enumeration<String> strEnts = content.getEntries();
				while (strEnts.hasMoreElements()) {
					String entry = strEnts.nextElement();
					if (entry != null && entry.endsWith(".class")) {
						String className = entry.substring(0,
								entry.length() - 6).replace('/', '.');
						Integer defCL = ClassLoaders.initCLToDefCL(bundleNum,
								className);
						if (defCL != null && defCL.equals(bundleNum)) {
							// we do not load classes which is shaded by imports
							// or boot delegation
							SootClass sc = cm.loadClassAndSupport(className,
									bundleNum);
							bundleClasses.add(sc);
//							sc.setApplicationClass();
						}
					}
				}
			}
		}
		
		soot.Scene.v().loadNecessaryClasses();
		soot.Scene.v().setEntryPoints(OSGiEntryPoints.v().all());
		
		TypeUtil.initSameNamedTypesAndCarryClasses();
		Map<String, Set<Type>> snTypesMap = TypeUtil.getSameNamedTypes();

		try {

			OutputUtil.init();

			printSameNamedClasses(snTypesMap);

			System.out.println("Begining points-to analysis ("
					+ new Date().toString() + ")");

			// do the points-to analysis
			doPointsToAnalysis();

			System.out.println("Begining checking for runtime type errors ("
					+ new Date().toString() + ")");

			// do the type check
			typeChecker.checkForBadPointsto(bundleClasses);
			
			System.out.println("Done ("
					+ new Date().toString() + ")");

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
			OutputUtil.destroy();
		}

		// by xiaowei zhou tmp, 20120218
		System.exit(0);
	}
	
	// by xiaowei zhou, We will use demand-driven points-to analysis implemented in Spark, not Paddle, 20111224
/*	public void doPointsToAnalysis() {
		System.out.println("[paddle] Starting analysis ...");

		System.err.println("Soot version string: "+soot.Main.v().versionString);

		HashMap opt = new HashMap();
		opt.put("enabled","true");
		opt.put("verbose","true");
//		opt.put("verbosegc", "true");
		opt.put("bdd","true");
		opt.put("backend","JavaBDD");
		opt.put("context","kcfa");
		opt.put("k","1");
		//		opt.put("context-heap","true");
		opt.put("propagator","auto");
		opt.put("conf","ofcg");
		opt.put("order","32");
		opt.put("q","auto");
		opt.put("set-impl","double");
		opt.put("double-set-old","hybrid");
		opt.put("double-set-new","hybrid");
		opt.put("pre-jimplify","false");
		

		
		PaddleTransformer pt = new PaddleTransformer();
		PaddleOptions paddle_opt = new PaddleOptions(opt);
		pt.setup(paddle_opt);
		pt.solve(paddle_opt);
		soot.jimple.paddle.Results.v().makeStandardSootResults();	
		
		System.out.println("[paddle] Done!");
	}*/
	
	public void doPointsToAnalysis() {
		System.out.println("[spark] Starting analysis ...");
		
		HashMap opt = new HashMap();
		opt.put("enabled","true");
		opt.put("verbose","true");
		opt.put("ignore-types","false");
		opt.put("force-gc","false");            
		opt.put("pre-jimplify","false");          
		opt.put("vta","false");                   
		opt.put("rta","false");                   
		opt.put("field-based","false");           
		opt.put("types-for-sites","false");        
		opt.put("merge-stringbuffer","true");   
		opt.put("string-constants","false");     
		opt.put("simulate-natives","true");      
		opt.put("simple-edges-bidirectional","false");
		// by xiaowei zhou tmp, 20120310
//		opt.put("on-fly-cg","true");
		opt.put("on-fly-cg","false");
		opt.put("simplify-offline","false");    
		opt.put("simplify-sccs","false");        
		opt.put("ignore-types-for-sccs","false");
		opt.put("propagator","worklist");
		opt.put("set-impl","double");
		opt.put("double-set-old","hybrid");         
		opt.put("double-set-new","hybrid");
		opt.put("dump-html","false");           
		opt.put("dump-pag","false");             
		opt.put("dump-solution","false");        
		opt.put("topo-sort","false");           
		opt.put("dump-types","true");             
		opt.put("class-method-var","true");     
		opt.put("dump-answer","false");          
		opt.put("add-tags","false");             
		opt.put("set-mass","false"); 		
		
		opt.put("cs-demand", "true");
		opt.put("traversal", "75000");
		opt.put("passes", "10");
		opt.put("lazy-pts", "false");
		
		opt.put("line_number_in_pag", "true");
				
		SparkTransformer.v().transform("",opt);
		
		System.out.println("[spark] Done!");
	}
	
	public void beginResolve() {
		
		int bundles = m_bundleList.size();
		Bundle [] arrBundles = new Bundle[bundles];
		
		// by xiaowei zhou, now we do not enumerate bundle resolving orders, 20111214
//		genPermutationToResolve(arrBundles, 0, bundles, new boolean[bundles]);
		for (int i = 0; i < bundles; i++) {
			arrBundles[i] = m_bundleList.get(i);
		}
		resolvePerm(arrBundles);
		
	}
	
	private void genPermutationToResolve(Bundle[] arrBundles, int index,
			int bundles, boolean[] isUsed) {
		
		for (int i = 0; i < bundles; i++) {
			if (!isUsed[i]) {
				isUsed[i] = true;
				arrBundles[index] = m_bundleList.get(i);
				if (index < bundles - 1) {
					genPermutationToResolve(arrBundles, index + 1, bundles,
							isUsed);
				} else {
					resolvePerm(arrBundles);
				}
				isUsed[i] = false;
			}
		}
	}
	
//	private void unResolveBundles() {
//		for (Bundle bundle : m_bundleList) {
//			((BundleImpl) bundle).setState(Bundle.INSTALLED);
//		}
//	}
	
	private void resolvePerm(Bundle[] arrBundles) {
		
		StatefulResolver resolver;
		
		int bundles = arrBundles.length;
		
		resolver = new StatefulResolver(m_felix);
		m_felix.setCurrentResolver(resolver);
		
		// add the bundle revisions to the resolver, clean their resolving states
		resolver.addRevision(m_felix.getExtensionManager()
				.getRevision());
		BundleRevisionImpl felixRev = (BundleRevisionImpl) m_felix
				.adapt(BundleRevision.class);
		felixRev.setWiring(null);
		m_felix.setState(Bundle.INSTALLED);
		for (int i = 0; i < bundles; i++) {
			BundleImpl bundle = (BundleImpl) arrBundles[i];
			bundle.setState(Bundle.INSTALLED);
			if (!bundle.isExtension()) {
				BundleRevision revision = bundle.adapt(BundleRevision.class);
				((BundleRevisionImpl)revision).setWiring(null);
				resolver.addRevision(revision);
			}
		}
	
		// resolve the system bundle
		m_felix.acquireBundleLock(m_felix, Bundle.INSTALLED | Bundle.RESOLVED
				| Bundle.STARTING | Bundle.ACTIVE);
		try {
			resolver.resolve(
					Collections.singleton(m_felix.adapt(BundleRevision.class)),
					Collections.EMPTY_SET);
		} catch (ResolveException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BundleException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			m_felix.releaseBundleLock(m_felix);
		}
		
		// resolve bundles
		for (int i = 0; i < bundles; i++) {
			Bundle bundle = arrBundles[i];
			BundleRevision revision = (BundleRevision) bundle
					.adapt(BundleRevision.class);
						
			m_felix.acquireBundleLock((BundleImpl) bundle, Bundle.INSTALLED
					| Bundle.RESOLVED | Bundle.STARTING | Bundle.ACTIVE
					| Bundle.STOPPING);
			try {
				resolver.resolve(Collections.singleton(revision),
						Collections.EMPTY_SET);
			} catch (ResolveException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BundleException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				m_felix.releaseBundleLock((BundleImpl) bundle);
			}
			
			
		}
		
		System.out.println();
	}
	
	public AnalysisTrigger(Felix m_felix, List<Bundle> bundleList) {
		super();
		this.m_felix = m_felix;
		this.m_bundleList = bundleList;
	}
	
	private void printSameNamedClasses(Map<String, Set<Type>> snTypesMap) {
		Set<Entry<String, Set<Type>>> entrySet = snTypesMap.entrySet();
		OutputUtil.outSNClassln("The system under analysis has " + entrySet.size()
				+ " classes which are in more than one bundle:");
		OutputUtil.outSNClassln();
		for (Entry<String, Set<Type>> entry : entrySet) {
			String className = entry.getKey();
			OutputUtil.outSNClassln(className + "  which are in the following bundles:");
			Set<Type> typeSet = entry.getValue();
			for (Type type : typeSet) {
				Integer bundleNum = ((RefType) type).getDefCL();
//				if(bundleNum == -1) {
//					OutputUtil.outSNClassln("  JRE System Library");
//				} else {
//					Bundle bundle = Bundles.getBundle(bundleNum);
//					OutputUtil.outSNClassln("  " + bundle.getSymbolicName() + " "
//							+ bundle.getVersion() + " (" + bundle.getLocation()
//							+ ")");
//					
//					
				OutputUtil.outSNClassln(Bundles.getBundleStr(bundleNum));
//				}
			}
			OutputUtil.outSNClassln();
		}
		OutputUtil.flushSNClass();
	}
}
