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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.AbstractSootFieldRef.FieldResolutionFailedException;
import soot.Body;
import soot.FastHierarchy;
import soot.Local;
import soot.Modifier;
import soot.PointsToAnalysis;
import soot.PointsToSet;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SootMethodRef;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;
import soot.jimple.spark.ondemand.AllocAndContext;
import soot.jimple.spark.ondemand.AllocAndContextSet;
import soot.jimple.spark.ondemand.DemandCSPointsTo;
import soot.jimple.spark.pag.VarNode;
import soot.jimple.spark.sets.EmptyPointsToSet;
import soot.tagkit.LineNumberTag;

public class ClassLoadingAwareTypeChecker {
	
	private Scene cm = Scene.v();
	
	public ClassLoadingAwareTypeChecker() {

		
	}
	
	public void checkForBadPointsto(Set<SootClass> classesToCheck) {

		PointsToAnalysis pointsto = cm.getPointsToAnalysis();
		DemandCSPointsTo demcspto = (DemandCSPointsTo)pointsto;
		
		for (SootClass sootClass : classesToCheck) {
			if (sootClass.isConcrete()) {

				Collection<SootMethod> methods = sootClass.getMethods();
				for (SootMethod method : methods) {

					checkOverridingLC(sootClass, method);

					if (method.isConcrete()) {
						Body body = method.retrieveActiveBody();

						// iterate through the statements of method
						Collection<Unit> uc = body.getUnits();
						for (Unit u : uc) {
							if (u instanceof Stmt) {
								Stmt stmt = (Stmt) u;
								checkStmt(stmt, method, demcspto);
							}
						}
					}
				} // for every method
			}
		} // for every class
	}
	
	// check loading constraints for method overriding (JVM spec 2nd 5.4.2)
	private void checkOverridingLC(SootClass cls, SootMethod method) {
		if(!cls.hasSuperclass()) {
			return;
		}
		SootClass superClass = null;
		superClass = cls.getSuperclass();
		String subSig = method.getSubSignature();
		while (superClass != null) {
			if (superClass.declaresMethod(subSig)) {
				SootMethod superMethod = superClass.getMethod(subSig);
				int superMod = superMethod.getModifiers();
				if (!Modifier.isPrivate(superMod)
						&& !Modifier.isNative(superMod)
						&& !Modifier.isFinal(superMod)
						&& !Modifier.isStatic(superMod)
						&& !superMethod.isPhantom()) {
					// check loading constraints between method and superMethod
					Integer defCL = cls.getDefCLNumber();
					Integer superDefCL = superClass.getDefCLNumber();
					
					// check return type
					RefType retType = TypeUtil.getCheckableRefType(method.getReturnType());
					if(retType!=null) {
						Integer retDefCLByC = ClassLoaders.initCLToDefCL(defCL, retType.getClassName());
						Integer retDefCLBySuper = ClassLoaders.initCLToDefCL(superDefCL, retType.getClassName());
						if(retDefCLByC!=null&&!retDefCLByC.equals(retDefCLBySuper)) {
							// got loading constraint violation
							OutputUtil.outLCVioln("A potential loading constraint violation is detected in method overriding (which may cause LinkageError)");
							String methodRefSig = method.getSignature();
							OutputUtil.outLCVioln("The return type of method \"" +methodRefSig+"\"");
							OutputUtil.outLCVioln("loaded by the defining class loader of the declaring class of the overriding method");
							OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(defCL));
							OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(retDefCLByC));
							
							OutputUtil.outLCVioln("The declaring class of the overrided method is \"" + superClass.toString() +"\"");
							OutputUtil.outLCVioln("While that type loaded by the defining class loader of the declaring class of the overrided method");
							OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(superDefCL));
							OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(retDefCLBySuper));
							OutputUtil.outLCVioln();
							OutputUtil.outLCVioln();
							OutputUtil.flushLCVio();
						}
					}
					
