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

import soot.Unit;
import soot.jimple.Stmt;
import soot.tagkit.LineNumberTag;

public class CodeUtil {
	public static int getlineNumber(Stmt stmt) {
		int lineNumber = 0; // "0" for no line number
		if (stmt != null) {
			Unit unit = (Unit) stmt;
			LineNumberTag tag = (LineNumberTag) unit.getTag("LineNumberTag");
			if (tag != null)
				lineNumber = tag.getLineNumber();
		}
		return lineNumber;
	}
}
