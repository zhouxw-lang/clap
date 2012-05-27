/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-1999 Raja Vallee-Rai
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

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */





package soot;
import soot.util.*;
import java.util.*;

import cn.iscas.tcse.osgiclassloadanalyzer.ClassLoaders;
import cn.iscas.tcse.osgiclassloadanalyzer.ClassNameAndDefCL;

/**
 *   A class that models Java's reference types. RefTypes are parametrized by a class name.
 *   Two RefType are equal iff they are parametrized by the same class name as a String.
 */

public class RefType extends RefLikeType implements Comparable
{
    public RefType( Singletons.Global g ) { className = ""; }
    public static RefType v() { return G.v().soot_RefType(); }

    /** the class name that parametrizes this RefType */
    private String className;
    
	// by xiaowei zhou, add class loader information, 20111127
    private Integer defCL;
	// by xiaowei zhou, initiating class loader only for temporary use, 20111207
    private Integer initCL;

	// by xiaowei zhou, , 20111207
    protected boolean isInScene;
    
    public void setInitCL(Integer initCL) {
		this.initCL = initCL;
	}
	public Integer getInitCL() {
		return initCL;
	}
	public Integer getDefCL() {
		return defCL;
	}
    
	public String getClassName() { return className; }
    private SootClass sootClass;
    private AnySubType anySubType;

    private RefType(String className)
    {
        if( className.startsWith("[") ) throw new RuntimeException("Attempt to create RefType whose name starts with [ --> " + className);
        if( className.indexOf("/") >= 0 ) throw new RuntimeException("Attempt to create RefType containing a / --> " + className);
        if( className.indexOf(";") >= 0 ) throw new RuntimeException("Attempt to create RefType containing a ; --> " + className);
        this.className = className;
    }
    
	// by xiaowei zhou, add class loader information, 20111127
	private RefType(String className, Integer defCL) {
		this(className);
		this.defCL = defCL;
	}
	
	// by xiaowei zhou, , 20111207
	private RefType(String className, Integer initCL, int nouse) {
		this(className);
		this.initCL = initCL;
	}
	
	// by xiaowei zhou, add class loader information, 20111127
	private RefType(ClassNameAndDefCL nameDCL) {
		this(nameDCL.className);
		this.defCL = nameDCL.defCL;
	}

    /** 
     *  Create a RefType for a class. 
     *  @param className The name of the class used to parametrize the created RefType.
     *  @return a RefType for the given class name.
     */
    public static RefType v(String className)
    {
    	// by xiaowei zhou, comment out the original method body, 20111215
/*        if(Scene.v().containsType(className)) {
        	return Scene.v().getRefType( className );
        } else {
	        RefType ret = new RefType(className);
	        Scene.v().addRefType( ret );
	        ret.isInScene = true;
	        return ret;
        }*/
    	// by xiaowei zhou, , 20111215
    	if(className.startsWith("java.")) {
    		return v(className, ClassLoaders.appCLNum);
    	} else {
    		throw new RuntimeException("Trying to call RefType.v(className) without a class loader!");
    	}
    	
    }
    
	// by xiaowei zhou, add class loader information, 20111127
    public static RefType v(String className, Integer defCL)
    {
    	ClassNameAndDefCL namedcl = new ClassNameAndDefCL(className, defCL);
    	
        if(Scene.v().containsType(namedcl)) {
        	return Scene.v().getRefType( namedcl );
        } else {
	        RefType ret = new RefType(namedcl);
	        Scene.v().addRefType( ret );
	        ret.isInScene = true;
	        return ret;
        }
    }
    
	// by xiaowei zhou, , 20111207
    public static RefType v(String className, Integer initCL, int nouse) {
    	Integer defCL = ClassLoaders.initCLToDefCL(initCL, className);
    	if(defCL != null) {
    		return v(className, defCL);
    	} else if(Scene.v().containsType(className, initCL, nouse)){
    		return Scene.v().getRefType(className, initCL, nouse);
    	} else {
    		RefType ret = new RefType(className, initCL, 0);
    		
    		// by xiaowei zhou, , 20120216
	        Scene.v().addRefType( ret );
	        ret.isInScene = true;
    		
    		return ret;
    	}
    }
    
    

    public int compareTo(Object o) throws ClassCastException
    {
        RefType t = (RefType)o;
        return this.toString().compareTo(t.toString());
    }
        
    /** 
     *  Create a RefType for a class. 
     *  @param c A SootClass for which to create a RefType.
     *  @return a RefType for the given SootClass..
     */
    public static RefType v(SootClass c)
    {
    	// by xiaowei zhou, , 20111207
        return v(c.getName(), c.getDefCLNumber());
    }
    
