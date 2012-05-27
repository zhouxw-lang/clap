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
package cn.iscas.tcse.osgiclassloadanalyzer.pointstoanalysis.forsoot;

import java.net.URL;
import java.util.Enumeration;

import org.apache.felix.framework.BundleRevisionImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRevision;

import soot.SourceLocator.FoundFile;

public class OSGiBundleClassPath implements ISootClassPath {
	
	private Bundle wrappedBundle;
	
	private BundleRevisionImpl bundleRev;

	public OSGiBundleClassPath(Bundle wrappedBundle) {
		super();
		this.wrappedBundle = wrappedBundle;
		bundleRev = (BundleRevisionImpl)wrappedBundle.adapt(BundleRevision.class);
	}

	public FoundFile lookupInClassPath(String fileName) {
		Enumeration e = bundleRev.getResourcesLocal(fileName);
		if(e!=null && e.hasMoreElements()) {
			URL url = (URL)e.nextElement();
			FoundFile ffile = new FoundFile(url);
			return ffile;
		} else {
			return null;
		}
	}

	public void setClasspath(String classpath) {
		
	}
	
	
}
