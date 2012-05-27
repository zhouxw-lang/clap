/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Ondrej Lhotak
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

package soot.jimple.spark.pag;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import soot.ArrayType;
import soot.Body;
import soot.Context;
import soot.EntryPoints;
import soot.G;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.VoidType;
import soot.jimple.Stmt;
import soot.jimple.spark.builder.MethodNodeFactory;
import soot.util.NumberedString;
import soot.util.SingletonList;
import soot.util.queue.ChunkedQueue;
import soot.util.queue.QueueReader;
import cn.iscas.tcse.osgiclassloadanalyzer.CodeUtil;
import cn.iscas.tcse.osgiclassloadanalyzer.ClapPair;


/** Part of a pointer assignment graph for a single method.
 * @author Ondrej Lhotak
 */
public final class MethodPAG {
    private PAG pag;
    public PAG pag() { return pag; }

    protected MethodPAG( PAG pag, SootMethod m ) {
        this.pag = pag;
        this.method = m;
        this.nodeFactory = new MethodNodeFactory( pag, this );
    }

    private Set<Context> addedContexts;

    /** Adds this method to the main PAG, with all VarNodes parameterized by
     * varNodeParameter. */
    public void addToPAG( Context varNodeParameter ) {
    	// by xiaowei zhou, may be a bug of soot, 20120310
    	// by xiaowei zhou, for soot.jimple.spark.builder.ContextInsensitiveBuilder.handleClass(SootClass) will not build phantom methods, 20120310
//        if( !hasBeenBuilt ) throw new RuntimeException();
        if( !hasBeenBuilt ) {
        	build();
        }
        
        if( varNodeParameter == null ) {
            if( hasBeenAdded ) return;
            hasBeenAdded = true;
        } else {
            if( addedContexts == null ) addedContexts = new HashSet<Context>();
            if( !addedContexts.add( varNodeParameter ) ) return;
        }
        QueueReader reader = (QueueReader) internalReader.clone();
        while(reader.hasNext()) {
        	// by xiaowei zhou, , 20120417
//            Node src = (Node) reader.next();
//            src = parameterize( src, varNodeParameter );
//            Node dst = (Node) reader.next();
//            dst = parameterize( dst, varNodeParameter );
//            pag.addEdge( src, dst );
            Node src = (Node) reader.next();
            Node dst = (Node) reader.next();
            Integer wraplineNum = internalLineNums.get(new ClapPair(src, dst));
            int lineNum = wraplineNum==null?0:wraplineNum;
            src = parameterize( src, varNodeParameter );
            dst = parameterize( dst, varNodeParameter );
            pag.addEdge( src, dst, lineNum, method.getDeclaringClass() );
        }
        reader = (QueueReader) inReader.clone();
        while(reader.hasNext()) {
            Node src = (Node) reader.next();
            Node dst = (Node) reader.next();
        	// by xiaowei zhou, , 20120417
            Integer wraplineNum = internalLineNums.get(new ClapPair(src, dst));
            int lineNum = wraplineNum==null?0:wraplineNum;
            
            dst = parameterize( dst, varNodeParameter );
        	// by xiaowei zhou, , 20120417
//            pag.addEdge( src, dst );
            pag.addEdge( src, dst, lineNum, method.getDeclaringClass() );
        }
        reader = (QueueReader) outReader.clone();
        while(reader.hasNext()) {
        	// by xiaowei zhou, , 20120417
//            Node src = (Node) reader.next();
//            src = parameterize( src, varNodeParameter );
//            Node dst = (Node) reader.next();
//            pag.addEdge( src, dst );
          Node src = (Node) reader.next();
          Node dst = (Node) reader.next();
          Integer wraplineNum = internalLineNums.get(new ClapPair(src, dst));
          int lineNum = wraplineNum==null?0:wraplineNum;
          src = parameterize( src, varNodeParameter );
          pag.addEdge( src, dst, lineNum, method.getDeclaringClass() );
        }
    }
    
