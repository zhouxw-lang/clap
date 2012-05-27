/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-1999 Raja Vallee-Rai
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

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */


package soot;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.iscas.tcse.osgiclassloadanalyzer.ClassLoaders;
import cn.iscas.tcse.osgiclassloadanalyzer.ClassNameAndDefCL;
import cn.iscas.tcse.osgiclassloadanalyzer.pointstoanalysis.forsoot.ISootClassPath;

import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.ContextSensitiveCallGraph;
import soot.jimple.toolkits.callgraph.ReachableMethods;
import soot.jimple.toolkits.pointer.DumbPointerAnalysis;
import soot.jimple.toolkits.pointer.SideEffectAnalysis;
import soot.options.CGOptions;
import soot.options.Options;
import soot.toolkits.exceptions.PedanticThrowAnalysis;
import soot.toolkits.exceptions.ThrowAnalysis;
import soot.toolkits.exceptions.UnitThrowAnalysis;
import soot.util.ArrayNumberer;
import soot.util.Chain;
import soot.util.HashChain;
import soot.util.MapNumberer;
import soot.util.Numberer;
import soot.util.SingletonList;
import soot.util.StringNumberer;

/** Manages the SootClasses of the application being analyzed. */
public class Scene  //extends AbstractHost
{
    public Scene ( Singletons.Global g )
    {
    	setReservedNames();
    	
        // load soot.class.path system property, if defined
        String scp = System.getProperty("soot.class.path");

        if (scp != null)
            setSootClassPath(scp);

        kindNumberer.add( Kind.INVALID );
        kindNumberer.add( Kind.STATIC );
        kindNumberer.add( Kind.VIRTUAL );
        kindNumberer.add( Kind.INTERFACE );
        kindNumberer.add( Kind.SPECIAL );
        kindNumberer.add( Kind.CLINIT );
        kindNumberer.add( Kind.THREAD );
        kindNumberer.add( Kind.FINALIZE );
        kindNumberer.add( Kind.INVOKE_FINALIZE );
        kindNumberer.add( Kind.PRIVILEGED );
        kindNumberer.add( Kind.NEWINSTANCE );

        addSootBasicClasses();
    }
    public static Scene  v() { return G.v().soot_Scene (); }
    
    Chain<SootClass> classes = new HashChain<SootClass>();
    Chain<SootClass> applicationClasses = new HashChain<SootClass>();
    Chain<SootClass> libraryClasses = new HashChain<SootClass>();
    Chain<SootClass> phantomClasses = new HashChain<SootClass>();
    
	// by xiaowei zhou, add defining class loader number to the data structure for identifying a class, 20111115
//    private final Map<String,Type> nameToClass = new HashMap<String,Type>();
    private final Map<ClassNameAndDefCL,Type> nameAndDefCLToClass = new HashMap<ClassNameAndDefCL,Type>();
    
 // by xiaowei zhou, , 20111115
    private final Map<String, Set<Type>> nameToClasses = new HashMap<String, Set<Type>>();
    
	// by xiaowei zhou, , 20120216
    // classes which has no defCL will be added to classes and this data structure, and will be treated as phantom classes during resolving
    private final Map<String, Map<Integer, Type>> classesWithNoDefCL = new HashMap<String, Map<Integer, Type>>();

	ArrayNumberer kindNumberer = new ArrayNumberer();
    ArrayNumberer typeNumberer = new ArrayNumberer();
    ArrayNumberer methodNumberer = new ArrayNumberer();
    Numberer unitNumberer = new MapNumberer();
    Numberer contextNumberer = null;
    ArrayNumberer fieldNumberer = new ArrayNumberer();
    ArrayNumberer classNumberer = new ArrayNumberer();
    StringNumberer subSigNumberer = new StringNumberer();
    ArrayNumberer localNumberer = new ArrayNumberer();

    private Hierarchy activeHierarchy;
    private FastHierarchy activeFastHierarchy;
    private CallGraph activeCallGraph;
    private ReachableMethods reachableMethods;
    private PointsToAnalysis activePointsToAnalysis;
    private SideEffectAnalysis activeSideEffectAnalysis;
    private List<SootMethod> entryPoints;

    boolean allowsPhantomRefs = false;

    SootClass mainClass;
    
	// by xiaowei zhou, partition the soot class path into those with respect to class loaders, 20111205
//    String sootClassPath = null;
 // by xiaowei zhou, sootAppClassPath is the default class path, 20111205
    String sootAppClassPath = null;
    Map<Integer, ISootClassPath> customCPs = new HashMap<Integer, ISootClassPath>();
    
    public Map<String, Set<Type>> getNameToClasses() {
		return nameToClasses;
	}
    
    public Map<Integer, ISootClassPath> getCustomCPs() {
		return customCPs;
	}
    
	public ISootClassPath getCustomCP(Integer customCL) {
    	return customCPs.get(customCL);
    }
    
    public void addCustomCLCP(Integer customCL, ISootClassPath customCP) {
    	customCPs.put(customCL, customCP);
    }

    // Two default values for constructing ExceptionalUnitGraphs:
    private ThrowAnalysis defaultThrowAnalysis = null;
    
    public void setMainClass(SootClass m)
    {
        mainClass = m;
        if(!m.declaresMethod(getSubSigNumberer().findOrAdd( "void main(java.lang.String[])" ))) {
        	throw new RuntimeException("Main-class has no main method!");
        }
    }
    
    Set<String> reservedNames = new HashSet<String>();
    
    /**
        Returns a set of tokens which are reserved.  Any field, class, method, or local variable with such a name will be quoted.
     */
     
    public Set<String> getReservedNames()
    {
        return reservedNames;
    }
    
    /**
        If this name is in the set of reserved names, then return a quoted version of it.  Else pass it through.
     */
    
    public String quotedNameOf(String s)
    {
        if(reservedNames.contains(s))
            return "\'" + s + "\'";
        else
            return s;
    }
    
    public SootClass getMainClass()
    {
        if(mainClass == null) {
        	setMainClassFromOptions();
        }
        if(mainClass == null)
            throw new RuntimeException("There is no main class set!");
            
        return mainClass;
    }
    public SootMethod getMainMethod() {
        if(mainClass==null) {
            throw new RuntimeException("There is no main class set!");
        } 
    	// by xiaowei zhou, , 20111213
        if (!mainClass.declaresMethod ("main", new SingletonList( ArrayType.v(RefType.v("java.lang.String", ClassLoaders.appCLNum), 1) ), VoidType.v())) {
            throw new RuntimeException("Main class declares no main method!");
        }
    	// by xiaowei zhou, , 20111213
        return mainClass.getMethod ("main", new SingletonList( ArrayType.v(RefType.v("java.lang.String", ClassLoaders.appCLNum), 1) ), VoidType.v());   
    }
    
    
    public void setSootClassPath(String p)
    {
    	// by xiaowei zhou, , 20111205
//        sootClassPath = p;
        sootAppClassPath = p;
        SourceLocator.v().invalidateClassPath();
    }
    
