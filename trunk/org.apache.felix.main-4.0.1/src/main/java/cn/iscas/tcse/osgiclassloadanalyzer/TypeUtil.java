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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import soot.ArrayType;
import soot.FastHierarchy;
import soot.RefLikeType;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootField;
import soot.Type;
import soot.util.Chain;

public class TypeUtil {
	private static Map<String, Set<Type>> sameNamedTypes;
	
	private static Set<SootClass> carryClasses;
	
	public static boolean isSameNamedType(String className) {
		return sameNamedTypes.containsKey(className);
	}
	
	public static boolean isSameNamedType(Type type) {
		RefType refT = getCheckableRefType(type);
		if(refT!=null) {
			return isSameNamedType(refT.getClassName());
		} else {
			return false;
		}
	}
	
	public static Set<Type> getSNClassByName(String className) {
		return sameNamedTypes.get(className);
	}
	
	public static boolean isCarryClass(Type type) {
		RefType refT = getCheckableRefType(type);
		if (refT != null) {
			return carryClasses.contains(refT.getSootClass());
		} else {
			return false;
		}
	}
	
	public static RefType getCheckableRefType(Type type) {
		RefType refType = null;
		if (type instanceof RefLikeType) {
			if (type instanceof ArrayType) {
				// for arraytype, we get its base type
				Type baseType = ((ArrayType) type).baseType;
				if (baseType instanceof RefType) {
					refType = (RefType) baseType;
				}
			} else if (type instanceof RefType) {
				refType = (RefType) type;
			}
		}
		return refType;
	}
	
	public static void initSameNamedTypesAndCarryClasses() {
		// find all same named classes
		sameNamedTypes = new HashMap<String, Set<Type>>();
		carryClasses = new HashSet<SootClass>();
		
		Set<SootClass> carryClassesAndSupers =  new HashSet<SootClass>();
		
		Map<String, Set<Type>> nameToTypes = Scene.v().getNameToClasses();
		Iterator<Entry<String, Set<Type>>> iterEnts = nameToTypes.entrySet()
				.iterator();
		while (iterEnts.hasNext()) {
			Entry<String, Set<Type>> entry = iterEnts.next();
			Set<Type> setType = entry.getValue();
			if (setType != null && setType.size() > 1) {
				// same named class found!
				sameNamedTypes.put(entry.getKey(), setType);
				
				// add same named classes to carry class set
				for (Type type : setType) {
					RefType refT = getCheckableRefType(type);
					if (refT != null) {
						SootClass sootc = refT.getSootClass();
						if (sootc.isConcrete() && !sootc.isPhantom()) {
							carryClasses.add(sootc);
							addClsAndSupersToSet(carryClassesAndSupers, sootc);
						}
					}
				}
			}
		}
		
		Scene cm = Scene.v();
		FastHierarchy fastHierarchy = cm.getOrMakeFastHierarchy();
		Set<SootClass> toAddToCarryClasses = new HashSet<SootClass>();
		boolean newClassAdded;
		
		do {
			newClassAdded = false;
			// add carry classes' subclasses into the carry class set
			for (SootClass c : carryClasses) {
				Collection<SootClass> subClses = fastHierarchy
						.getSubclassesOf(c);
				for (SootClass subCls : subClses) {
					if (!carryClasses.contains(subCls)) {
						toAddToCarryClasses.add(subCls);
					}
				}
			}
			
			// add classes which declares fields having type of a carry class or its super classes to carry class set
			Chain<SootClass> allClasses = cm.getClasses();
			for (SootClass cls : allClasses) {
				Chain<SootField> fields = cls.getFields();
				for (SootField field : fields) {
					Type fieldType = field.getType();
					RefType fieldRefT = getCheckableRefType(fieldType);
					if (fieldRefT != null) {
						// contravariant for fields
						SootClass fieldClass = fieldRefT.getSootClass();
						if (carryClassesAndSupers.contains(fieldClass) && !carryClasses.contains(cls)) {
							toAddToCarryClasses.add(cls);
							break;
						}
					}
				}
			}
			
			if (!toAddToCarryClasses.isEmpty()) {
				newClassAdded = true;
				carryClasses.addAll(toAddToCarryClasses);
				for (SootClass cls : toAddToCarryClasses) {
					addClsAndSupersToSet(carryClassesAndSupers, cls);
				}
				toAddToCarryClasses.clear();
			}

		} while (newClassAdded);
		
		
		
		
		
		
	}
	
//	public static void initSameNamedTypes() {
//		// find all same named classes
//		sameNamedTypes = new HashMap<String, Set<Type>>();
//		Map<String, Set<Type>> nameToTypes = Scene.v().getNameToClasses();
//		Iterator<Entry<String, Set<Type>>> iterEnts = nameToTypes.entrySet()
//				.iterator();
//		while (iterEnts.hasNext()) {
//			Entry<String, Set<Type>> entry = iterEnts.next();
//			Set<Type> setType = entry.getValue();
//			if (setType != null && setType.size() > 1) {
//				// same named class found!
//				sameNamedTypes.put(entry.getKey(), setType);
//			}
//		}
//	}
	
	public static Map<String, Set<Type>> getSameNamedTypes() {
		return sameNamedTypes;
	}
	
	private static void addClsAndSupersToSet(Set<SootClass> set, SootClass cls) {
		SootClass clsSuper = cls;
		set.add(cls);
		while(clsSuper.hasSuperclass()) {
			clsSuper = clsSuper.getSuperclass();
			set.add(clsSuper);
		}
	}
}
