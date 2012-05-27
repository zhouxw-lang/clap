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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class OutputUtil {
	private static final String LC_VIOLATION_OUT_FILE = "LoadingConstraintViolations.txt";
	
	private static final String TYPEERR_OUT_FILE = "RuntimeTypeErrors.txt";
	
	private static final String SNCLASSES_OUT_FILE = "SameNamedClasses.txt";
	
	private static final String OUT_FOLDER = System.getProperty("OutputFolder");
	
	private static OutputStream outLCVios;
	
	private static PrintWriter writerLCVios;
	
	private static OutputStream outTypeErrs;
	
	private static PrintWriter writerTypeErrs;
	
	private static OutputStream outSNClses;
	
	private static PrintWriter writerSNClses;
	
	public static void init() throws FileNotFoundException {
		outLCVios = new FileOutputStream(new File(OUT_FOLDER, LC_VIOLATION_OUT_FILE));
		writerLCVios = new PrintWriter(outLCVios);
		outTypeErrs = new FileOutputStream(new File(OUT_FOLDER, TYPEERR_OUT_FILE));
		writerTypeErrs = new PrintWriter(outTypeErrs);
		outSNClses = new FileOutputStream(new File(OUT_FOLDER, SNCLASSES_OUT_FILE));
		writerSNClses = new PrintWriter(outSNClses);
	}
	
	public static PrintWriter getWriterLCVios() {
		return writerLCVios;
	}
	
	public static PrintWriter getWriterTypeErrs() {
		return writerTypeErrs;
	}
	
	// output a loading constraint violation
	public static void outLCVio(String str) {
		writerLCVios.print(str);
	}
	
	// output a loading constraint violation with a line break
	public static void outLCVioln(String str) {
		writerLCVios.println(str);
	}
	
	public static void outLCVioln() {
		writerLCVios.println();
	}
	
	public static void flushLCVio() {
		writerLCVios.flush();
	}
	
	// output a runtime type error
	public static void outTypeErr(String str) {
		writerTypeErrs.print(str);
	}
	
	// output a runtime type error with a line break
	public static void outTypeErrln(String str) {
		writerTypeErrs.println(str);
	}
	
	public static void outTypeErrln() {
		writerTypeErrs.println();
	}
	
	public static void flushTypeErr() {
		writerTypeErrs.flush();
	}
	
	// output a same named class
	public static void outSNClass(String str) {
		writerSNClses.print(str);
	}
	
	// output a same named class with a line break
	public static void outSNClassln(String str) {
		writerSNClses.println(str);
	}
	
	public static void outSNClassln() {
		writerSNClses.println();
	}
	
	public static void flushSNClass() {
		writerSNClses.flush();
	}
	
	public static void destroy() {
		if (writerLCVios != null) {
			try {
				writerLCVios.close();
			} catch (Exception e) {
			}
		}
		if (outLCVios != null) {
			try {
				outLCVios.close();
			} catch (Exception e) {
			}
		}
		if (writerTypeErrs != null) {
			try {
				writerTypeErrs.close();
			} catch (Exception e) {
			}
		}
		if (outTypeErrs != null) {
			try {
				outTypeErrs.close();
			} catch (Exception e) {
			}
		}
		if (writerSNClses != null) {
			try {
				writerSNClses.close();
			} catch (Exception e) {
			}
		}
		if (outSNClses != null) {
			try {
				outSNClses.close();
			} catch (Exception e) {
			}
		}
	}
}
