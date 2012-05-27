/* Soot - a J*va Optimization Framework
 * Copyright (C) 2002 Ondrej Lhotak
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

package soot.jimple.spark.builder;
import cn.iscas.tcse.osgiclassloadanalyzer.CodeUtil;
import cn.iscas.tcse.osgiclassloadanalyzer.TypeUtil;
import soot.AbstractSootFieldRef.FieldResolutionFailedException;
import soot.jimple.spark.pag.*;
import soot.jimple.*;
import soot.*;
import soot.toolkits.scalar.Pair;
import soot.shimple.*;

/** Class implementing builder parameters (this decides
 * what kinds of nodes should be built for each kind of Soot value).
 * @author Ondrej Lhotak
 */
public class MethodNodeFactory extends AbstractShimpleValueSwitch {
    public MethodNodeFactory( PAG pag, MethodPAG mpag ) {
	this.pag = pag;
	this.mpag = mpag;
        setCurrentMethod( mpag.getMethod() );
    }
    /** Sets the method for which a graph is currently being built. */
    private void setCurrentMethod( SootMethod m ) {
	method = m;
        if( !m.isStatic() ) {
            SootClass c = m.getDeclaringClass();
            if( c == null ) {
                throw new RuntimeException( "Method "+m+" has no declaring class" );
            }
            caseThis();
        }
        for( int i = 0; i < m.getParameterCount(); i++ ) {
            if( m.getParameterType(i) instanceof RefLikeType ) {
                caseParm( i );
            }
        }
        Type retType = m.getReturnType();
        if( retType instanceof RefLikeType ) {
            caseRet();
        }
    }