    public String getSootClassPath()
    {
    	// by xiaowei zhou, change "sootClassPath" to "sootAppClassPath", 20111205
    	
        if( sootAppClassPath == null ) {
            String optionscp = Options.v().soot_classpath();
            if( optionscp.length() > 0 )
            	sootAppClassPath = optionscp;

            String defaultSootClassPath = defaultClassPath();
	
	        //if no classpath is given on the command line, take the default
	        if( sootAppClassPath == null ) {
	        	sootAppClassPath = defaultSootClassPath;
	        } else {
	        	//if one is given...
	            if(Options.v().prepend_classpath()) {
	            	//if the prepend flag is set, append the default classpath
	            	sootAppClassPath += File.pathSeparator + defaultSootClassPath;
	            } 
	            //else, leave it as it is
	        }        
        }

        return sootAppClassPath;
    }
    
	public String defaultClassPath() {
		StringBuffer sb = new StringBuffer();
		sb.append(System.getProperty("java.class.path")+File.pathSeparator);
        if(System.getProperty("os.name").equals("Mac OS X")) {
	        //in Mac OS X, rt.jar is split into classes.jar and ui.jar
	        sb.append(File.pathSeparator);
	        sb.append(System.getProperty("java.home"));
	        sb.append(File.separator);
	        sb.append("..");
	        sb.append(File.separator);
	        sb.append("Classes");
	        sb.append(File.separator);
	        sb.append("classes.jar");
	
	        sb.append(File.pathSeparator);
	        sb.append(System.getProperty("java.home"));
	        sb.append(File.separator);
	        sb.append("..");
	        sb.append(File.separator);
	        sb.append("Classes");
	        sb.append(File.separator);
	        sb.append("ui.jar");


        } else {
            sb.append(File.pathSeparator);
            sb.append(System.getProperty("java.home"));
            sb.append(File.separator);
            sb.append("lib");
            sb.append(File.separator);
            sb.append("rt.jar");
        }
        
		if(Options.v().whole_program() || Options.v().output_format()==Options.output_format_dava) {
			//add jce.jar, which is necessary for whole program mode
			//(java.security.Signature from rt.jar import javax.crypto.Cipher from jce.jar            	
			sb.append(File.pathSeparator+
				System.getProperty("java.home")+File.separator+"lib"+File.separator+"jce.jar");
		}
		
		return sb.toString();
	}


    private int stateCount;
    public int getState() { return this.stateCount; }
    private void modifyHierarchy() {
        stateCount++;
        activeHierarchy = null;
        activeFastHierarchy = null;
        activeSideEffectAnalysis = null;
        activePointsToAnalysis = null;
    }

    public void addClass(SootClass c) 
    {
        if(c.isInScene())
            throw new RuntimeException("already managed: "+c.getName());

        // by xiaowei zhou, , 20111115
//        if(containsClass(c.getName()))
//            throw new RuntimeException("duplicate class: "+c.getName());
        
    	// by xiaowei zhou, , 20120216
        if(c.defCLNumber == null) {
    		if (containsClass(c.name, c.initCLNumber, 0))
    			throw new RuntimeException("duplicate class: " + c.getName());
        } else if (containsClass(c.getNameAndDefCL())) {
			throw new RuntimeException("duplicate class: " + c.getName());
        }
        
    	// by xiaowei zhou, comment out since moving to the "else" branch in the above "if", 20120216
//		if (containsClass(c.getNameAndDefCL()))
//			throw new RuntimeException("duplicate class: " + c.getName());

        classes.add(c);
        c.setLibraryClass();

     // by xiaowei zhou, , 20111115
//        nameToClass.put(c.getName(), c.getType());
        String cName = c.getName();
		if (c.defCLNumber != null) {
			Set<Type> cSet = nameToClasses.get(cName);
			if (cSet == null) {
				cSet = new HashSet<Type>();
				nameToClasses.put(cName, cSet);
			}
			cSet.add(c.getType());
			nameAndDefCLToClass.put(c.getNameAndDefCL(), c.getType());
		} else {
			// by xiaowei zhou, , 20120216
			Map<Integer, Type> initCLToCls = classesWithNoDefCL.get(cName);
			if(initCLToCls==null){
				initCLToCls = new HashMap<Integer, Type>();
				classesWithNoDefCL.put(cName, initCLToCls);
			}
			initCLToCls.put(c.initCLNumber, c.getType());
		}
        
        c.getType().setSootClass(c);
        c.setInScene(true);
        modifyHierarchy();
    }

    public void removeClass(SootClass c)
    {
        if(!c.isInScene())
            throw new RuntimeException();

        classes.remove(c);
        
        if(c.isLibraryClass()) {
            libraryClasses.remove(c);
        } else if(c.isPhantomClass()) {
            phantomClasses.remove(c);
        } else if(c.isApplicationClass()) {
            applicationClasses.remove(c);
        }
        
    	// by xiaowei zhou, , 20120216
        if(c.defCLNumber == null) {
        	Map<Integer, Type> initCLToClses = classesWithNoDefCL.get(c.name);
        	if(initCLToClses!=null) {
        		initCLToClses.remove(c.initCLNumber);
        	}
        }
        
        c.getType().setSootClass(null);
        c.setInScene(false);
        modifyHierarchy();
    }

	// by xiaowei zhou, change parameter type to include bundle number, 20111115
    public boolean containsClass(ClassNameAndDefCL className)
    {
        RefType type = (RefType) nameAndDefCLToClass.get(className);
        if( type == null ) return false;
        if( !type.hasSootClass() ) return false;
        SootClass c = type.getSootClass();
        return c.isInScene();
    }
    
	// by xiaowei zhou, , 20111230
	public boolean containClasses(String className) {
		Set<Type> set = nameToClasses.get(className);
		if (set == null || set.isEmpty()) {
			return false;
		}
		for (Type type : set) {
			RefType refType = (RefType) type;
			if (refType.hasSootClass()) {
				SootClass c = refType.getSootClass();
				if (c.isInScene()) {
					return true;
				}
			}
		}
		return false;
	}
    
	// by xiaowei zhou, , 20111213
    public boolean containsClass(String className, Integer defCL) {
    	return containsClass(new ClassNameAndDefCL(className, defCL));
    }
    
