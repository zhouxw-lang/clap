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

package soot.jimple.spark.pag;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import soot.Context;
import soot.PhaseOptions;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.options.CGOptions;

/** Represents an allocation site node (Blue) in the pointer assignment graph.
 * @author Ondrej Lhotak
 */
public class AllocNode extends Node implements Context {
    /** Returns the new expression of this allocation site. */
    public Object getNewExpr() { return newExpr; }
    /** Returns all field ref nodes having this node as their base. */
    public Collection getAllFieldRefs() { 
        if( fields == null ) return Collections.EMPTY_LIST;
        return fields.values();
    }
    /** Returns the field ref node having this node as its base,
     * and field as its field; null if nonexistent. */
    public AllocDotField dot( SparkField field ) 
    { return fields == null ? null : (AllocDotField) fields.get( field ); }
    
 // by xiaowei zhou, , 20120415
//    public String toString() {
//	return "AllocNode "+getNumber()+" "+newExpr+" in method "+method;
//    }
	public String toString() {
		if (linenumber != 0) {
			return "AllocNode " + getNumber() + " " + newExpr + " at line \""
					+ linenumber + "\"";
		} else {
			return "AllocNode " + getNumber() + " " + newExpr;
		}
	}
    
	// by xiaowei zhou, , 20120415
    public int getLinenumber() {
		return linenumber;
	}
	public void setLinenumber(int linenumber) {
		this.linenumber = linenumber;
	}
	public AllocNode setLinenumberRe(int linenumber) {
		this.linenumber = linenumber;
		return this;
	}

    /* End of public methods. */

    AllocNode( PAG pag, Object newExpr, Type t, SootMethod m ) {
	super( pag, t );
        this.method = m;
        if( t instanceof RefType ) {
            RefType rt = (RefType) t;
            if( rt.getSootClass().isAbstract()) {
				boolean usesReflectionLog = new CGOptions(PhaseOptions.v().getPhaseOptions("cg")).reflection_log()!=null;
				if (!usesReflectionLog) {
				    throw new RuntimeException( "Attempt to create allocnode with abstract type "+t );
				}
			}
        }
	this.newExpr = newExpr;
        if( newExpr instanceof ContextVarNode ) throw new RuntimeException();
        pag.getAllocNodeNumberer().add( this );
    }
    /** Registers a AllocDotField as having this node as its base. */
    void addField( AllocDotField adf, SparkField field ) {
	if( fields == null ) fields = new HashMap();
        fields.put( field, adf );
    }

    public Set getFields() {
        if( fields == null ) return Collections.EMPTY_SET;
        return new HashSet( fields.values() );
    }

    /* End of package methods. */

    protected Object newExpr;
    protected Map fields;
    
	// by xiaowei zhou, the source code line number of the allocation node, 20120415
    private int linenumber = 0;

	private SootMethod method;
    public SootMethod getMethod() { return method; }
}

