/* Soot - a J*va Optimization Framework
 * Copyright (C) 2004 Ondrej Lhotak
 * Changed by Xiaowei Zhou for Clap, 2012
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

package soot;
import java.util.*;

import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.NewExpr;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.options.Options;
import soot.util.*;

/** Representation of a reference to a method as it appears in a class file.
 * Note that the method directly referred to may not actually exist; the
 * actual target of the reference is determined according to the resolution
 * procedure in the Java Virtual Machine Specification, 2nd ed, section 5.4.3.3.
 */

// by xiaowei zhou, change to public, 20120222
//class SootMethodRefImpl implements SootMethodRef {
public class SootMethodRefImpl implements SootMethodRef {
    public SootMethodRefImpl( 
            SootClass declaringClass,
            String name,
            List parameterTypes,
            Type returnType,
            boolean isStatic) {
        this.declaringClass = declaringClass;
        this.name = name;
        List l = new ArrayList();
        l.addAll(parameterTypes);
        this.parameterTypes = Collections.unmodifiableList(l);
        this.returnType = returnType;
        this.isStatic = isStatic;
        if( declaringClass == null ) throw new RuntimeException( "Attempt to create SootMethodRef with null class" );
        if( name == null ) throw new RuntimeException( "Attempt to create SootMethodRef with null name" );
        if( parameterTypes == null ) throw new RuntimeException( "Attempt to create SootMethodRef with null parameterTypes" );
        if( returnType == null ) throw new RuntimeException( "Attempt to create SootMethodRef with null returnType" );        
    }

    private final SootClass declaringClass;
    private final String name;
    private final List parameterTypes;
    private final Type returnType;
    private final boolean isStatic;

    private NumberedString subsig;

    public SootClass declaringClass() { return declaringClass; }
    public String name() { return name; }
    public List parameterTypes() { return parameterTypes; }
    public Type returnType() { return returnType; }
    public boolean isStatic() { return isStatic; }

    public NumberedString getSubSignature() {
        if( subsig == null ) {
            subsig = Scene.v().getSubSigNumberer().findOrAdd(
                SootMethod.getSubSignature( name, parameterTypes, returnType ));
        }
        return subsig;
    }

    public String getSignature() {
        return SootMethod.getSignature(declaringClass, name, parameterTypes, returnType);
    }

    public Type parameterType(int i) {
        return (Type) parameterTypes.get(i);
    }

    public class ClassResolutionFailedException extends ResolutionFailedException {
        public ClassResolutionFailedException() {
            super("Class "+declaringClass+" doesn't have method "+name+
                    "("+parameterTypes+")"+" : "+returnType+
                    "; failed to resolve in superclasses and interfaces" );
        }
        public String toString() {
            StringBuffer ret = new StringBuffer();
            ret.append(super.toString());
            resolve(ret);
            return ret.toString();
        }
    }

    public SootMethod resolve() {
        return resolve(null);
    }
    
    private SootMethod checkStatic(SootMethod ret) {
        if( ret.isStatic() != isStatic()) {
            throw new ResolutionFailedException( "Resolved "+this+" to "+ret+" which has wrong static-ness" );
        }
        return ret;
    }
    
    private SootMethod resolve(StringBuffer trace) {
        SootClass cl = declaringClass;
        while(true) {
            if(trace != null) trace.append(
                    "Looking in "+cl+" which has methods "+cl.getMethods()+"\n" );
            if( cl.declaresMethod( getSubSignature() ) )
                return checkStatic(cl.getMethod( getSubSignature() ));
            if(Scene.v().allowsPhantomRefs() && cl.isPhantom())
            {
                SootMethod m = new SootMethod(name, parameterTypes, returnType, isStatic()?Modifier.STATIC:0);
                m.setPhantom(true);
                cl.addMethod(m);
                return checkStatic(m);
            }
            if( cl.hasSuperclass() ) cl = cl.getSuperclass();
            else break;
        }
        cl = declaringClass;
        while(true) {
            LinkedList<SootClass> queue = new LinkedList<SootClass>();
            queue.addAll( cl.getInterfaces() );
            while( !queue.isEmpty() ) {
                SootClass iface = queue.removeFirst();
                if(trace != null) trace.append(
                        "Looking in "+iface+" which has methods "+iface.getMethods()+"\n" );
                if( iface.declaresMethod( getSubSignature() ) )
                    return checkStatic(iface.getMethod( getSubSignature() ));
                queue.addAll( iface.getInterfaces() );
            }
            if( cl.hasSuperclass() ) cl = cl.getSuperclass();
            else break;
        }
        
        //when allowing phantom refs we also allow for references to non-existing methods;
        //we simply create the methods on the fly; the method body will throw an appropriate
        //error just in case the code *is* actually reached at runtime
        if(Options.v().allow_phantom_refs()) {
        	
        	// by xiaowei zhou, fix a potential bug in soot, 20120218
//        	SootMethod m = new SootMethod(name, parameterTypes, returnType);
        	SootMethod m = new SootMethod(name, parameterTypes, returnType, isStatic()?Modifier.STATIC:0);
        	
        	JimpleBody body = Jimple.v().newBody(m);
			m.setActiveBody(body);
			
			//exc = new Error
			RefType runtimeExceptionType = RefType.v("java.lang.Error");
			NewExpr newExpr = Jimple.v().newNewExpr(runtimeExceptionType);
			LocalGenerator lg = new LocalGenerator(body);
			Local exceptionLocal = lg.generateLocal(runtimeExceptionType);
			AssignStmt assignStmt = Jimple.v().newAssignStmt(exceptionLocal, newExpr);
			body.getUnits().add(assignStmt);
			
			//exc.<init>(message)
			SootMethodRef cref = runtimeExceptionType.getSootClass().getMethod("<init>", Collections.singletonList(RefType.v("java.lang.String"))).makeRef();
			SpecialInvokeExpr constructorInvokeExpr = Jimple.v().newSpecialInvokeExpr(exceptionLocal, cref, StringConstant.v("Unresolved compilation error: Method "+getSignature()+" does not exist!"));
			InvokeStmt initStmt = Jimple.v().newInvokeStmt(constructorInvokeExpr);
			body.getUnits().insertAfter(initStmt, assignStmt);
			
			//throw exc
			body.getUnits().insertAfter(Jimple.v().newThrowStmt(exceptionLocal), initStmt);

			declaringClass.addMethod(m);
			return m; 
        } else if( trace == null ) {
        	throw new ClassResolutionFailedException();
        }
        return null;
    }
    
    public String toString() {
        return getSignature();
    }
}
