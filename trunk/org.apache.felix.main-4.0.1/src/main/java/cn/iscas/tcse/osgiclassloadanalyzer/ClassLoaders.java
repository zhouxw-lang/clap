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

import java.util.HashMap;
import java.util.Map;

public class ClassLoaders {
	
	public static final Integer appCLNum = -1;
	 
	private static Map<Integer, BogoClassLoader> mapCLs = new HashMap<Integer, BogoClassLoader>();
	
	private static BogoClassLoader appCL;
	
	public static Integer initCLToDefCL(Integer initCL, String className) {
		BogoClassLoader bogoCL = mapCLs.get(initCL);
		return bogoCL.tryDelegate(className);
	}
	
	public static void addClassLoader(Integer num, BogoClassLoader cl) {
		mapCLs.put(num, cl);
	}
	
	public static void setAppCL(BogoClassLoader cl) {
		mapCLs.put(appCLNum, cl);
	}
	
	
}