    public Node getNode( Value v ) {
        v.apply( this );
        return getNode();
    }
    /** Adds the edges required for this statement to the graph. */
    final public void handleStmt( Stmt s ) {
    	// by xiaowei zhou, , 20120415
    	curStmt = s;
    	
	if( s.containsInvokeExpr() ) {
	    return;
	}
	s.apply( new AbstractStmtSwitch() {
	    final public void caseAssignStmt(AssignStmt as) {
                Value l = as.getLeftOp();
                Value r = as.getRightOp();
		if( !( l.getType() instanceof RefLikeType ) ) return;
		l.apply( MethodNodeFactory.this );
		Node dest = getNode();
		r.apply( MethodNodeFactory.this );
		Node src = getNode();
                if( l instanceof InstanceFieldRef ) {
                    ((InstanceFieldRef) l).getBase().apply( MethodNodeFactory.this );
            		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//                    pag.addDereference( (VarNode) getNode() );
                    Node node = getNode();
                    if(node != null) {
                    	pag.addDereference( (VarNode) node );
                    }
                }
                if( r instanceof InstanceFieldRef ) {
                    ((InstanceFieldRef) r).getBase().apply( MethodNodeFactory.this );
            		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//                    pag.addDereference( (VarNode) getNode() );
                    Node node = getNode();
                    if(node != null) {
                    	pag.addDereference( (VarNode) node );
                    }
                }
                if( r instanceof StaticFieldRef ) {
                	StaticFieldRef sfr = (StaticFieldRef) r;
					SootFieldRef s = sfr.getFieldRef();
                	if (pag.getOpts().empties_as_allocs()) {
                	    if (s.declaringClass().getName().equals("java.util.Collections")) {
                                if (s.name().equals("EMPTY_SET")) {
                                    src = pag.makeAllocNode( RefType.v("java.util.HashSet"), 
                                                                  RefType.v("java.util.HashSet"), method );
                                } else if (s.name().equals("EMPTY_MAP")) {
                                    src = pag.makeAllocNode( RefType.v("java.util.HashMap"), 
                                                                  RefType.v("java.util.HashMap"), method );
                                } else if (s.name().equals("EMPTY_LIST")) {
                                    src = pag.makeAllocNode( RefType.v("java.util.LinkedList"), 
                                                                  RefType.v("java.util.LinkedList"), method );
                                } 
                        } else if (s.declaringClass().getName().equals("java.util.Hashtable")) {
                            if (s.name().equals("emptyIterator")) {
                                src = pag.makeAllocNode( RefType.v("java.util.Hashtable$EmptyIterator"), 
                                                              RefType.v("java.util.Hashtable$EmptyIterator"), method );
                            } else if (s.name().equals("emptyEnumerator")) {
                                src = pag.makeAllocNode( RefType.v("java.util.Hashtable$EmptyEnumerator"), 
                                        RefType.v("java.util.Hashtable$EmptyEnumerator"), method );
                            } 
                        }
                	}
                }
                
        		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//		mpag.addInternalEdge( src, dest );
				if (src != null && dest != null) {
					// by xiaowei zhou, , 20120417
//					mpag.addInternalEdge(src, dest);
					mpag.addInternalEdge(src, dest, curStmt);
				}
	    }
	    final public void caseReturnStmt(ReturnStmt rs) {
		if( !( rs.getOp().getType() instanceof RefLikeType ) ) return;
		rs.getOp().apply( MethodNodeFactory.this );
                Node retNode = getNode();
        		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//                mpag.addInternalEdge( retNode, caseRet() );
                if(retNode!=null) {
                	// by xiaowei zhou, , 20120417
//                	mpag.addInternalEdge( retNode, caseRet() );
                	mpag.addInternalEdge( retNode, caseRet(), curStmt );
                }
	    }
	    final public void caseIdentityStmt(IdentityStmt is) {
		if( !( is.getLeftOp().getType() instanceof RefLikeType ) ) return;
		is.getLeftOp().apply( MethodNodeFactory.this );
		Node dest = getNode();
		is.getRightOp().apply( MethodNodeFactory.this );
		Node src = getNode();
		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//		mpag.addInternalEdge( src, dest );
				if (src != null && dest != null) {
					// by xiaowei zhou, , 20120417
//					mpag.addInternalEdge(src, dest);
					mpag.addInternalEdge(src, dest, curStmt);
				}
	    }
	    final public void caseThrowStmt(ThrowStmt ts) {
		ts.getOp().apply( MethodNodeFactory.this );
		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//		mpag.addOutEdge( getNode(), pag.nodeFactory().caseThrow() );
		Node node = getNode();
		if(node!=null) {
			// by xiaowei zhou, , 20120417
//			mpag.addOutEdge( node, pag.nodeFactory().caseThrow() );
			mpag.addOutEdge( node, pag.nodeFactory().caseThrow(), curStmt );
		}
	    }
	} );
    }
    final public Node getNode() {
	return (Node) getResult();
    }
    final public Node caseThis() {
	VarNode ret = pag.makeLocalVarNode(
		    new Pair( method, PointsToAnalysis.THIS_NODE ),
		    method.getDeclaringClass().getType(), method );
        ret.setInterProcTarget();
        return ret;
    }

    final public Node caseParm( int index ) {
        VarNode ret = pag.makeLocalVarNode(
                    new Pair( method, new Integer( index ) ),
                    method.getParameterType( index ), method );
        ret.setInterProcTarget();
        return ret;
    }

    final public void casePhiExpr(PhiExpr e) {
        Pair phiPair = new Pair( e, PointsToAnalysis.PHI_NODE );
        Node phiNode = pag.makeLocalVarNode( phiPair, e.getType(), method );
        for (Value op : e.getValues()) {
            op.apply( MethodNodeFactory.this );
            Node opNode = getNode();
    		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//            mpag.addInternalEdge( opNode, phiNode );
            if(opNode != null) {
            	// by xiaowei zhou, , 20120417
//            	mpag.addInternalEdge( opNode, phiNode );
            	mpag.addInternalEdge( opNode, phiNode, curStmt );
            }
        }
        setResult( phiNode );
    }
    
    final public Node caseRet() {
        VarNode ret = pag.makeLocalVarNode(
                    Parm.v( method, PointsToAnalysis.RETURN_NODE ),
                    method.getReturnType(), method );
        ret.setInterProcSource();
        return ret;
    }
    final public Node caseArray( VarNode base ) {
	return pag.makeFieldRefNode( base, ArrayElement.v() );
    }
    /* End of public methods. */
    /* End of package methods. */