 // by xiaowei zhou, , 20111214
	public boolean containsClass(String className, Integer initCL, int nouse) {
		Integer defCL = ClassLoaders.initCLToDefCL(initCL, className);
		if (defCL != null) {
			return containsClass(new ClassNameAndDefCL(className, defCL));
		} else {
			// by xiaowei zhou, , 20120216
			Map<Integer, Type> initCLToCls = classesWithNoDefCL.get(className);
			if(initCLToCls == null) {
				return false;
			} else {
				RefType refT = (RefType) initCLToCls.get(initCL);
				if(refT==null||!refT.hasSootClass()) {
					return false;
				}
				return refT.getSootClass().isInScene;
			}
		}
	}
    
    
 // by xiaowei zhou, iterate the class set with the same name to see if there is a class, 20111115
	// by xiaowei zhou, comment out, 20111213
/*	public boolean containsClass(String className) {
		Set<Type> typeSet = nameToClasses.get(className);
		if (typeSet == null) {
			return false;
		} else {
			for (Type type : typeSet) {
				RefType refType = (RefType) type;
				if (refType.hasSootClass()) {
					SootClass sc = refType.getSootClass();
					if (sc.isInScene()) {
						return true;
					}
				}
			}
			return false;
		}
	}*/
    
	// by xiaowei zhou, comment out, 20111213
/*    public boolean containsType(String className)
    {
    	// by xiaowei zhou, , 20111115
//        return nameToClass.containsKey(className);
        return nameToClasses.containsKey(className);
    }*/
    
	// by xiaowei zhou, , 20111127
    public boolean containsType(ClassNameAndDefCL classAndDCL)
    {
        return nameAndDefCLToClass.containsKey(classAndDCL);
    }
    
	// by xiaowei zhou, , 20111213
    public boolean containsType(String className, Integer defCL) {
    	return containsType(new ClassNameAndDefCL(className, defCL));
    }
    
	// by xiaowei zhou, , 20120216
    public boolean containsType(String className, Integer initCL, int nouse) {
		Integer defCL = ClassLoaders.initCLToDefCL(initCL, className);
		if(defCL!=null) {
			return containsType(className, defCL);
		} else {
			Map<Integer, Type> initCLToCls = classesWithNoDefCL.get(className);
			if(initCLToCls == null) {
				return false;
			} else {
				return initCLToCls.containsKey(initCL);
			}
		}
    }
    
    public String signatureToClass(String sig) {
        if( sig.charAt(0) != '<' ) throw new RuntimeException("oops "+sig);
        if( sig.charAt(sig.length()-1) != '>' ) throw new RuntimeException("oops "+sig);
        int index = sig.indexOf( ":" );
        if( index < 0 ) throw new RuntimeException("oops "+sig);
        return sig.substring(1,index);
    }

    public String signatureToSubsignature(String sig) {
        if( sig.charAt(0) != '<' ) throw new RuntimeException("oops "+sig);
        if( sig.charAt(sig.length()-1) != '>' ) throw new RuntimeException("oops "+sig);
        int index = sig.indexOf( ":" );
        if( index < 0 ) throw new RuntimeException("oops "+sig);
        return sig.substring(index+2,sig.length()-1);
    }

	// by xiaowei zhou, tmp comment out, 20111214
/*    private SootField grabField(String fieldSignature)
    {
        String cname = signatureToClass( fieldSignature );
        String fname = signatureToSubsignature( fieldSignature );
        if( !containsClass(cname) ) return null;
        SootClass c = getSootClass(cname);
        if( !c.declaresField( fname ) ) return null;
        return c.getField( fname );
    }*/

	// by xiaowei zhou, tmp comment out, 20111214
    /*public boolean containsField(String fieldSignature)
    {
        return grabField(fieldSignature) != null;
    }*/
    
	// by xiaowei zhou, tmp comment out, 20111214
/*    private SootMethod grabMethod(String methodSignature)
    {
        String cname = signatureToClass( methodSignature );
        String mname = signatureToSubsignature( methodSignature );
        if( !containsClass(cname) ) return null;
        SootClass c = getSootClass(cname);
        if( !c.declaresMethod( mname ) ) return null;
        return c.getMethod( mname );
    }*/

	// by xiaowei zhou, tmp comment out, 20111214
/*    public boolean containsMethod(String methodSignature)
    {
        return grabMethod(methodSignature) != null;
    }*/

	// by xiaowei zhou, tmp comment out, 20111214
/*    public SootField getField(String fieldSignature)
    {
        SootField f = grabField( fieldSignature );
        if (f != null)
            return f;

        throw new RuntimeException("tried to get nonexistent field "+fieldSignature);
    }
*/
    
	// by xiaowei zhou, 'sourceClass' is the class which contains the code calling the method with 'methodSignature', 20111214
	// by xiaowei zhou, change some code in method body, 20111214
//    public SootMethod getMethod(String methodSignature)
    public SootMethod getMethod(String methodSignature, SootClass sourceClass, int nouse)
    {
		String cname = signatureToClass(methodSignature);
		Integer defCL = ClassLoaders.initCLToDefCL(
				sourceClass.getDefCLNumber(), cname);
		if (defCL != null) {
			SootMethod m = grabMethod(methodSignature, defCL);
			if (m != null)
				return m;
		}
        throw new RuntimeException("tried to get nonexistent method "+methodSignature);
    }
    
	// by xiaowei zhou, , 20111215
    public SootMethod getMethod(String methodSignature, SootClass containingClass) {
    	return getMethod(methodSignature, containingClass.getDefCLNumber());
    }
    
	// by xiaowei zhou, , 20111215
    public SootMethod getMethod(String methodSignature, Integer defCL) {
    	SootMethod m = grabMethod(methodSignature, defCL);
		if (m != null)
			return m;
		throw new RuntimeException("tried to get nonexistent method "+methodSignature);
    }
    
	// by xiaowei zhou, add a parameter 'defCL', 20111214
	// by xiaowei zhou, change some code in method body, 20111214
    private SootMethod grabMethod(String methodSignature, Integer defCL)
    {
        String cname = signatureToClass( methodSignature );
        String mname = signatureToSubsignature( methodSignature );
        if( !containsClass(cname, defCL) ) return null;
        SootClass c = getSootClass(cname, defCL);
        if( !c.declaresMethod( mname ) ) return null;
        return c.getMethod( mname );
    }

	// by xiaowei zhou, 'sourceClass' is the class which contains the code calling the method with 'methodSignature', 20111214
	// by xiaowei zhou, change some code in method body, 20111214
//    public boolean containsMethod(String methodSignature)
    public boolean containsMethod(String methodSignature, SootClass sourceClass, int nouse)
    {
		String cname = signatureToClass(methodSignature);
		Integer defCL = ClassLoaders.initCLToDefCL(
				sourceClass.getDefCLNumber(), cname);
		if (defCL == null) {
			return false;
		} else {
			return grabMethod(methodSignature, defCL) != null;
		}
    }
    
	// by xiaowei zhou, , 20111215
    public boolean containsMethod(String methodSignature, SootClass containingClass) {
    	return grabMethod(methodSignature, containingClass.getDefCLNumber()) != null;
    }
    
