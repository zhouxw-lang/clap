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

public class ClassNameAndDefCL {
	
	public String className;
	public Integer defCL;
	
	public ClassNameAndDefCL(String className, Integer defCL) {
		super();
		
		if (className == null || defCL == null) {
			throw new RuntimeException(
					"className or defCL must not be null when constructing ClassNameAndDefCL instance!");
		}
		
		this.className = className;
		this.defCL = defCL;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((defCL == null) ? 0 : defCL.hashCode());
		result = prime * result
				+ ((className == null) ? 0 : className.hashCode());
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
		ClassNameAndDefCL other = (ClassNameAndDefCL) obj;
		if (defCL == null) {
			if (other.defCL != null)
				return false;
		} else if (!defCL.equals(other.defCL))
			return false;
		if (className == null) {
			if (other.className != null)
				return false;
		} else if (!className.equals(other.className))
			return false;
		return true;
	}
	
	
}
