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

import java.net.URL;

import org.apache.felix.framework.Felix;
import org.osgi.framework.Bundle;

public class OSGiBundleBogoClassLoader extends BogoClassLoader {
	
	private Felix m_felix;

	public OSGiBundleBogoClassLoader(Integer number, Felix felix) {
		super(number);
		this.m_felix = felix;
	}
	
	public OSGiBundleBogoClassLoader(Integer number, ClassLoader wrappedCL, Felix felix) {
		super(number, wrappedCL);
		this.m_felix = felix;
	}

	@Override
	public Integer tryDelegate(String className) {
		String fileName = className.replace('.', '/') + ".class";
		URL url = wrappedCL.getResource(fileName);
		if (url == null) {
			return null;
		}
		String protoc = url.getProtocol();
		if("bundle".equals(protoc)) {
			String host = url.getHost();
			String bundleIdInFramework = host.substring(0, host.indexOf('.'));
			long ibIdInF = Integer.parseInt(bundleIdInFramework);
			Bundle bundle = m_felix.getBundle(ibIdInF);
			return Bundles.getBundleNum(bundle);
		} else if (url.toExternalForm().contains("clap.jar")) {
			// system bundle
			return 0;
		} else {
			return ClassLoaders.appCLNum;
		}
	}

}