	// by xiaowei zhou, add a parameter 'stmt', for line number, 20120416
//    public void addInternalEdge( Node src, Node dst ) {
    public void addInternalEdge( Node src, Node dst, Stmt stmt ) {
        if( src == null ) return;
        internalEdges.add( src );
        internalEdges.add( dst );
        
     // by xiaowei zhou, add to data structure representing source code line numbers, 20120416
        int lineNum = CodeUtil.getlineNumber(stmt);
		if (pag.getOpts().line_number_in_pag()) {
			if (lineNum != 0) {
				internalLineNums.put(new ClapPair(src, dst), lineNum);
			}
		}
        
        if (hasBeenAdded) {
        	// by xiaowei zhou, , 20120417
//            pag.addEdge(src, dst);
            pag.addEdge(src, dst, lineNum, method.getDeclaringClass());
        }
    }
    
	// by xiaowei zhou, add a parameter 'stmt', for line number, 20120416
//    public void addInEdge( Node src, Node dst ) {
    public void addInEdge( Node src, Node dst, Stmt stmt ) {
        if( src == null ) return;
        inEdges.add( src );
        inEdges.add( dst );
        
        // by xiaowei zhou, add to data structure representing source code line numbers, 20120416
        int lineNum = CodeUtil.getlineNumber(stmt);
		if (pag.getOpts().line_number_in_pag()) {
			if (lineNum != 0) {
				inLineNums.put(new ClapPair(src, dst), lineNum);
			}
		}
        
        if (hasBeenAdded) {
        	// by xiaowei zhou, , 20120417
//            pag.addEdge(src, dst);
            pag.addEdge(src, dst, lineNum, method.getDeclaringClass());
        }
    }
	// by xiaowei zhou, add a parameter 'stmt', for line number, 20120416
//    public void addOutEdge( Node src, Node dst ) {
    public void addOutEdge( Node src, Node dst, Stmt stmt ) {
        if( src == null ) return;
        outEdges.add( src );
        outEdges.add( dst );
        
        // by xiaowei zhou, add to data structure representing source code line numbers, 20120416
        int lineNum = CodeUtil.getlineNumber(stmt);
        if(pag.getOpts().line_number_in_pag()) {
			if (lineNum != 0) {
				outLineNums.put(new ClapPair(src, dst), lineNum);
			}
        }
        
        if (hasBeenAdded) {
        	// by xiaowei zhou, , 20120417
//            pag.addEdge(src, dst);
            pag.addEdge(src, dst, lineNum, method.getDeclaringClass());
        }        
    }
    private final ChunkedQueue internalEdges = new ChunkedQueue();
    private final ChunkedQueue inEdges = new ChunkedQueue();
    private final ChunkedQueue outEdges = new ChunkedQueue();
    private final QueueReader internalReader = internalEdges.reader();
    private final QueueReader inReader = inEdges.reader();
    private final QueueReader outReader = outEdges.reader();
    
	// by xiaowei zhou, add data structure to represent source code line numbers, 20120416
    private final Map<ClapPair, Integer> internalLineNums = new HashMap<ClapPair, Integer>();
    private final Map<ClapPair, Integer> inLineNums = new HashMap<ClapPair, Integer>();
    private final Map<ClapPair, Integer> outLineNums = new HashMap<ClapPair, Integer>();

    SootMethod method;
    public SootMethod getMethod() { return method; }
    protected MethodNodeFactory nodeFactory;
    public MethodNodeFactory nodeFactory() { return nodeFactory; }

    public static MethodPAG v( PAG pag, SootMethod m ) {
        MethodPAG ret = G.v().MethodPAG_methodToPag.get( m );
        if( ret == null ) { 
            ret = new MethodPAG( pag, m );
            G.v().MethodPAG_methodToPag.put( m, ret );
        }
        return ret;
    }

