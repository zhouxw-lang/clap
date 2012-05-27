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

public abstract class BogoClassLoader extends ClassLoader {
	protected Integer number;
	
	protected ClassLoader wrappedCL;

	public BogoClassLoader(Integer number, ClassLoader wrappedCL) {
		super();
		this.number = number;
		this.wrappedCL = wrappedCL;
	}

	public BogoClassLoader(Integer number) {
		super();
		this.number = number;
	}

	public ClassLoader getWrappedCL() {
		return wrappedCL;
	}

	public void setWrappedCL(ClassLoader wrappedCL) {
		this.wrappedCL = wrappedCL;
	}

	public Integer getNumber() {
		return number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}
	
	// get the prospective defining class loader of a  class
	public abstract Integer tryDelegate(String className);
}
