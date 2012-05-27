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

import java.util.Iterator;
import java.util.List;


import org.apache.felix.framework.BundleImpl;
import org.apache.felix.framework.Felix;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;

public class Test1 {
	public static void test1(Felix fwk) {
		Bundle[] bundles = fwk.getBundles();
		for (int i = 0; i < bundles.length; i++) {
			BundleImpl bundle = (BundleImpl) bundles[i];
			BundleRevision revision = bundle.adapt(BundleRevision.class);
			List<BundleCapability> capabilities = revision
					.getDeclaredCapabilities(null);
			System.out.println(revision.getSymbolicName());
			Iterator<BundleCapability> iterCapa = capabilities.iterator();
			while(iterCapa.hasNext()) {
				BundleCapability capa =  iterCapa.next();
//				if(capa.getNamespace().equals(BundleCapabilityImpl.BUNDLE_NAMESPACE)) {
					// 
//				}
				System.out.println(capa.getNamespace());
				System.out.println(capa.getAttributes());
				
			}
			System.out.println();
			System.out.println();
		}
	}

}