    public void build() {
        if( hasBeenBuilt ) return;
        hasBeenBuilt = true;
        if( method.isNative() ) {
            if( pag().getOpts().simulate_natives() ) {
                buildNative();
            }
        } else {
            if( method.isConcrete() && !method.isPhantom() ) {
                buildNormal();
            }
        }
        addMiscEdges();
    }

    protected VarNode parameterize( LocalVarNode vn, Context varNodeParameter ) {
        SootMethod m = vn.getMethod();
        if( m != method && m != null ) throw new RuntimeException( "VarNode "+vn+" with method "+m+" parameterized in method "+method );
        //System.out.println( "parameterizing "+vn+" with "+varNodeParameter );
        return pag().makeContextVarNode( vn, varNodeParameter );
    }
    protected FieldRefNode parameterize( FieldRefNode frn, Context varNodeParameter ) {
        return pag().makeFieldRefNode(
                (VarNode) parameterize( frn.getBase(), varNodeParameter ),
                frn.getField() );
    }
    public Node parameterize( Node n, Context varNodeParameter ) {
        if( varNodeParameter == null ) return n;
        if( n instanceof LocalVarNode ) 
            return parameterize( (LocalVarNode) n, varNodeParameter);
        if( n instanceof FieldRefNode )
            return parameterize( (FieldRefNode) n, varNodeParameter);
        return n;
    }
    protected boolean hasBeenAdded = false;
    protected boolean hasBeenBuilt = false;

    protected void buildNormal() {
        Body b = method.retrieveActiveBody();
        Iterator unitsIt = b.getUnits().iterator();
        while( unitsIt.hasNext() )
        {
            Stmt s = (Stmt) unitsIt.next();
            nodeFactory.handleStmt( s );
        }
    }
    protected void buildNative() {
        ValNode thisNode = null;
        ValNode retNode = null; 
        if( !method.isStatic() ) { 
	    thisNode = (ValNode) nodeFactory.caseThis();
        }
        if( method.getReturnType() instanceof RefLikeType ) {
	    retNode = (ValNode) nodeFactory.caseRet();
	}
        ValNode[] args = new ValNode[ method.getParameterCount() ];
        for( int i = 0; i < method.getParameterCount(); i++ ) {
            if( !( method.getParameterType(i) instanceof RefLikeType ) ) continue;
	    args[i] = (ValNode) nodeFactory.caseParm(i);
        }
        pag.nativeMethodDriver.process( method, thisNode, retNode, args );
    }

    protected void addMiscEdges() {
        // Add node for parameter (String[]) in main method
        if( method.getSubSignature().equals( SootMethod.getSubSignature( "main", new SingletonList( ArrayType.v(RefType.v("java.lang.String"), 1) ), VoidType.v() ) ) ) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge( pag().nodeFactory().caseArgv(), nodeFactory.caseParm(0) );
            addInEdge( pag().nodeFactory().caseArgv(), nodeFactory.caseParm(0), null );
        } else