     /** 
      *  Get the SootClass object corresponding to this RefType.
      *  @return the corresponding SootClass
      */    
    public SootClass getSootClass()
    {
        if( sootClass == null ) {
            //System.out.println( "wrning: "+this+" has no sootclass" );
        	// by xiaowei zhou, add class loader information, 20111127
//        	sootClass = SootResolver.v().makeClassRef(className);
			if (defCL != null) {
				sootClass = SootResolver.v().makeClassRef(
						new ClassNameAndDefCL(className, defCL));
			} else {
				sootClass = SootResolver.v().makeClassRefByInitCL(className,
						initCL);
			}
        }
        return sootClass;
    }
    
    public boolean hasSootClass() {
        return sootClass != null;
    }
    
    public void setClassName( String className )
    {
        this.className = className;
    }

     /** 
      *  Set the SootClass object corresponding to this RefType.
      *  @param sootClass The SootClass corresponding to this RefType.
      */    
    public void setSootClass( SootClass sootClass )
    {
        this.sootClass = sootClass;
    }

    /** 
     *  2 RefTypes are considered equal if they are parametrized by the same class name String.
     *  @param t an object to test for equality.
     *  @ return true if t is a RefType parametrized by the same name as this.
     */
	// by xiaowei zhou, , 20111127
//    public boolean equals(Object t)
//    {
//        return ((t instanceof RefType) && className.equals(((RefType) t).className));
//    }
    
    

    public String toString()
    {
        return className;
    }

	// by xiaowei zhou, , 20111127
//    public int hashCode()
//    {
//        return className.hashCode();
//    }

    
	// by xiaowei zhou, , 20111127
    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((className == null) ? 0 : className.hashCode());
		result = prime * result + ((defCL == null) ? 0 : defCL.hashCode());
		return result;
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RefType other = (RefType) obj;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		if (defCL == null) {
			if (other.defCL != null)
				return false;
		} else if (!defCL.equals(other.defCL))
			return false;
		return true;
	}
	
	public void apply(Switch sw)
    {
        ((TypeSwitch) sw).caseRefType(this);
    }


    /** Returns the least common superclass of this type and other. */
    public Type merge(Type other, Scene cm)
    {
        if(other.equals(UnknownType.v()) || this.equals(other))
            return this;
        
        if(! (other instanceof RefType))
            throw new RuntimeException("illegal type merge: "
                                       + this + " and " + other);


        {
            // Return least common superclass
            
        	// by xiaowei zhou, , 20111207
            SootClass thisClass = cm.getSootClass((this).className, this.defCL);
            SootClass otherClass = cm.getSootClass(((RefType) other).className, ((RefType) other).defCL);
            
        	// by xiaowei zhou, , 20111213
//            SootClass javalangObject = cm.getSootClass("java.lang.Object");
            SootClass javalangObject = cm.getSootClass("java.lang.Object", ClassLoaders.appCLNum);

            LinkedList<SootClass> thisHierarchy = new LinkedList<SootClass>();
            LinkedList<SootClass> otherHierarchy = new LinkedList<SootClass>();

            // Build thisHierarchy
            {
                SootClass SootClass = thisClass;

                for(;;)
                {
                    thisHierarchy.addFirst(SootClass);

                    if(SootClass == javalangObject)
                        break;

                    SootClass = SootClass.getSuperclass();
                }
            }

            // Build otherHierarchy
            {
                SootClass SootClass = otherClass;

                for(;;)
                {
                    otherHierarchy.addFirst(SootClass);

                    if(SootClass == javalangObject)
                        break;

                    SootClass = SootClass.getSuperclass();
                }
            }

            // Find least common superclass
            {
                SootClass commonClass = null;

                while(!otherHierarchy.isEmpty() && !thisHierarchy.isEmpty() &&
                    otherHierarchy.getFirst() == thisHierarchy.getFirst())
                {
                    commonClass = otherHierarchy.removeFirst();
                    thisHierarchy.removeFirst();
                }

            	// by xiaowei zhou, , 20111207
				return RefType.v(commonClass.getName(),
						commonClass.getDefCLNumber());
            }
        }
        
    }

    public Type getArrayElementType() {
	if( className.equals( "java.lang.Object" )
	    || className.equals( "java.io.Serializable" )
	    || className.equals( "java.lang.Cloneable" ) ) {
		// by xiaowei zhou, , 20111213
	    return RefType.v( "java.lang.Object", ClassLoaders.appCLNum );
	}
	throw new RuntimeException( "Attempt to get array base type of a non-array" );

    }

    public AnySubType getAnySubType() { return anySubType; }
    public void setAnySubType( AnySubType anySubType ) {
        this.anySubType = anySubType;
    }
}
