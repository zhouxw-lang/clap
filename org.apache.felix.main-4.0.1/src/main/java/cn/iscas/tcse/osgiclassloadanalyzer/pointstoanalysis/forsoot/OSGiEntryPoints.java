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
package cn.iscas.tcse.osgiclassloadanalyzer.pointstoanalysis.forsoot;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;

import org.osgi.framework.Bundle;

import soot.EntryPoints;
import soot.G;
import soot.Local;
import soot.Modifier;
import soot.RefType;
import soot.Scene;
import soot.Singletons.Global;
import soot.SootClass;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.SootMethodRefImpl;
import soot.Type;
import soot.VoidType;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.NewExpr;
import soot.jimple.StaticInvokeExpr;
import soot.jimple.VirtualInvokeExpr;
import cn.iscas.tcse.osgiclassloadanalyzer.Bundles;

public class OSGiEntryPoints extends EntryPoints {

	public OSGiEntryPoints(Global g) {
		super(g);
	}
	
	public static OSGiEntryPoints v() { return G.v().osgi_EntryPoints(); }
	
	public List<SootMethod> all() {
		
		Iterator<Bundle> iterBuns = Bundles.getNumToBundle().values().iterator();
		
		List<SootMethod> methods = new ArrayList<SootMethod>();
		
		while(iterBuns.hasNext()) {
			Bundle bun = iterBuns.next();

			Dictionary<String, String> headers = bun.getHeaders();
			String strActivClass = headers.get("Bundle-Activator");
			if (strActivClass != null) {
				Integer bundleNum = Bundles.getBundleNum(bun);
				SootClass scActivClass = Scene.v().getSootClass(strActivClass,
						bundleNum);
				SootMethod vstMethod = createVstMethod(scActivClass);
				methods.add(vstMethod);
//				SootMethod startMethod = scActivClass.getMethodByName("start");
//				if (startMethod != null) {
//					methods.add(startMethod);
//				}
//				SootMethod stopMethod = scActivClass.getMethodByName("stop");
//				if (stopMethod != null) {
//					methods.add(stopMethod);
//				}
			}
		}
		
//		methods.addAll(implicit());
		
		return methods;
	}
	
	protected SootMethod createVstMethod(SootClass containingClass) {
		SootMethod m = new SootMethod("iscasVst", new ArrayList<Type>(), VoidType.v(), Modifier.STATIC);
		JimpleBody body = Jimple.v().newBody(m);
		m.setActiveBody(body);
		NewExpr newExpr = Jimple.v().newNewExpr(containingClass.getType());
		LocalGenerator lg = new LocalGenerator(body);
		Local thisLocal = lg.generateLocal(containingClass.getType());
		AssignStmt assignThisStmt = Jimple.v().newAssignStmt(thisLocal, newExpr);
		body.getUnits().add(assignThisStmt); // Activator v = new Activator();
		
		RefType bundleCtxtType = Scene.v().getRefType("org.osgi.framework.BundleContext", containingClass.getDefCLNumber(), 0);
		Local bundleContextLocal = lg.generateLocal(bundleCtxtType);
		SootClass ctxtGetCls = Scene.v().getSootClass("org.osgi.framework.BundleContextGetter", containingClass.getDefCLNumber(), 0);
		SootMethodRef ctxtGetMRef = new SootMethodRefImpl(ctxtGetCls, "getBundleContext", new ArrayList<Type>(), bundleCtxtType, true);
		StaticInvokeExpr getCtxtInvkExpr = Jimple.v().newStaticInvokeExpr(ctxtGetMRef);
		AssignStmt assignCtxtStmt = Jimple.v().newAssignStmt(bundleContextLocal, getCtxtInvkExpr);
		body.getUnits().addLast(assignCtxtStmt); // BundleContext bundleContext = org.osgi.framework.BundleContextGetter.getBundleContext();
		
		List<Type> stParList = new ArrayList<Type>();
		stParList.add(bundleCtxtType);
		
		SootMethodRef startMRef = new SootMethodRefImpl(containingClass, "start", stParList, VoidType.v(), false);
		SootMethod startMeth = startMRef.resolve();
		if (startMeth != null && startMeth.isConcrete()) {
			VirtualInvokeExpr startInvkExpr = Jimple.v().newVirtualInvokeExpr(thisLocal, startMRef, bundleContextLocal);
			InvokeStmt startInvkStmt = Jimple.v().newInvokeStmt(startInvkExpr);
			body.getUnits().addLast(startInvkStmt); // v.start(bundleContext);
		}
		
		SootMethodRef stopMRef = new SootMethodRefImpl(containingClass, "stop", stParList, VoidType.v(), false);
		SootMethod stopMeth = stopMRef.resolve();
		if(stopMeth!=null&&stopMeth.isConcrete()) {
			VirtualInvokeExpr stopInvkExpr = Jimple.v().newVirtualInvokeExpr(thisLocal, stopMRef, bundleContextLocal);
			InvokeStmt stopInvkStmt = Jimple.v().newInvokeStmt(stopInvkExpr);
			body.getUnits().addLast(stopInvkStmt); // v.stop(bundleContext);
		}
		
		containingClass.addMethod(m);

		return m;
	}


}