        if( method.getSignature().equals(
                    "<java.lang.Thread: void <init>(java.lang.ThreadGroup,java.lang.String)>" ) ) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge( pag().nodeFactory().caseMainThread(), nodeFactory.caseThis() );
//            addInEdge( pag().nodeFactory().caseMainThreadGroup(), nodeFactory.caseParm( 0 ) );
            addInEdge( pag().nodeFactory().caseMainThread(), nodeFactory.caseThis(), null );
            addInEdge( pag().nodeFactory().caseMainThreadGroup(), nodeFactory.caseParm( 0 ), null );
        } else

        if (method.getSignature().equals(
                "<java.lang.ref.Finalizer: void <init>(java.lang.Object)>")) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge( nodeFactory.caseThis(), pag().nodeFactory().caseFinalizeQueue());
            addInEdge( nodeFactory.caseThis(), pag().nodeFactory().caseFinalizeQueue(), null);
        } else
        	
        if (method.getSignature().equals(
                "<java.lang.ref.Finalizer: void runFinalizer()>")) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge(pag.nodeFactory().caseFinalizeQueue(), nodeFactory.caseThis());
            addInEdge(pag.nodeFactory().caseFinalizeQueue(), nodeFactory.caseThis(), null);
        } else

        if (method.getSignature().equals(
                "<java.lang.ref.Finalizer: void access$100(java.lang.Object)>")) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge(pag.nodeFactory().caseFinalizeQueue(), nodeFactory.caseParm(0));
            addInEdge(pag.nodeFactory().caseFinalizeQueue(), nodeFactory.caseParm(0), null);
        } else

        if (method.getSignature().equals(
                "<java.lang.ClassLoader: void <init>()>")) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge(pag.nodeFactory().caseDefaultClassLoader(), nodeFactory.caseThis());
            addInEdge(pag.nodeFactory().caseDefaultClassLoader(), nodeFactory.caseThis(), null);
        } else

        if (method.getSignature().equals("<java.lang.Thread: void exit()>")) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge(pag.nodeFactory().caseMainThread(), nodeFactory.caseThis());
            addInEdge(pag.nodeFactory().caseMainThread(), nodeFactory.caseThis(), null);
        } else

        if (method
                .getSignature()
                .equals(
                        "<java.security.PrivilegedActionException: void <init>(java.lang.Exception)>")) {
        	// by xiaowei zhou, use null for stmt parameter, 20120416
//            addInEdge(pag.nodeFactory().caseThrow(), nodeFactory.caseParm(0));
//            addInEdge(pag.nodeFactory().casePrivilegedActionException(), nodeFactory.caseThis());
            addInEdge(pag.nodeFactory().caseThrow(), nodeFactory.caseParm(0), null);
            addInEdge(pag.nodeFactory().casePrivilegedActionException(), nodeFactory.caseThis(), null);
        }

        if (method.getNumberedSubSignature().equals(sigCanonicalize)) {
            SootClass cl = method.getDeclaringClass();
            while (true) {
                if (cl.equals(Scene.v().getSootClass("java.io.FileSystem"))) {
                	// by xiaowei zhou, use null for stmt parameter, 20120416
//                    addInEdge(pag.nodeFactory().caseCanonicalPath(), nodeFactory.caseRet());
                    addInEdge(pag.nodeFactory().caseCanonicalPath(), nodeFactory.caseRet(), null);
                }
                if (!cl.hasSuperclass())
                    break;
                cl = cl.getSuperclass();
            }
        }

        boolean isImplicit = false;
        for (SootMethod implicitMethod : EntryPoints.v().implicit()) {
         if (implicitMethod.getNumberedSubSignature().equals(
		    method.getNumberedSubSignature())) {
        	 isImplicit = true;
        	 break;
         }
      }
        if (isImplicit) {
            SootClass c = method.getDeclaringClass();
            outer: do {
                while (!c.getName().equals("java.lang.ClassLoader")) {
                    if (!c.hasSuperclass()) {
                        break outer;
                    }
                    c = c.getSuperclass();
                }
                if (method.getName().equals("<init>"))
                    continue;
             // by xiaowei zhou, use null for stmt parameter, 20120416
//                addInEdge(pag().nodeFactory().caseDefaultClassLoader(),
//                        nodeFactory.caseThis());
//                addInEdge(pag().nodeFactory().caseMainClassNameString(),
//                        nodeFactory.caseParm(0));
                addInEdge(pag().nodeFactory().caseDefaultClassLoader(),
                        nodeFactory.caseThis(), null);
                addInEdge(pag().nodeFactory().caseMainClassNameString(),
                        nodeFactory.caseParm(0), null);
            } while (false);
        }
    }


    protected final NumberedString sigCanonicalize = Scene.v().getSubSigNumberer().
    findOrAdd("java.lang.String canonicalize(java.lang.String)");
}