 // by xiaowei zhou, , 20111215
    public boolean containsMethod(String methodSignature, Integer defCL) {
    	return grabMethod(methodSignature, defCL) != null;
    }
    

    /** 
     * Attempts to load the given class and all of the required support classes.
     * Returns the original class if it was loaded, or null otherwise.
     */
     
	// by xiaowei zhou, add a parameter denoting intended defining class loader, 20111205
//    public SootClass tryLoadClass(String className, int desiredLevel)
    public SootClass tryLoadClass(String className, int desiredLevel, Integer intendedDefCL) 
    {   
        /*
        if(Options.v().time())
            Main.v().resolveTimer.start();
        */
        
        setPhantomRefs(true);
        //SootResolver resolver = new SootResolver();
        if( !getPhantomRefs() 
        // by xiaowei zhou, , 20111206
//        && SourceLocator.v().getClassSource(className) == null ) {
        && SourceLocator.v().getClassSource(className, intendedDefCL) == null ) {
            setPhantomRefs(false);
            return null;
        }
        SootResolver resolver = SootResolver.v();
        
    	// by xiaowei zhou, , 20111209
        SootClass toReturn = resolver.resolveClass(className, desiredLevel, intendedDefCL);
        
        setPhantomRefs(false);

        return toReturn;
        
        /*
        if(Options.v().time())
            Main.v().resolveTimer.end(); */
    }
    
    /** 
     * Loads the given class and all of the required support classes.  Returns the first class.
     */
     
	// by xiaowei zhou, add a parameter denoting intended defining class loader, 20111208
    public SootClass loadClassAndSupport(String className, Integer intendedDefCL) 
    {
        SootClass ret = loadClass(className, SootClass.SIGNATURES, intendedDefCL);
        if( !ret.isPhantom() ) ret = loadClass(className, SootClass.BODIES, intendedDefCL);
        return ret;
    }

	// by xiaowei zhou, add a parameter denoting intended defining class loader, 20111205
//    public SootClass loadClass(String className, int desiredLevel) 
    public SootClass loadClass(String className, int desiredLevel, Integer intendedDefCL)
    {   
        /*
        if(Options.v().time())
            Main.v().resolveTimer.start();
        */
        
        setPhantomRefs(true);
        //SootResolver resolver = new SootResolver();
        SootResolver resolver = SootResolver.v();
    	// by xiaowei zhou, , 20111206
        SootClass toReturn = resolver.resolveClass(className, desiredLevel, intendedDefCL);
        
        setPhantomRefs(false);

        return toReturn;
        
        /*
        if(Options.v().time())
            Main.v().resolveTimer.end(); */
    }
    
    /**
     * Returns the RefType with the given className.  
     * @throws IllegalStateException if the RefType for this class cannot be found.
     * Use {@link #containsType(String)} to check if type is registered
     */
/*    public RefType getRefType(String className) 
    {
    	// by xiaowei zhou, change to get an arbitrary class with the name, 20111115
    	// FIXME
//        RefType refType = (RefType) nameToClass.get(className);
    	Set<Type> classes = nameToClasses.get(className);
//        if(refType==null) {
        if(classes==null) {
        	throw new IllegalStateException("RefType "+className+" not loaded. " +
        			"If you tried to get the RefType of a library class, did you call loadNecessaryClasses()? " +
        			"Otherwise please check Soot's classpath.");
        }
		return (RefType)classes.iterator().next();
    }*/
    
	// by xiaowei zhou, , 20111127
    public RefType getRefType(ClassNameAndDefCL classNameAndDCL) {
    	RefType refType = (RefType) nameAndDefCLToClass.get(classNameAndDCL);
        if(refType==null) {
        	throw new IllegalStateException("RefType "+classNameAndDCL+" not loaded. " +
        			"If you tried to get the RefType of a library class, did you call loadNecessaryClasses()? " +
        			"Otherwise please check Soot's classpath.");
        }
        return refType;
    }
    
	// by xiaowei zhou, , 20111215
    public RefType getRefType(String className, Integer defCL) {
    	return getRefType(new ClassNameAndDefCL(className, defCL));
    }
    
    public RefType getRefType(String className, Integer initCL, int nouse) {
		Integer defCL = ClassLoaders.initCLToDefCL(initCL, className);
		if(defCL != null) {
			return getRefType(className, defCL);
		} else {
			Map<Integer, Type> initCLToCls = classesWithNoDefCL.get(className);
			RefType refType = null;
			if(initCLToCls!=null) {
				refType = (RefType)initCLToCls.get(initCL);
			}
	        if(refType==null) {
	        	throw new IllegalStateException("RefType "+className+" not loaded. " +
	        			"If you tried to get the RefType of a library class, did you call loadNecessaryClasses()? " +
	        			"Otherwise please check Soot's classpath.");
	        }
	        return refType;
		}
    }
    
    /**
     * Returns the {@link RefType} for {@link Object}.
     */
    public RefType getObjectType() {
    	// by xiaowei zhou, , 20111215
//    	return getRefType("java.lang.Object");
    	return getRefType("java.lang.Object", ClassLoaders.appCLNum);
    }

    /**
     * Returns the RefType with the given className.  
     */
    public void addRefType(RefType type)
    {
    	// by xiaowei zhou, , 20111208
//        nameToClass.put(type.getClassName(), type);
    	
    	// by xiaowei zhou, , 20120216
		String cName = type.getClassName();
    	if(type.getDefCL()!=null) {
			Set<Type> cSet = nameToClasses.get(cName);
			if (cSet == null) {
				cSet = new HashSet<Type>();
				nameToClasses.put(cName, cSet);
			}
			cSet.add(type);
			nameAndDefCLToClass.put(new ClassNameAndDefCL(type.getClassName(),
					type.getDefCL()), type);
		} else {
			Map<Integer, Type> initCLToCls = classesWithNoDefCL.get(cName);
			if(initCLToCls==null){
				initCLToCls = new HashMap<Integer, Type>();
				classesWithNoDefCL.put(cName, initCLToCls);
			}
			initCLToCls.put(type.getInitCL(), type);
		}
    }

    /**
     * Returns the SootClass with the given className.  
     */

/*	public SootClass getSootClass(String className) {
		
		// by xiaowei zhou tmp, 20111209
		throw new RuntimeException("Do not call Scene.getSootClass without a class loader! -- xiaowei zhou");

		// by xiaowei zhou tmp comment out, 20111209
		RefType type = (RefType) nameToClass.get(className);
		SootClass toReturn = null;
		if (type != null)
			toReturn = type.getSootClass();

		if (toReturn != null) {
			return toReturn;
		} else if (allowsPhantomRefs()) {
			SootClass c = new SootClass(className);
			c.setPhantom(true);
			addClass(c);
			return c;
		} else {
			throw new RuntimeException(System.getProperty("line.separator")
					+ "Aborting: can't find classfile " + className);
		}
	}*/
	
