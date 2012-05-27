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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import soot.CompilationDeathException;
import soot.G;
import soot.SourceLocator.FoundFile;

public class OrdinaryClassPath implements ISootClassPath {
	
	private List<String> lstClasspath;
	
	public void setClasspath(String classpath) {
		lstClasspath = explodeClassPath(classpath);
	}
	
    public OrdinaryClassPath(List<String> lstClasspath) {
		super();
		this.lstClasspath = lstClasspath;
	}
    
    public OrdinaryClassPath(String strClasspath) {
		super();
		this.lstClasspath = explodeClassPath(strClasspath);
	}

	public FoundFile lookupInClassPath( String fileName ) {
    	
        for (String dir : lstClasspath) {
            FoundFile ret;
            if(isJar(dir)) {
                ret = lookupInJar(dir, fileName);
            } else {
                ret = lookupInDir(dir, fileName);
            }
            if( ret != null ) return ret;
        }
        return null;
    }
    private FoundFile lookupInDir(String dir, String fileName) {
        File f = new File( dir+File.separatorChar+fileName );
        if( f.canRead() ) {
            return new FoundFile(f);
        }
        return null;
    }
    private FoundFile lookupInJar(String jar, String fileName) {
        try {
            ZipFile jarFile = new ZipFile(jar);
            ZipEntry entry = jarFile.getEntry(fileName);
            if( entry == null ) return null;
            return new FoundFile(jarFile, entry);
        } catch( IOException e ) {
            throw new RuntimeException( "Caught IOException "+e+" looking in jar file "+jar+" for file "+fileName );
        }
    }
	
	
    private boolean isJar(String path) {
	File f = new File(path);	
	if(f.isFile() && f.canRead()) { 		
	    if(path.endsWith("zip") || path.endsWith("jar")) {
		return true;
	    } else {
		G.v().out.println("Warning: the following soot-classpath entry is not a supported archive file (must be .zip or .jar): " + path);
	    }
	}  
	return false;
    }

    protected List<String> explodeClassPath( String classPath ) {
        List<String> ret = new ArrayList<String>();

        StringTokenizer tokenizer = 
            new StringTokenizer(classPath, File.pathSeparator);
        while( tokenizer.hasMoreTokens() ) {
            String originalDir = tokenizer.nextToken();
            String canonicalDir;
            try {
                canonicalDir = new File(originalDir).getCanonicalPath();
                ret.add(canonicalDir);
            } catch( IOException e ) {
                throw new CompilationDeathException( "Couldn't resolve classpath entry "+originalDir+": "+e );
            }
        }
        return ret;
    }

}