					// check parameter types
					List<Type> parTypes = method.getParameterTypes();
					int i = 1;
					for (Type parType : parTypes) {
						RefType parRefType = TypeUtil.getCheckableRefType(parType);
						if (parRefType != null) {
							Integer parDefCLByC = ClassLoaders.initCLToDefCL(defCL, parRefType.getClassName());
							Integer parDefCLBySuper = ClassLoaders.initCLToDefCL(superDefCL, parRefType.getClassName());
							if(parDefCLByC!=null&&!parDefCLByC.equals(parDefCLBySuper)) {
								// got loading constraint violation
								OutputUtil.outLCVioln("A potential loading constraint violation is detected in method overriding (which may cause LinkageError)");
								String methodRefSig = method.getSignature();
								OutputUtil.outLCVioln("The type of the " + i + "(st/nd/th) parameter of method \"" + methodRefSig + "\"");
								OutputUtil.outLCVioln("loaded by the defining class loader of the declaring class of the overriding method");
								OutputUtil.outLCVioln("which is the bundle class loader of " + Bundles.getBundleStr(defCL));
								OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(parDefCLByC));
								
								OutputUtil.outLCVioln("The declaring class of the overrided method is \"" + superClass.toString() +"\"");
								OutputUtil.outLCVioln("While that type loaded by the defining class loader of the declaring class of the overrided method");
								OutputUtil.outLCVioln("which is the bundle class loader of " + Bundles.getBundleStr(superDefCL));
								OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(parDefCLBySuper));
								OutputUtil.outLCVioln();
								OutputUtil.outLCVioln();
								OutputUtil.flushLCVio();
							}
						}
						i++;
					}
				}
				break;
			} else if (superClass.hasSuperclass()) {
				superClass = superClass.getSuperclass();
			} else {
				superClass = null;
			}
			
			
		}
		
	}
	
	// check loading constaints for field resolution, JVM spec 2nd 5.4.3.2	
	private void checkFieldRefLC(Stmt stmt, SootMethod containingMethod) {
		if(stmt.containsFieldRef()) {
			SootClass containingClass = containingMethod.getDeclaringClass();
			FieldRef fieldRef = stmt.getFieldRef();
			SootField field = null;
			try {
				field = fieldRef.getField();
			} catch (FieldResolutionFailedException e) {
				// field resolution failed!
				e.printStackTrace(OutputUtil.getWriterLCVios());
				OutputUtil.outLCVioln();
				OutputUtil.outLCVioln();
				OutputUtil.flushLCVio();
			}
			
			if (field != null && !field.isPhantom()) {
				Type fieldType = field.getType();
				RefType fieldRefType = TypeUtil.getCheckableRefType(fieldType);
				if (fieldRefType != null) {
					SootClass fieldDeclCls = field.getDeclaringClass();
					Integer fieldDefCLByContain = ClassLoaders.initCLToDefCL(containingClass.getDefCLNumber(), fieldRefType.getClassName());
					Integer fieldDefCLByDecl = ClassLoaders.initCLToDefCL(fieldDeclCls.getDefCLNumber(), fieldRefType.getClassName());
					if(fieldDefCLByContain!=null && !fieldDefCLByContain.equals(fieldDefCLByDecl)) {
						// got loading constraint violation
						OutputUtil.outLCVioln("A potential loading constraint violation is detected in field resolution of \""+fieldRef+"\" (which may cause LinkageError)");
						int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
						OutputUtil.outLCVioln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
						OutputUtil.outLCVioln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));

						OutputUtil.outLCVioln("The type (or the base type of array type) of the field is \"" +fieldRefType+"\"");
						OutputUtil.outLCVioln("loaded by the defining class loader of the containing class of the field reference");
//						OutputUtil.outLCVioln("which is the bundle class loader of " + getBundleStr(containingClass.getDefCLNumber()));
						OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(fieldDefCLByContain));
						
						OutputUtil.outLCVioln("The name of the declaring class of the field is \"" + fieldDeclCls.toString() +"\"");
						OutputUtil.outLCVioln("While that type loaded by the defining class loader of the declaring class of the field");
						OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(fieldDeclCls.getDefCLNumber()));
						OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(fieldDefCLByDecl));
						OutputUtil.outLCVioln();
						OutputUtil.outLCVioln();
						OutputUtil.flushLCVio();
					}
				}
				
			}
		}
	}
	
	// check loading constaints for method resolution, JVM spec 2nd 5.4.3.3, 5.4.3.4
	private void checkMethodRefLC(Stmt stmt, SootMethod containingMethod) {
		if (stmt.containsInvokeExpr()) {
			SootClass containingClass = containingMethod.getDeclaringClass();
			InvokeExpr invkExpr = stmt.getInvokeExpr();
			SootMethod targetMethod = invkExpr.getMethod();
			if (!targetMethod.isPhantom()) {
				SootClass declClass = targetMethod.getDeclaringClass();
				Integer containDefCL = containingClass.getDefCLNumber();
				Integer declDefCL = declClass.getDefCLNumber();

				// check return type
				RefType retType = TypeUtil.getCheckableRefType(targetMethod.getReturnType());
				if(retType!=null) {
					Integer retDefCLByContain = ClassLoaders.initCLToDefCL(containDefCL, retType.getClassName());
					Integer retDefCLByDecl = ClassLoaders.initCLToDefCL(declDefCL, retType.getClassName());
					if(retDefCLByContain!=null&&!retDefCLByContain.equals(retDefCLByDecl)) {
						// got loading constraint violation
						OutputUtil.outLCVioln("A potential loading constraint violation is detected in method resolution (which may cause LinkageError)");
						int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
						OutputUtil.outLCVioln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
						OutputUtil.outLCVioln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
						
						String methodRefSig = targetMethod.getSignature();
						OutputUtil.outLCVioln("the target method is \""+ methodRefSig+"\"" + " in class \""+declClass.toString()+"\"");
						OutputUtil.outLCVioln("in bundle "+Bundles.getBundleStr(declClass.getDefCLNumber()));
												
						OutputUtil.outLCVioln("The return type of this method \"" +methodRefSig+"\"");
						OutputUtil.outLCVioln("loaded by the defining class loader of the containing class of the method reference");
						OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(containDefCL));
						OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(retDefCLByContain));
						
						OutputUtil.outLCVioln("While that type loaded by the defining class loader of the declaring class of the method");
						OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(declDefCL));
						OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(retDefCLByDecl));
						OutputUtil.outLCVioln();
						OutputUtil.outLCVioln();
						OutputUtil.flushLCVio();
					}
				}
				
				// check parameter types
				List<Type> parTypes = targetMethod.getParameterTypes();
				int i = 1;
				for (Type parType : parTypes) {
					RefType parRefType = TypeUtil.getCheckableRefType(parType);
					if (parRefType != null) {
						Integer parDefCLByContain = ClassLoaders.initCLToDefCL(containDefCL, parRefType.getClassName());
						Integer parDefCLByDecl = ClassLoaders.initCLToDefCL(declDefCL, parRefType.getClassName());
						if(parDefCLByContain!=null&&!parDefCLByContain.equals(parDefCLByDecl)) {
							// got loading constraint violation
							OutputUtil.outLCVioln("A potential loading constraint violation is detected in method resolution (which may cause LinkageError)");
							int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
							OutputUtil.outLCVioln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
							OutputUtil.outLCVioln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
							
							String methodRefSig = targetMethod.getSignature();
							OutputUtil.outLCVioln("the target method is \""+ methodRefSig+"\"" + " in class \""+declClass.toString()+"\"");
							OutputUtil.outLCVioln("in bundle "+Bundles.getBundleStr(declClass.getDefCLNumber()));
													
							OutputUtil.outLCVioln("The type of the " + i + "(st/nd/th) parameter of method \"" +methodRefSig+"\"");
							OutputUtil.outLCVioln("loaded by the defining class loader of the containing class of the method reference");
							OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(containDefCL));
							OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(parDefCLByContain));
							
							OutputUtil.outLCVioln("While that type loaded by the defining class loader of the declaring class of the method");
							OutputUtil.outLCVioln("which is the class loader of bundle " + Bundles.getBundleStr(declDefCL));
							OutputUtil.outLCVioln("is the class in bundle " + Bundles.getBundleStr(parDefCLByDecl));
							OutputUtil.outLCVioln();
							OutputUtil.outLCVioln();
							OutputUtil.flushLCVio();
						}
					}
					i++;
				}
			}
		}
	}
	
	private void checkStmt(Stmt stmt, SootMethod containingMethod,
			DemandCSPointsTo demcspto) {
		
		// check loading constraints
		checkFieldRefLC(stmt, containingMethod);
		checkMethodRefLC(stmt, containingMethod);
		
		// check bad points-to
		if(stmt instanceof AssignStmt) {
			checkAssignStmt((AssignStmt) stmt, containingMethod, demcspto);
		} else if(stmt instanceof InvokeStmt) {
			checkInvokeExpr(stmt, stmt.getInvokeExpr(), containingMethod, demcspto);
		} else if(stmt instanceof ReturnStmt) {
			checkReturnValStmt((ReturnStmt)stmt, containingMethod, demcspto);
		}
	}
	
	private void checkReturnValStmt(ReturnStmt stmt, SootMethod containingMethod,
			DemandCSPointsTo demcspto) {
		SootClass containingClass = containingMethod.getDeclaringClass();
		Type retType = containingMethod.getReturnType();
		RefType retRefType = TypeUtil.getCheckableRefType(retType);
		if (retRefType != null && TypeUtil.isSameNamedType(retRefType.getClassName())) {
			Value retOp = stmt.getOp();
			if(retOp instanceof Local) {
				Local loc = (Local) retOp;
				RefType retResolvType = RefType.v(retRefType.getClassName(), containingClass.getDefCLNumber(), 0);
				ReturnValueChecker retChecker = new ReturnValueChecker(containingMethod, stmt, loc, demcspto, retResolvType, true);
				retChecker.check();
			}
		}
	}
		
	private void checkInvokeExpr(Stmt stmt, InvokeExpr invkExpr,
			SootMethod containingMethod, DemandCSPointsTo demcspto) {
		SootClass containingClass = containingMethod.getDeclaringClass();
		List<Value> args = invkExpr.getArgs();
		SootMethodRef methRef = invkExpr.getMethodRef();
		List<Type> parTypes = methRef.parameterTypes();
		Iterator<Type> iterParType = parTypes.iterator();
		int i = 0;
		for (Value arg : args) {
			i++;
			if(arg instanceof Local) {
				Local loc = (Local) arg;
				Type parType = iterParType.next();
				RefType parRefType = TypeUtil.getCheckableRefType(parType);
				if (parRefType != null && TypeUtil.isSameNamedType(parRefType.getClassName())) {
					RefType parResolvType = RefType.v(parRefType.getClassName(), containingClass.getDefCLNumber(), 0);
					InvocationChecker invkChker = new InvocationChecker(containingMethod, stmt, loc, demcspto, parResolvType, true, i);
					invkChker.check();
				}
				
			}
		}
		
		if(invkExpr instanceof InstanceInvokeExpr) {
			Value invkBase = ((InstanceInvokeExpr) invkExpr).getBase();
			Type baseType = invkBase.getType();
			if(invkBase instanceof Local) {
				Local loc = (Local)invkBase;
				RefType baseRefType = TypeUtil.getCheckableRefType(baseType);
				if (baseRefType != null && TypeUtil.isSameNamedType(baseRefType.getClassName())) {
					RefType baseResolvType = RefType.v(baseRefType.getClassName(), containingClass.getDefCLNumber(), 0);
					InvocationChecker invkChker = new InvocationChecker(containingMethod, stmt, loc, demcspto, baseResolvType, true, 0);
					invkChker.check();
				}
			}
		}
	}
	
	private void checkAssignStmt(AssignStmt stmt, SootMethod containingMethod,
			DemandCSPointsTo demcspto) {
		Value l = stmt.getLeftOp();
		Value r = stmt.getRightOp();
		Type leftType = l.getType();
		Type rightType = r.getType();
		if (!(leftType instanceof RefLikeType) || !(rightType instanceof RefLikeType))
			return;
		
		SootClass containingClass = containingMethod.getDeclaringClass();
		
		if(r instanceof CastExpr) {
			Type castToType = ((CastExpr) r).getCastType();
			RefType castToRefType = TypeUtil.getCheckableRefType(castToType);
			if (castToRefType != null && TypeUtil.isSameNamedType(castToRefType.getClassName())) {
				Value castOp = ((CastExpr) r).getOp();
				if(castOp instanceof Local) {
					Local locCastOp = (Local) castOp;
					CastChecker castChk = new CastChecker(containingMethod,
							stmt, locCastOp, demcspto, castToRefType, true);
					castChk.check();
				}
			}
		} else if (r instanceof InstanceFieldRef) {
			InstanceFieldRef insFRef = (InstanceFieldRef) r;
			Value refBase = insFRef.getBase();
			RefType baseRefType = TypeUtil.getCheckableRefType(refBase.getType());
			if (baseRefType != null && TypeUtil.isSameNamedType(baseRefType.getClassName())) {
				RefType baseResolvType = RefType.v(baseRefType.getClassName(),
						containingClass.getDefCLNumber(), 0);
				if (refBase instanceof Local) {
					Local locRefBase = (Local) refBase;
					InstanceFieldRefChecker frChecker = new InstanceFieldRefChecker(
							containingMethod, stmt, locRefBase, demcspto, baseResolvType,
							true, insFRef);
					frChecker.check();
				}
			}
		} else if (r instanceof InvokeExpr) {
			checkInvokeExpr(stmt, (InvokeExpr) r, containingMethod, demcspto);
		}
		
		if (l instanceof FieldRef) {
			if(r instanceof Local) {
				Local loc = (Local)r;
				RefType refFieldType =  TypeUtil.getCheckableRefType(l.getType());
				if(refFieldType!=null && TypeUtil.isSameNamedType(refFieldType.getClassName())) {
					RefType refFResolvType = RefType.v(
							refFieldType.getClassName(),
							containingClass.getDefCLNumber(), 0);
					StoreChecker sChecker = new StoreChecker(containingMethod, stmt, loc, demcspto, refFResolvType, true, 1, l.toString());
					sChecker.check();
				}
			}
			if(l instanceof InstanceFieldRef) {
				InstanceFieldRef insFRef = (InstanceFieldRef) l;
				Value refBase = insFRef.getBase();
				RefType baseRefType = TypeUtil.getCheckableRefType(refBase.getType());
				if (baseRefType != null && TypeUtil.isSameNamedType(baseRefType.getClassName())) {
					RefType baseResolvType = RefType.v(baseRefType.getClassName(),
							containingClass.getDefCLNumber(), 0);
					if (refBase instanceof Local) {
						Local locRefBase = (Local) refBase;
						InstanceFieldRefChecker frChecker = new InstanceFieldRefChecker(
								containingMethod, stmt, locRefBase, demcspto, baseResolvType,
								true, insFRef);
						frChecker.check();
					}
				}
			}
		} else if (l instanceof ArrayRef) {
			if(r instanceof Local) {
				Local loc = (Local)r;
				RefType refArrBaseType =  TypeUtil.getCheckableRefType(l.getType());
				if(refArrBaseType!=null && TypeUtil.isSameNamedType(refArrBaseType.getClassName())) {
					RefType refABResolvType = RefType.v(
							refArrBaseType.getClassName(),
							containingClass.getDefCLNumber(), 0);
					StoreChecker sChecker = new StoreChecker(containingMethod, stmt, loc, demcspto, refABResolvType, true, 2, l.toString());
					sChecker.check();
				}
			}
		}
		
	}
	
	private class ReturnValueChecker extends PtoTypeChecker {

		public ReturnValueChecker(SootMethod containingMethod, Stmt stmt,
				Local loc, DemandCSPointsTo demcspto, RefType againstType,
				boolean isAgainstTypeShouldBeSuper) {
			super(containingMethod, stmt, loc, demcspto, againstType,
					isAgainstTypeShouldBeSuper);
		}

		public void gotTypeErr(AllocAndContext allocCtxt, RefType ptoRefType) {
			int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
			OutputUtil.outTypeErr("A potential runtime type error is detected when return (upon a return statement) from method ");
			String methodRefSig = containingMethod.getSignature();
			OutputUtil.outTypeErrln("\"" +methodRefSig+"\" (which may cause VerifyError).");
			
			OutputUtil.outTypeErrln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
			OutputUtil.outTypeErrln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
			OutputUtil.outTypeErrln("For the return type (base type of array type) of the method is: \"" + againstType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(againstType.getDefCL()));
			
			OutputUtil.outTypeErrln("may point to:");
			OutputUtil.outTypeErrln(allocCtxt.alloc.toString());
			SootMethod methodHoldingAlloc = allocCtxt.alloc.getMethod();
			SootClass classHoldingAlloc = methodHoldingAlloc.getDeclaringClass();
			OutputUtil.outTypeErrln("in method \""+methodHoldingAlloc.toString()+"\"" +"in class \""+classHoldingAlloc.toString()+"\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(classHoldingAlloc.getDefCLNumber()));
			
			OutputUtil.outTypeErrln("The type (base type of array type) of the allocation site is: \"" + ptoRefType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(ptoRefType.getDefCL()));
			
			OutputUtil.outTypeErrln("The points-to trace is:");
			
			VarNode varNode = demcspto.getPAG().findLocalVarNode(loc);
			demcspto.dumpPathForLoc(varNode, allocCtxt.alloc);
			OutputUtil.outTypeErrln();
			OutputUtil.outTypeErrln();
			OutputUtil.flushTypeErr();
		}
		
	}
	
	private class InvocationChecker extends PtoTypeChecker {
		
		protected InvokeExpr invkExpr;
		
		protected int argNum;

		public InvocationChecker(SootMethod containingMethod, Stmt stmt,
				Local loc, DemandCSPointsTo demcspto, RefType againstType,
				boolean isAgainstTypeShouldBeSuper, int argNum) {
			super(containingMethod, stmt, loc, demcspto, againstType,
					isAgainstTypeShouldBeSuper);
			invkExpr = stmt.getInvokeExpr();
			this.argNum = argNum;
		}

		public void gotTypeErr(AllocAndContext allocCtxt, RefType ptoRefType) {
			int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
			OutputUtil.outTypeErr("A potential runtime type error is detected when invoking method ");
			String methodRefSig = invkExpr.getMethodRef().getSignature();
			OutputUtil.outTypeErrln("\"" +methodRefSig+"\" (which may cause VerifyError).");
			

			OutputUtil.outTypeErrln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
			OutputUtil.outTypeErrln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
			OutputUtil.outTypeErrln("For the No."+argNum+" argument, which is of type (base type of array type): \"" + againstType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(againstType.getDefCL()));
			
			OutputUtil.outTypeErrln("may point to:");
			OutputUtil.outTypeErrln(allocCtxt.alloc.toString());
			SootMethod methodHoldingAlloc = allocCtxt.alloc.getMethod();
			SootClass classHoldingAlloc = methodHoldingAlloc.getDeclaringClass();
			OutputUtil.outTypeErrln("in method \""+methodHoldingAlloc.toString()+"\"" +"in class \""+classHoldingAlloc.toString()+"\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(classHoldingAlloc.getDefCLNumber()));
			
			OutputUtil.outTypeErrln("The type (base type of array type) of the allocation site is: \"" + ptoRefType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(ptoRefType.getDefCL()));
			
			OutputUtil.outTypeErrln("The points-to trace is:");
			
			VarNode varNode = demcspto.getPAG().findLocalVarNode(loc);
			demcspto.dumpPathForLoc(varNode, allocCtxt.alloc);
			OutputUtil.outTypeErrln();
			OutputUtil.outTypeErrln();
			OutputUtil.flushTypeErr();
		}
		
	}
	
	private class StoreChecker extends PtoTypeChecker {
		// check when storing to field or array
		
		protected int storeKind; // 1 for fieldstore, 2 for arraystore
		
		protected String refString;
		
		public StoreChecker(SootMethod containingMethod, Stmt stmt, Local loc,
				DemandCSPointsTo demcspto, RefType againstType,
				boolean isAgainstTypeShouldBeSuper, int storeKind, String refString) {
			super(containingMethod, stmt, loc, demcspto, againstType,
					isAgainstTypeShouldBeSuper);
			this.storeKind = storeKind;
			this.refString = refString;
		}

		@Override
		public void gotTypeErr(AllocAndContext allocCtxt, RefType ptoRefType) {
			int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
			OutputUtil.outTypeErr("A potential runtime type error is detected when storing into");
			
			if (storeKind == 1) {
				OutputUtil.outTypeErrln("field \"" +refString+"\" (which may cause VerifyError).");
			} else if (storeKind == 2) {
				OutputUtil.outTypeErrln("array \"" +refString+"\" (which may cause ArrayStoreException).");
			}
			OutputUtil.outTypeErrln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
			OutputUtil.outTypeErrln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
			if (storeKind == 1) {
				OutputUtil.outTypeErrln("For the type (base type of array type) of the field is: \"" + againstType.toString() + "\"");
			} else if (storeKind == 2) {
				OutputUtil.outTypeErrln("For the base type of the array is: \"" + againstType.toString() + "\"");
			}
			//TODO:print the type of base
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(againstType.getDefCL()));
			
			OutputUtil.outTypeErrln("may point to:");
			OutputUtil.outTypeErrln(allocCtxt.alloc.toString());
			SootMethod methodHoldingAlloc = allocCtxt.alloc.getMethod();
			SootClass classHoldingAlloc = methodHoldingAlloc.getDeclaringClass();
			OutputUtil.outTypeErrln("in method \""+methodHoldingAlloc.toString()+"\"" +"in class \""+classHoldingAlloc.toString()+"\"");
			OutputUtil.outTypeErrln("in bundle "+Bundles.getBundleStr(classHoldingAlloc.getDefCLNumber()));
			
			OutputUtil.outTypeErrln("The type (base type of array type) of the allocation site is: \"" + ptoRefType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(ptoRefType.getDefCL()));
			
			OutputUtil.outTypeErrln("The points-to trace is:");
			
			VarNode varNode = demcspto.getPAG().findLocalVarNode(loc);
			demcspto.dumpPathForLoc(varNode, allocCtxt.alloc);
			OutputUtil.outTypeErrln();
			OutputUtil.outTypeErrln();
			OutputUtil.flushTypeErr();
		}
		
	}
	
	private class InstanceFieldRefChecker extends PtoTypeChecker {
		
		protected InstanceFieldRef insFRef;

		public InstanceFieldRefChecker(SootMethod containingMethod,
				Stmt stmt, Local loc, DemandCSPointsTo demcspto,
				RefType againstType, boolean isAgainstTypeShouldBeSuper,
				InstanceFieldRef insFRef) {
			super(containingMethod, stmt, loc, demcspto, againstType,
					isAgainstTypeShouldBeSuper);
			this.insFRef = insFRef;
		}

		public void gotTypeErr(AllocAndContext allocCtxt, RefType ptoRefType) {
			int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
			
			OutputUtil.outTypeErrln("A potential runtime type error in field reference \""+insFRef+"\" is detected (which may cause VerifyError).");
			OutputUtil.outTypeErrln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
			OutputUtil.outTypeErrln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
			OutputUtil.outTypeErrln("For the base of the reference, which is of type (base type of array type): \"" + againstType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(againstType.getDefCL()));
			
			OutputUtil.outTypeErrln("may point to:");
			OutputUtil.outTypeErrln(allocCtxt.alloc.toString());
			SootMethod methodHoldingAlloc = allocCtxt.alloc.getMethod();
			SootClass classHoldingAlloc = methodHoldingAlloc.getDeclaringClass();
			OutputUtil.outTypeErrln("in method \""+methodHoldingAlloc.toString()+"\"" +"in class \""+classHoldingAlloc.toString()+"\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(classHoldingAlloc.getDefCLNumber()));
			
			OutputUtil.outTypeErrln("The type (base type of array type) of the allocation site is: \"" + ptoRefType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(ptoRefType.getDefCL()));
			
			OutputUtil.outTypeErrln("The points-to trace is:");
			
			VarNode varNode = demcspto.getPAG().findLocalVarNode(loc);
			demcspto.dumpPathForLoc(varNode, allocCtxt.alloc);
			OutputUtil.outTypeErrln();
			OutputUtil.outTypeErrln();
			OutputUtil.flushTypeErr();
		}
		
		
	}
	
	private class CastChecker extends PtoTypeChecker {

		public CastChecker(SootMethod containingMethod, Stmt stmt, Local loc,
				DemandCSPointsTo demcspto, RefType againstType,
				boolean isAgainstTypeShouldBeSuper) {
			super(containingMethod, stmt, loc, demcspto, againstType,
					isAgainstTypeShouldBeSuper);

		}

		public void gotTypeErr(AllocAndContext allocCtxt, RefType ptoRefType) {
			int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
			
			OutputUtil.outTypeErr("A potential bad cast is detected (which may cause ClassCastException) ");
			OutputUtil.outTypeErrln("when trying to cast variable \""+loc.getName()+"\" to other type");
			OutputUtil.outTypeErrln("At line \""+lineNumber+"\" in method \"" + containingMethod.getName() + "\" in class \"" + containingClass.toString()+"\".");
			OutputUtil.outTypeErrln("in bundle "+Bundles.getBundleStr(containingClass.getDefCLNumber()));
			OutputUtil.outTypeErrln("For the target type (base type of array type) of the cast expression is: \"" + againstType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(againstType.getDefCL()));
			
			OutputUtil.outTypeErrln("Ant the variable to be casted may point to:");
			OutputUtil.outTypeErrln(allocCtxt.alloc.toString());
			SootMethod methodHoldingAlloc = allocCtxt.alloc.getMethod();
			SootClass classHoldingAlloc = methodHoldingAlloc.getDeclaringClass();
			OutputUtil.outTypeErrln("in method \""+methodHoldingAlloc.toString()+"\"" +"in class \""+classHoldingAlloc.toString()+"\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(classHoldingAlloc.getDefCLNumber()));
			
			OutputUtil.outTypeErrln("The type (base type of array type) of the allocation site is: \"" + ptoRefType.toString() + "\"");
			OutputUtil.outTypeErrln("in bundle " + Bundles.getBundleStr(ptoRefType.getDefCL()));
			
			OutputUtil.outTypeErrln("The points-to trace is:");
			
			VarNode varNode = demcspto.getPAG().findLocalVarNode(loc);
			demcspto.dumpPathForLoc(varNode, allocCtxt.alloc);
			OutputUtil.outTypeErrln();
			OutputUtil.outTypeErrln();
			OutputUtil.flushTypeErr();
		}
		
	}
	
	private abstract class PtoTypeChecker {
		
		protected SootMethod containingMethod;
		
		protected SootClass containingClass;
		
		protected Stmt stmt;

		protected Local loc;
		
		protected DemandCSPointsTo demcspto;
		
		protected RefType againstType;
		
		protected boolean isAgainstTypeShouldBeSuper;

		public abstract void gotTypeErr(AllocAndContext allocCtxt, RefType ptoRefType);
		
		public PtoTypeChecker(SootMethod containingMethod, Stmt stmt,
				Local loc, DemandCSPointsTo demcspto, RefType againstType,
				boolean isAgainstTypeShouldBeSuper) {
			super();
			this.containingMethod = containingMethod;
			this.stmt = stmt;
			this.loc = loc;
			this.demcspto = demcspto;
			this.againstType = againstType;
			this.isAgainstTypeShouldBeSuper = isAgainstTypeShouldBeSuper;
			
			containingClass = containingMethod.getDeclaringClass();
		}

		// for an instance of this class, check can only be called once
		public void check() {
			
			SootClass containingClass = containingMethod.getDeclaringClass();
			
			int lineNumber = CodeUtil.getlineNumber(stmt); // 0 for no line number
			
			if (containingClass.isHasLineNumber() && lineNumber == 0) {
				// we do not check statements generated by soot
				return;
			}
			
			PointsToSet ptoSet = demcspto.reachingObjects(loc);
			if (ptoSet instanceof AllocAndContextSet) {
				AllocAndContextSet allocCtxtSet = (AllocAndContextSet)ptoSet;
				FastHierarchy fastHierarchy = cm.getOrMakeFastHierarchy();
				for (AllocAndContext allocCtxt : allocCtxtSet) {
					Type ptoType = allocCtxt.alloc.getType();
					RefType ptoRefType = TypeUtil.getCheckableRefType(ptoType);
					if (ptoRefType != null) {
						boolean gotErr = false;
						if (isAgainstTypeShouldBeSuper) {
							if (!fastHierarchy.canStoreType(ptoRefType,
									againstType) && fastHierarchy.canStoreTypeDespiteCLs(ptoRefType, againstType)) {
								gotErr = true;
							}
						} else {
							if (!fastHierarchy.canStoreType(againstType,
									ptoRefType) && fastHierarchy.canStoreTypeDespiteCLs(againstType, ptoRefType)) {
								gotErr = true;
							}
						}
						if(gotErr) {
							gotTypeErr(allocCtxt, ptoRefType);
						}
					}
				}
			} else if (ptoSet instanceof EmptyPointsToSet) {
				
			} else {
				//TODO
				// the demand-driven analysis has aborted and the original result of SPARK is returned
//				throw new RuntimeException("The demand-driven analysis has aborted and the original result of SPARK is returned!");
			}
		}
	}
}