	// by xiaowei zhou, , 20111127
	public SootClass getSootClass(ClassNameAndDefCL nameAndDCL) {
		RefType type = (RefType) nameAndDefCLToClass.get(nameAndDCL);
		SootClass toReturn = null;
		if (type != null)
			toReturn = type.getSootClass();

		if (toReturn != null) {
			return toReturn;
		} else if (allowsPhantomRefs()) {
			SootClass c = new SootClass(nameAndDCL);
			// by xiaowei zhou, some thought: the "addClass" call will set "c" to be not a phantom class by a internal call to "c.setLibraryClass()" , 20120218
			// by xiaowei zhou, some thought: so the "c.setPhantom(true)" line may be useless, 20120218
			c.setPhantom(true);
			addClass(c);
			return c;
		} else {
			throw new RuntimeException(System.getProperty("line.separator")
					+ "Aborting: can't find classfile " + nameAndDCL);
		}
	}
	
	// by xiaowei zhou, , 20111207
	public SootClass getSootClass(String className, Integer defCL) {
		return getSootClass(new ClassNameAndDefCL(className, defCL));
	}
	
	// by xiaowei zhou, , 20120217
	public SootClass getSootClass(String className) {
    	if(className.startsWith("java.")) {
    		return getSootClass(className, ClassLoaders.appCLNum);
    	} else {
    		throw new RuntimeException("Trying to call Scene.getSootClass(className) without a class loader!");
    	}
	}
	
	// by xiaowei zhou, , 20111213
	public SootClass getSootClass(String className, Integer initCL, int nouse) {
		Integer defCL = ClassLoaders.initCLToDefCL(initCL, className);
		if(defCL != null) {
			return getSootClass(className, defCL);
		} else if (containsClass(className, initCL, nouse)) {
			Map<Integer, Type> initCLToCls = classesWithNoDefCL.get(className);
			RefType refT = (RefType) initCLToCls.get(initCL);
			return refT.getSootClass();
		} else if (allowsPhantomRefs()) {
			SootClass c = new SootClass(className, initCL, 0);
			// by xiaowei zhou, some thought: the "addClass" call will set "c" to be not a phantom class by a internal call to "c.setLibraryClass()" , 20120218
			// by xiaowei zhou, some thought: so the "c.setPhantom(true)" line may be useless, 20120218
			c.setPhantom(true);
			addClass(c);
			return c;
		} else {
			throw new RuntimeException(System.getProperty("line.separator")
					+ "Aborting: can't find classfile " + className);
		}
	}

    /**
     * Returns an backed chain of the classes in this manager.
     */
     
    public Chain<SootClass> getClasses()
    {
        return classes;
    }

    /* The four following chains are mutually disjoint. */

    /**
     * Returns a chain of the application classes in this scene.
     * These classes are the ones which can be freely analysed & modified.
     */
    public Chain<SootClass> getApplicationClasses()
    {
        return applicationClasses;
    }

    /**
     * Returns a chain of the library classes in this scene.
     * These classes can be analysed but not modified.
     */
    public Chain<SootClass> getLibraryClasses()
    {
        return libraryClasses;
    }

    /**
     * Returns a chain of the phantom classes in this scene.
     * These classes are referred to by other classes, but cannot be loaded.
     */
    public Chain<SootClass> getPhantomClasses()
    {
        return phantomClasses;
    }

    Chain<SootClass> getContainingChain(SootClass c)
    {
        if (c.isApplicationClass())
            return getApplicationClasses();
        else if (c.isLibraryClass())
            return getLibraryClasses();
        else if (c.isPhantomClass())
            return getPhantomClasses();

        return null;
    }

    /****************************************************************************/
    /**
        Retrieves the active side-effect analysis
     */

    public SideEffectAnalysis getSideEffectAnalysis() 
    {
        if(!hasSideEffectAnalysis()) {
	    setSideEffectAnalysis( new SideEffectAnalysis(
			getPointsToAnalysis(),
			getCallGraph() ) );
	}
            
        return activeSideEffectAnalysis;
    }
    
    /**
        Sets the active side-effect analysis
     */
     
    public void setSideEffectAnalysis(SideEffectAnalysis sea)
    {
        activeSideEffectAnalysis = sea;
    }

    public boolean hasSideEffectAnalysis()
    {
        return activeSideEffectAnalysis != null;
    }
    
    public void releaseSideEffectAnalysis()
    {
        activeSideEffectAnalysis = null;
    }

    /****************************************************************************/
    /**
        Retrieves the active pointer analysis
     */

    public PointsToAnalysis getPointsToAnalysis() 
    {
        if(!hasPointsToAnalysis()) {
	    return DumbPointerAnalysis.v();
	}
            
        return activePointsToAnalysis;
    }
    
    /**
        Sets the active pointer analysis
     */
     
    public void setPointsToAnalysis(PointsToAnalysis pa)
    {
        activePointsToAnalysis = pa;
    }

    public boolean hasPointsToAnalysis()
    {
        return activePointsToAnalysis != null;
    }
    
    public void releasePointsToAnalysis()
    {
        activePointsToAnalysis = null;
    }

    /****************************************************************************/
    /** Makes a new fast hierarchy is none is active, and returns the active
     * fast hierarchy. */
    public FastHierarchy getOrMakeFastHierarchy() {
	if(!hasFastHierarchy() ) {
	    setFastHierarchy( new FastHierarchy() );
	}
	return getFastHierarchy();
    }
    /**
        Retrieves the active fast hierarchy
     */

    public FastHierarchy getFastHierarchy() 
    {
        if(!hasFastHierarchy())
            throw new RuntimeException("no active FastHierarchy present for scene");
            
        return activeFastHierarchy;
    }
    
    /**
        Sets the active hierarchy
     */
     
    public void setFastHierarchy(FastHierarchy hierarchy)
    {
        activeFastHierarchy = hierarchy;
    }

    public boolean hasFastHierarchy()
    {
        return activeFastHierarchy != null;
    }
    
    public void releaseFastHierarchy()
    {
        activeFastHierarchy = null;
    }

    /****************************************************************************/
    /**
        Retrieves the active hierarchy
     */

    public Hierarchy getActiveHierarchy() 
    {
        if(!hasActiveHierarchy())
            //throw new RuntimeException("no active Hierarchy present for scene");
            setActiveHierarchy( new Hierarchy() );
            
        return activeHierarchy;
    }
    
    /**
        Sets the active hierarchy
     */
     
    public void setActiveHierarchy(Hierarchy hierarchy)
    {
        activeHierarchy = hierarchy;
    }

    public boolean hasActiveHierarchy()
    {
        return activeHierarchy != null;
    }
    
    public void releaseActiveHierarchy()
    {
        activeHierarchy = null;
    }

