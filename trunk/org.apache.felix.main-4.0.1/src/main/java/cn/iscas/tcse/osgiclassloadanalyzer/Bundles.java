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

import org.osgi.framework.Bundle;

public class Bundles {
	private static Map<Integer, Bundle> numToBundle = new HashMap<Integer, Bundle>();
	private static Map<Bundle, Integer> bundleToNum = new HashMap<Bundle, Integer>();
	
	public static void addBundle(Integer num, Bundle bundle) {
		numToBundle.put(num, bundle);
		bundleToNum.put(bundle, num);
	}
	
	public static Map<Integer, Bundle> getNumToBundle() {
		return numToBundle;
	}

	public static Map<Bundle, Integer> getBundleToNum() {
		return bundleToNum;
	}
	
	public static Bundle getBundle(Integer num) {
		return numToBundle.get(num);
	}
	
	public static Integer getBundleNum(Bundle bundle) {
		return bundleToNum.get(bundle);
	}
	
	public static String getBundleStr(Integer bundleNum) {
		String bundleStr;
		if(bundleNum==-1) {
			bundleStr = "\"JRE System Library\"";
		} else {
			Bundle bundle = Bundles.getBundle(bundleNum);
			String bundleLoc = bundle.getLocation();
			String bundleFile = bundleLoc
					.substring(bundleLoc.lastIndexOf("/") + 1);
			bundleStr = "\"" + bundle.getSymbolicName()+" ("+bundle.getVersion()+")\" file name \""+bundleFile + "\"";
		}
		return bundleStr;
	}
}