    // OK, these ones are public, but they really shouldn't be; it's just
    // that Java requires them to be, because they override those other
    // public methods.
    final public void caseArrayRef( ArrayRef ar ) {
    	caseLocal( (Local) ar.getBase() );
    	
		// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//    	setResult(caseArray((VarNode) getNode()));
		Node node = getNode();
		if (node != null) {
			setResult(caseArray((VarNode) node));
		}
    }
    final public void caseCastExpr( CastExpr ce ) {
	Pair castPair = new Pair( ce, PointsToAnalysis.CAST_NODE );
	ce.getOp().apply( this );
	Node opNode = getNode();
	
	// by xiaowei zhou, getNode may return null for the code changed by me, 20120310
//	Node castNode = pag.makeLocalVarNode( castPair, ce.getCastType(), method );
//	mpag.addInternalEdge( opNode, castNode );
//	setResult( castNode );
		if (opNode != null) {
			Node castNode = pag.makeLocalVarNode(castPair, ce.getCastType(),
					method);
			// by xiaowei zhou, , 20120417
//			mpag.addInternalEdge(opNode, castNode);
			mpag.addInternalEdge(opNode, castNode, curStmt);
			setResult(castNode);
		}
    }
    final public void caseCaughtExceptionRef( CaughtExceptionRef cer ) {
	setResult( pag.nodeFactory().caseThrow() );
    }
    final public void caseInstanceFieldRef( InstanceFieldRef ifr ) {
		// by xiaowei zhou, the method "getField" may fail due to "loading constraint violation", add try catch, 20120309
		try {
			if (pag.getOpts().field_based() || pag.getOpts().vta()) {
				setResult(pag.makeGlobalVarNode(ifr.getField(), ifr.getField()
						.getType()));
			} else {
				setResult(pag.makeLocalFieldRefNode(ifr.getBase(), ifr
						.getBase().getType(), ifr.getField(), method));
			}
		} catch (FieldResolutionFailedException e) {
			setResult(null);
		}
    }
    final public void caseLocal( Local l ) {
	setResult( pag.makeLocalVarNode( l,  l.getType(), method ) );
    }
    final public void caseNewArrayExpr( NewArrayExpr nae ) {
    	// by xiaowei zhou, , 20120415
//        setResult( pag.makeAllocNode( nae, nae.getType(), method ) );
        setResult( pag.makeAllocNode( nae, nae.getType(), method ).setLinenumberRe(CodeUtil.getlineNumber(curStmt)) );
    }
    private boolean isStringBuffer(Type t) {
        if(!(t instanceof RefType)) return false;
        RefType rt = (RefType) t;
        String s = rt.toString();
        if(s.equals("java.lang.StringBuffer")) return true;
        if(s.equals("java.lang.StringBuilder")) return true;
        return false;
    }
    final public void caseNewExpr( NewExpr ne ) {
    	// by xiaowei zhou, only propagate allocation sites with carry classes as its types, 20120310
//        if( pag.getOpts().merge_stringbuffer() 
//        && isStringBuffer(ne.getType())) {
//            setResult( pag.makeAllocNode( ne.getType(), ne.getType(), null ) );
//        } else {
//            setResult( pag.makeAllocNode( ne, ne.getType(), method ) );
//        }
		if (TypeUtil.isCarryClass(ne.getType())) {
			if (pag.getOpts().merge_stringbuffer()
					&& isStringBuffer(ne.getType())) {
				// by xiaowei zhou, , 20120415
//				setResult(pag.makeAllocNode(ne.getType(), ne.getType(), null));
				setResult(pag.makeAllocNode(ne.getType(), ne.getType(), null).setLinenumberRe(CodeUtil.getlineNumber(curStmt)));
			} else {
				// by xiaowei zhou, , 20120415
//				setResult(pag.makeAllocNode(ne, ne.getType(), method));
				setResult(pag.makeAllocNode(ne, ne.getType(), method).setLinenumberRe(CodeUtil.getlineNumber(curStmt)));
			}
		} else {
			setResult(null);
		}
    }
    final public void caseNewMultiArrayExpr( NewMultiArrayExpr nmae ) {
        ArrayType type = (ArrayType) nmae.getType();
    	// by xiaowei zhou, , 20120415
//        AllocNode prevAn = pag.makeAllocNode(
//            new Pair( nmae, new Integer( type.numDimensions ) ), type, method );
        AllocNode prevAn = pag.makeAllocNode(
                new Pair( nmae, new Integer( type.numDimensions ) ), type, method ).setLinenumberRe(CodeUtil.getlineNumber(curStmt));
        
        VarNode prevVn = pag.makeLocalVarNode( prevAn, prevAn.getType(), method );
    	// by xiaowei zhou, , 20120417
//        mpag.addInternalEdge( prevAn, prevVn );
        mpag.addInternalEdge( prevAn, prevVn, curStmt );
        setResult( prevAn );
        while( true ) {
            Type t = type.getElementType();
            if( !( t instanceof ArrayType ) ) break;
            type = (ArrayType) t;
        	// by xiaowei zhou, , 20120415
//            AllocNode an = pag.makeAllocNode(
//                new Pair( nmae, new Integer( type.numDimensions ) ), type, method );
            AllocNode an = pag.makeAllocNode(
                    new Pair( nmae, new Integer( type.numDimensions ) ), type, method ).setLinenumberRe(CodeUtil.getlineNumber(curStmt));
            
            VarNode vn = pag.makeLocalVarNode( an, an.getType(), method );
        	// by xiaowei zhou, , 20120417
//            mpag.addInternalEdge( an, vn );
//            mpag.addInternalEdge( vn, pag.makeFieldRefNode( prevVn, ArrayElement.v() ) );
            mpag.addInternalEdge( an, vn, curStmt );
            mpag.addInternalEdge( vn, pag.makeFieldRefNode( prevVn, ArrayElement.v() ), curStmt );
            prevAn = an;
            prevVn = vn;
        }
    }
    final public void caseParameterRef( ParameterRef pr ) {
	setResult( caseParm( pr.getIndex() ) );
    }
    final public void caseStaticFieldRef( StaticFieldRef sfr ) {
	setResult( pag.makeGlobalVarNode( 
		    sfr.getField(), 
		    sfr.getField().getType() ) );
    }
    final public void caseStringConstant( StringConstant sc ) {
        AllocNode stringConstant;
        if( pag.getOpts().string_constants()
        		// by xiaowei zhou, , 20111230
//        || Scene.v().containClass(sc.value)
        || Scene.v().containClasses(sc.value)
        || ( sc.value.length() > 0 && sc.value.charAt(0) == '[' ) ) {
            stringConstant = pag.makeStringConstantNode( sc.value );
        } else {
            stringConstant = pag.makeAllocNode(
                PointsToAnalysis.STRING_NODE,
                RefType.v( "java.lang.String" ), null );
        }
        VarNode stringConstantLocal = pag.makeGlobalVarNode(
            stringConstant,
            RefType.v( "java.lang.String" ) );
    	// by xiaowei zhou, , 20120417
//        pag.addEdge( stringConstant, stringConstantLocal );
        pag.addEdge( stringConstant, stringConstantLocal, CodeUtil.getlineNumber(curStmt), method.getDeclaringClass() );
        setResult( stringConstantLocal );
    }
    final public void caseThisRef( ThisRef tr ) {
	setResult( caseThis() );
    }
    final public void caseNullConstant( NullConstant nr ) {
	setResult( null );
    }
    final public void caseClassConstant( ClassConstant cc ) {
        AllocNode classConstant = pag.makeClassConstantNode(cc);
        VarNode classConstantLocal = pag.makeGlobalVarNode(
            classConstant,
            RefType.v( "java.lang.Class" ) );
    	// by xiaowei zhou, , 20120417
//        pag.addEdge(classConstant, classConstantLocal);
        pag.addEdge(classConstant, classConstantLocal, CodeUtil.getlineNumber(curStmt), method.getDeclaringClass());
        setResult(classConstantLocal);
    }
    final public void defaultCase( Object v ) {
	throw new RuntimeException( "failed to handle "+v );
    }
    protected PAG pag;
    protected MethodPAG mpag;
    protected SootMethod method;
    
	// by xiaowei zhou, current statement to handle, for retrieving the line number, 20120415
    protected Stmt curStmt;
}