    /** Get the set of entry points that are used to build the call graph. */
    public List<SootMethod> getEntryPoints() {
        if( entryPoints == null ) {
            entryPoints = EntryPoints.v().all();
        }
        return entryPoints;
    }

    /** Change the set of entry point methods used to build the call graph. */
    public void setEntryPoints( List<SootMethod> entryPoints ) {
        this.entryPoints = entryPoints;
    }

    private ContextSensitiveCallGraph cscg;
    public ContextSensitiveCallGraph getContextSensitiveCallGraph() {
        if(cscg == null) throw new RuntimeException("No context-sensitive call graph present in Scene. You can bulid one with Paddle.");
        return cscg;
    }

    public void setContextSensitiveCallGraph(ContextSensitiveCallGraph cscg) {
        this.cscg = cscg;
    }

    public CallGraph getCallGraph() 
    {
        if(!hasCallGraph()) {
            throw new RuntimeException( "No call graph present in Scene. Maybe you want Whole Program mode (-w)." );
        }
            
        return activeCallGraph;
    }
    
    public void setCallGraph(CallGraph cg)
    {
        reachableMethods = null;
        activeCallGraph = cg;
    }

    public boolean hasCallGraph()
    {
        return activeCallGraph != null;
    }
    
    public void releaseCallGraph()
    {
        activeCallGraph = null;
        reachableMethods = null;
    }
    public ReachableMethods getReachableMethods() {
        if( reachableMethods == null ) {
            reachableMethods = new ReachableMethods(
                    getCallGraph(), new ArrayList<MethodOrMethodContext>(getEntryPoints()) );
        }
        reachableMethods.update();
        return reachableMethods;
    }
    public void setReachableMethods( ReachableMethods rm ) {
        reachableMethods = rm;
    }
    public boolean hasReachableMethods() {
        return reachableMethods != null;
    }
    public void releaseReachableMethods() {
        reachableMethods = null;
    }
   
    public boolean getPhantomRefs()
    {
        //if( !Options.v().allow_phantom_refs() ) return false;
        //return allowsPhantomRefs;
    	return Options.v().allow_phantom_refs();
    }

    public void setPhantomRefs(boolean value)
    {
        allowsPhantomRefs = value;
    }
    
    public boolean allowsPhantomRefs()
    {
        return getPhantomRefs();
    }
    public Numberer kindNumberer() { return kindNumberer; }
    public ArrayNumberer getTypeNumberer() { return typeNumberer; }
    public ArrayNumberer getMethodNumberer() { return methodNumberer; }
    public Numberer getContextNumberer() { return contextNumberer; }
    public Numberer getUnitNumberer() { return unitNumberer; }
    public ArrayNumberer getFieldNumberer() { return fieldNumberer; }
    public ArrayNumberer getClassNumberer() { return classNumberer; }
    public StringNumberer getSubSigNumberer() { return subSigNumberer; }
    public ArrayNumberer getLocalNumberer() { return localNumberer; }

    public void setContextNumberer( Numberer n ) {
        if( contextNumberer != null )
            throw new RuntimeException(
                    "Attempt to set context numberer when it is already set." );
        contextNumberer = n;
    }

    /**
     * Returns the {@link ThrowAnalysis} to be used by default when
     * constructing CFGs which include exceptional control flow.
     *
     * @return the default {@link ThrowAnalysis}
     */
    public ThrowAnalysis getDefaultThrowAnalysis() 
    {
	if( defaultThrowAnalysis == null ) {
	    int optionsThrowAnalysis = Options.v().throw_analysis();
	    switch (optionsThrowAnalysis) {
	    case Options.throw_analysis_pedantic:
		defaultThrowAnalysis = PedanticThrowAnalysis.v();
		break;
	    case Options.throw_analysis_unit:
		defaultThrowAnalysis = UnitThrowAnalysis.v();
		break;
	    default:
		throw new IllegalStateException("Options.v().throw_analysi() == " +
						Options.v().throw_analysis());
	    }
	}
	return defaultThrowAnalysis;
    }

    /**
     * Sets the {@link ThrowAnalysis} to be used by default when
     * constructing CFGs which include exceptional control flow.
     *
     * @param the default {@link ThrowAnalysis}.
     */
    public void setDefaultThrowAnalysis(ThrowAnalysis ta) 
    {
	defaultThrowAnalysis = ta;
    }

    private void setReservedNames()
    {
        Set<String> rn = getReservedNames();        
        rn.add("newarray");
        rn.add("newmultiarray");
        rn.add("nop");
        rn.add("ret");
        rn.add("specialinvoke");
        rn.add("staticinvoke");
        rn.add("tableswitch");
        rn.add("virtualinvoke");
        rn.add("null_type");
        rn.add("unknown");
        rn.add("cmp");
        rn.add("cmpg");
        rn.add("cmpl");
        rn.add("entermonitor");
        rn.add("exitmonitor");
        rn.add("interfaceinvoke");
        rn.add("lengthof");
        rn.add("lookupswitch");
        rn.add("neg");
        rn.add("if");
        rn.add("abstract");
        rn.add("annotation");
        rn.add("boolean");
        rn.add("break");
        rn.add("byte");
        rn.add("case");
        rn.add("catch");
        rn.add("char");
        rn.add("class");
        rn.add("final");
        rn.add("native");
        rn.add("public");
        rn.add("protected");
        rn.add("private");
        rn.add("static");
        rn.add("synchronized");
        rn.add("transient");
        rn.add("volatile");
        rn.add("interface");
        rn.add("void");
        rn.add("short");
        rn.add("int");
        rn.add("long");
        rn.add("float");
        rn.add("double");
        rn.add("extends");
        rn.add("implements");
        rn.add("breakpoint");
        rn.add("default");
        rn.add("goto");
        rn.add("instanceof");
        rn.add("new");
        rn.add("return");
        rn.add("throw");
        rn.add("throws");
        rn.add("null");
        rn.add("from");
        rn.add("to");
    }

    private final Set<String>[] basicclasses=new Set[4];

