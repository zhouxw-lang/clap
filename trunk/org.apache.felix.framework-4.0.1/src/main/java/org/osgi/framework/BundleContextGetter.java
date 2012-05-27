/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.osgi.framework;

import org.apache.felix.framework.BundleContextImpl;
import org.apache.felix.framework.BundleImpl;
import org.apache.felix.framework.Felix;

// by xiaowei zhou, these code is not for running, just for being scanned by the program analyzer, 20120222
public class BundleContextGetter {
	
	public static Felix m_felix = new Felix(null);
	
	public static BundleContext getBundleContext() {
		BundleImpl bundle = new BundleImpl();
		BundleContextImpl context = new BundleContextImpl(null, m_felix, bundle);
		return context;
	}
}