    private void addSootBasicClasses() {
        basicclasses[SootClass.HIERARCHY] = new HashSet<String>();
        basicclasses[SootClass.SIGNATURES] = new HashSet<String>();
        basicclasses[SootClass.BODIES] = new HashSet<String>();

	addBasicClass("java.lang.Object");
	addBasicClass("java.lang.Class", SootClass.SIGNATURES);

	addBasicClass("java.lang.Void", SootClass.SIGNATURES);
	addBasicClass("java.lang.Boolean", SootClass.SIGNATURES);
	addBasicClass("java.lang.Byte", SootClass.SIGNATURES);
	addBasicClass("java.lang.Character", SootClass.SIGNATURES);
	addBasicClass("java.lang.Short", SootClass.SIGNATURES);
	addBasicClass("java.lang.Integer", SootClass.SIGNATURES);
	addBasicClass("java.lang.Long", SootClass.SIGNATURES);
	addBasicClass("java.lang.Float", SootClass.SIGNATURES);
	addBasicClass("java.lang.Double", SootClass.SIGNATURES);

	addBasicClass("java.lang.String");
	addBasicClass("java.lang.StringBuffer", SootClass.SIGNATURES);

	addBasicClass("java.lang.Error");
	addBasicClass("java.lang.AssertionError", SootClass.SIGNATURES);
	addBasicClass("java.lang.Throwable", SootClass.SIGNATURES);
	addBasicClass("java.lang.NoClassDefFoundError", SootClass.SIGNATURES);
	addBasicClass("java.lang.ExceptionInInitializerError");
	addBasicClass("java.lang.RuntimeException");
	addBasicClass("java.lang.ClassNotFoundException");
	addBasicClass("java.lang.ArithmeticException");
	addBasicClass("java.lang.ArrayStoreException");
	addBasicClass("java.lang.ClassCastException");
	addBasicClass("java.lang.IllegalMonitorStateException");
	addBasicClass("java.lang.IndexOutOfBoundsException");
	addBasicClass("java.lang.ArrayIndexOutOfBoundsException");
	addBasicClass("java.lang.NegativeArraySizeException");
	addBasicClass("java.lang.NullPointerException");
	addBasicClass("java.lang.InstantiationError");
	addBasicClass("java.lang.InternalError");
	addBasicClass("java.lang.OutOfMemoryError");
	addBasicClass("java.lang.StackOverflowError");
	addBasicClass("java.lang.UnknownError");
	addBasicClass("java.lang.ThreadDeath");
	addBasicClass("java.lang.ClassCircularityError");
	addBasicClass("java.lang.ClassFormatError");
	addBasicClass("java.lang.IllegalAccessError");
	addBasicClass("java.lang.IncompatibleClassChangeError");
	addBasicClass("java.lang.LinkageError");
	addBasicClass("java.lang.VerifyError");
	addBasicClass("java.lang.NoSuchFieldError");
	addBasicClass("java.lang.AbstractMethodError");
	addBasicClass("java.lang.NoSuchMethodError");
	addBasicClass("java.lang.UnsatisfiedLinkError");

	addBasicClass("java.lang.Thread");
	addBasicClass("java.lang.Runnable");
	addBasicClass("java.lang.Cloneable");

	addBasicClass("java.io.Serializable");	

	addBasicClass("java.lang.ref.Finalizer");
    }

    public void addBasicClass(String name) {
	addBasicClass(name,SootClass.HIERARCHY);
    }
    
    public void addBasicClass(String name,int level) {
	basicclasses[level].add(name);
    }

    /** Load just the set of basic classes soot needs, ignoring those
     *  specified on the command-line. You don't need to use both this and 
     *  loadNecessaryClasses, though it will only waste time.
     */
    public void loadBasicClasses() {
    	addReflectionTraceClasses();
    	
		for(int i=SootClass.BODIES;i>=SootClass.HIERARCHY;i--) {
		    for(String name: basicclasses[i]) {
		    	// by xiaowei zhou, use app classloader to load basic classes, 20111206
//		    	tryLoadClass(name,i);
		    	tryLoadClass(name,i, ClassLoaders.appCLNum);
		    }
		}
    }

    private void addReflectionTraceClasses() {
    	CGOptions options = new CGOptions( PhaseOptions.v().getPhaseOptions("cg") );
    	String log = options.reflection_log();
    	
    	Set<String> classNames = new HashSet<String>();
    	if(log!=null && log.length()>0) {
			BufferedReader reader;
			String line="";
			try {
				reader = new BufferedReader(new InputStreamReader(new FileInputStream(log)));
				while((line=reader.readLine())!=null) {
					if(line.length()==0) continue;
					String[] portions = line.split(";",-1);
					String kind = portions[0];
					String target = portions[1];
					String source = portions[2];
					String sourceClassName = source.substring(0,source.lastIndexOf("."));
					classNames.add(sourceClassName);
					if(kind.equals("Class.forName")) {
						classNames.add(target);
					} else if(kind.equals("Class.newInstance")) {
						classNames.add(target);
					} else if(kind.equals("Method.invoke") || kind.equals("Constructor.newInstance")) {
						classNames.add(signatureToClass(target));
					} else throw new RuntimeException("Unknown entry kind: "+kind);
				}
			} catch (Exception e) {
				throw new RuntimeException("Line: '"+line+"'", e);
			}
    	}
    	
    	for (String c : classNames) {
    		addBasicClass(c, SootClass.BODIES);
		}
	}

	private List<SootClass> dynamicClasses;
    public Collection<SootClass> dynamicClasses() {
    	if(dynamicClasses==null) {
    		throw new IllegalStateException("Have to call loadDynamicClasses() first!");
    	}
        return dynamicClasses;
    }

    private void loadNecessaryClass(String name) {
        SootClass c;
    	// by xiaowei zhou tmp, there is usually no necessary class if we do not use command-line, 20111209
//        c = loadClassAndSupport(name);
        c = loadClassAndSupport(name, ClassLoaders.appCLNum);
        c.setApplicationClass();
    }
    /** Load the set of classes that soot needs, including those specified on the
     *  command-line. This is the standard way of initialising the list of
     *  classes soot should use.
     */
    public void loadNecessaryClasses() {
	loadBasicClasses();

        Iterator<String> it = Options.v().classes().iterator();

        while (it.hasNext()) {
            String name = (String) it.next();
            loadNecessaryClass(name);
        }

        loadDynamicClasses();

        for( Iterator<String> pathIt = Options.v().process_dir().iterator(); pathIt.hasNext(); ) {

            final String path = (String) pathIt.next();
            for (String cl : SourceLocator.v().getClassesUnder(path)) {
            	// by xiaowei zhou tmp, there is usually no necessary class if we do not use command-line, 20111209
//                loadClassAndSupport(cl).setApplicationClass();
                loadClassAndSupport(cl, ClassLoaders.appCLNum).setApplicationClass();
            }
        }

        prepareClasses();
        setDoneResolving();
    }

    public void loadDynamicClasses() {
        dynamicClasses = new ArrayList<SootClass>();
        HashSet<String> dynClasses = new HashSet<String>();
        dynClasses.addAll(Options.v().dynamic_class());

        for( Iterator<String> pathIt = Options.v().dynamic_dir().iterator(); pathIt.hasNext(); ) {

            final String path = (String) pathIt.next();
            dynClasses.addAll(SourceLocator.v().getClassesUnder(path));
        }

        for( Iterator<String> pkgIt = Options.v().dynamic_package().iterator(); pkgIt.hasNext(); ) {

            final String pkg = (String) pkgIt.next();
            dynClasses.addAll(SourceLocator.v().classesInDynamicPackage(pkg));
        }

        for (String className : dynClasses) {
        	// by xiaowei zhou tmp, there is usually no dynamic class if we do not use command-line, 20111209
            dynamicClasses.add( loadClassAndSupport(className, ClassLoaders.appCLNum) );
        }
        
        //remove non-concrete classes that may accidentally have been loaded
        for (Iterator<SootClass> iterator = dynamicClasses.iterator(); iterator.hasNext();) {
			SootClass c = iterator.next();
			if(!c.isConcrete()) {
				if(Options.v().verbose()) {
					G.v().out.println("Warning: dynamic class "+c.getName()+" is abstract or an interface, and it will not be considered.");
				}
				iterator.remove();
			}
		}
    }


    /* Generate classes to process, adding or removing package marked by
     * command line options. 
     */
    private void prepareClasses() {

        LinkedList<String> excludedPackages = new LinkedList<String>();
        if (Options.v().exclude() != null)
            excludedPackages.addAll(Options.v().exclude());

        if( !Options.v().include_all() ) {
            excludedPackages.add("java.");
            excludedPackages.add("sun.");
            excludedPackages.add("javax.");
            excludedPackages.add("com.sun.");
            excludedPackages.add("com.ibm.");
            excludedPackages.add("org.xml.");
            excludedPackages.add("org.w3c.");
            excludedPackages.add("org.apache.");
            excludedPackages.add("apple.awt.");
            excludedPackages.add("com.apple.");
        }

        // Remove/add all classes from packageInclusionMask as per -i option
        Chain<SootClass> processedClasses = new HashChain<SootClass>();
        while(true) {
            Chain<SootClass> unprocessedClasses = new HashChain<SootClass>(getClasses());
            unprocessedClasses.removeAll(processedClasses);
            if( unprocessedClasses.isEmpty() ) break;
            processedClasses.addAll(unprocessedClasses);
            for (SootClass s : unprocessedClasses) {
                if( s.isPhantom() ) continue;
                if(Options.v().app()) {
                    s.setApplicationClass();
                }
                if (Options.v().classes().contains(s.getName())) {
                    s.setApplicationClass();
                    continue;
                }
                for( Iterator<String> pkgIt = excludedPackages.iterator(); pkgIt.hasNext(); ) {
                    final String pkg = (String) pkgIt.next();
                    if (s.isApplicationClass()
                    && (s.getPackageName()+".").startsWith(pkg)) {
                            s.setLibraryClass();
                    }
                }
                for( Iterator<String> pkgIt = Options.v().include().iterator(); pkgIt.hasNext(); ) {
                    final String pkg = (String) pkgIt.next();
                    if ((s.getPackageName()+".").startsWith(pkg))
                        s.setApplicationClass();
                }
                if(s.isApplicationClass()) {
                    // make sure we have the support
                	// by xiaowei zhou, , 20111209
//                    loadClassAndSupport(s.getName());
                	loadClassAndSupport(s.getName(), s.defCLNumber);
                }
            }
        }
    }

    ArrayList<String> pkgList;

    public void setPkgList(ArrayList<String> list){
        pkgList = list;
    }

    public ArrayList<String> getPkgList(){
        return pkgList;
    }


    /** Create an unresolved reference to a method. */
    public SootMethodRef makeMethodRef( 
            SootClass declaringClass,
            String name,
            List<Type> parameterTypes,
            Type returnType,
            boolean isStatic ) {
        return new SootMethodRefImpl(declaringClass, name, parameterTypes,
                returnType, isStatic);
    }

    /** Create an unresolved reference to a constructor. */
    public SootMethodRef makeConstructorRef( 
            SootClass declaringClass,
            List<Type> parameterTypes) {
        return makeMethodRef(declaringClass, SootMethod.constructorName, 
                                         parameterTypes, VoidType.v(), false );
    }


    /** Create an unresolved reference to a field. */
    public SootFieldRef makeFieldRef( 
            SootClass declaringClass,
            String name,
            Type type,
            boolean isStatic) {
        return new AbstractSootFieldRef(declaringClass, name, type, isStatic);
    }
    /** Returns the list of SootClasses that have been resolved at least to 
     * the level specified. */
    public List/*SootClass*/<SootClass> getClasses(int desiredLevel) {
        List<SootClass> ret = new ArrayList<SootClass>();
        for( Iterator<SootClass> clIt = getClasses().iterator(); clIt.hasNext(); ) {
            final SootClass cl = (SootClass) clIt.next();
            if( cl.resolvingLevel() >= desiredLevel ) ret.add(cl);
        }
        return ret;
    }
    private boolean doneResolving = false;
	private boolean incrementalBuild;
    public boolean doneResolving() { return doneResolving; }
    public void setDoneResolving() { doneResolving = true; }
    public void setMainClassFromOptions() {
        if(mainClass != null) return;
        if( Options.v().main_class() != null
                && Options.v().main_class().length() > 0 ) {
        	// by xiaowei zhou, main class must be defined by the app class loader, 20111213
//            setMainClass(getSootClass(Options.v().main_class()));
            setMainClass(getSootClass(Options.v().main_class(), ClassLoaders.appCLNum));
        } else {             	
        	// try to infer a main class from the command line if none is given 
        	// by xiaowei zhou, comment out, do not infer main class , 20111213
/*        	for (Iterator<String> classIter = Options.v().classes().iterator(); classIter.hasNext();) {
                    SootClass c = getSootClass(classIter.next());
                	// by xiaowei zhou, , 20111213
                    if (c.declaresMethod ("main", new SingletonList( ArrayType.v(RefType.v("java.lang.String", ClassLoaders.appCLNum), 1) ), VoidType.v()))
                    {
                        G.v().out.println("No main class given. Inferred '"+c.getName()+"' as main class.");					
                        setMainClass(c);
                        return;
                    }
            }
        	
        	// try to infer a main class from the usual classpath if none is given 
        	for (Iterator<SootClass> classIter = getApplicationClasses().iterator(); classIter.hasNext();) {
                    SootClass c = (SootClass) classIter.next();
                	// by xiaowei zhou, , 20111213
                    if (c.declaresMethod ("main", new SingletonList( ArrayType.v(RefType.v("java.lang.String", ClassLoaders.appCLNum), 1) ), VoidType.v()))
                    {
                        G.v().out.println("No main class given. Inferred '"+c.getName()+"' as main class.");					
                        setMainClass(c);
                        return;
                    }
            }*/
        }
    }
    
    /**
     * This method returns true when in incremental build mode.
     * Other classes can query this flag and change the way in which they use the Scene,
     * depending on the flag's value.
     */
    public boolean isIncrementalBuild() {
    	return incrementalBuild;
    }
    
    public void initiateIncrementalBuild() {
    	this.incrementalBuild = true;
    }

    public void incrementalBuildFinished() {
    	this.incrementalBuild = false;
    }
}

