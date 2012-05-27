/* Soot - a J*va Optimization Framework
 * Copyright (C) 2007 Manu Sridharan
 * Changed by Xiaowei Zhou for Clap, 2012
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
package soot.jimple.spark.ondemand;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

import soot.SootClass;
import soot.jimple.spark.ondemand.genericutil.Predicate;
import soot.jimple.spark.pag.AllocNode;
import soot.jimple.spark.pag.FieldRefNode;
import soot.jimple.spark.pag.Node;
import soot.jimple.spark.pag.PagToDotDumper;
import soot.jimple.spark.pag.VarNode;
import cn.iscas.tcse.osgiclassloadanalyzer.OutputUtil;

/**
 * you can just add edges and then dump them as a dot graph
 * 
 * @author Manu Sridharan
 * 
 */
public class DotPointerGraph {
	
	// by xiaowei zhou, , 20120417
	private boolean useLineNumber;

	// by xiaowei zhou, , 20120417
	public DotPointerGraph(boolean useLineNumber) {
		super();
		this.useLineNumber = useLineNumber;
	}

	private final Set<String> edges = new HashSet<String>();

	private final Set<Node> nodes = new HashSet<Node>();

	// by xiaowei zhou, , 20120417
//	public void addAssign(VarNode from, VarNode to) {
	public void addAssign(VarNode from, VarNode to, int lineNumber, SootClass inClass) {
		// by xiaowei zhou, , 20120228
//		addEdge(to, from, "", "black");
		addEdge(to, from, "", "assign edge", lineNumber, inClass);
	}

	// by xiaowei zhou, , 20120417
//	private void addEdge(Node from, Node to, String edgeLabel, String color) {
	private void addEdge(Node from, Node to, String edgeLabel, String color, int lineNumber, SootClass inClass) {
		nodes.add(from);
		nodes.add(to);
		// by xiaowei zhou, , 20120417
//		addEdge(PagToDotDumper.makeNodeName(from), PagToDotDumper
//				.makeNodeName(to), edgeLabel, color);
		addEdge(PagToDotDumper.makeNodeName(from), PagToDotDumper
				.makeNodeName(to), edgeLabel, color, lineNumber, inClass);
	}
	
	// by xiaowei zhou, , 20120417
	private void addEdge(Node from, Node to, String edgeLabel, String color) {
		addEdge(from, to, edgeLabel, color, 0, null);
	}

	private void addEdge(String from, String to, String edgeLabel, String color, int lineNumber, SootClass inClass) {
		StringBuffer tmp = new StringBuffer();
		tmp.append("    ");
		tmp.append(from);
		tmp.append(" -> ");
		tmp.append(to);
		tmp.append(" [label=\"");
		tmp.append(edgeLabel);
		
		// by xiaowei zhou, , 20120228
//		tmp.append("\", color=");
		tmp.append("\", category=");
		
		tmp.append(color);
		tmp.append("];");
		if(useLineNumber) {
			if(lineNumber!=0) {
				tmp.append(" at line \"" + lineNumber + "\"");
			}
		}
		edges.add(tmp.toString());
	}
	
	// by xiaowei zhou, , 20120417
	private void addEdge(String from, String to, String edgeLabel, String color) {
		addEdge(from, to, edgeLabel, color, 0, null);
	}

	// by xiaowei zhou, , 20120417
//	public void addNew(AllocNode from, VarNode to) {
	public void addNew(AllocNode from, VarNode to, int lineNumber, SootClass inClass) {
		// by xiaowei zhou, , 20120228
//		addEdge(to, from, "", "yellow");
		// by xiaowei zhou, , 20120417
//		addEdge(to, from, "", "allocation edge");
		addEdge(to, from, "", "allocation edge", lineNumber, inClass);
	}

	// by xiaowei zhou, , 20120417
//	public void addCall(VarNode from, VarNode to, Integer callSite) {
	public void addCall(VarNode from, VarNode to, Integer callSite, int lineNumber, SootClass inClass) {
		// by xiaowei zhou, , 20120228
//		addEdge(to, from, callSite.toString(), "blue");
		addEdge(to, from, callSite.toString(), "call edge", lineNumber, inClass);
	}

	public void addMatch(VarNode from, VarNode to) {
		// by xiaowei zhou, , 20120228
//		addEdge(to, from, "", "brown");
		// by xiaowei zhou, , 20120417
//		addEdge(to, from, "", "match edge (special for demand-driven points-to analysis)");
		addEdge(to, from, "", "match edge (special for demand-driven points-to analysis)");
	}

	public void addLoad(FieldRefNode from, VarNode to) {
		// by xiaowei zhou, , 20120228
//		addEdge(to, from.getBase(), from.getField().toString(), "green");
		addEdge(to, from.getBase(), from.getField().toString(), "field load edge");
	}

	public void addStore(VarNode from, FieldRefNode to) {
		// by xiaowei zhou, , 20120228
//		addEdge(to.getBase(), from, to.getField().toString(), "red");
		addEdge(to.getBase(), from, to.getField().toString(), "field store edge");
	}

	public int numEdges() {
		return edges.size();
	}
	
	public void dump(String filename) {
		PrintWriter pw = null;
		try {
			pw = new PrintWriter(new FileOutputStream(filename));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		// pw.println("digraph G {\n\trankdir=LR;");
		pw.println("digraph G {");
		Predicate<Node> falsePred = new Predicate<Node>() {

			@Override
			public boolean test(Node obj_) {
				return false;
			}

		};
		for (Node node : nodes) {
			pw.println(PagToDotDumper.makeDotNodeLabel(node, falsePred));
		}
		for (String edge : edges) {
			pw.println(edge);
		}
		pw.println("}");
		pw.close();

	}
	
	// by xiaowei zhou, , 20120319
	public void dump() {
		OutputUtil.outTypeErrln("digraph G {");
		Predicate<Node> falsePred = new Predicate<Node>() {

			@Override
			public boolean test(Node obj_) {
				return false;
			}

		};
		OutputUtil.outTypeErrln("Nodes:");
		for (Node node : nodes) {
			OutputUtil.outTypeErrln(PagToDotDumper.makeDotNodeLabel(node, falsePred));
			OutputUtil.outTypeErrln();
		}
		OutputUtil.outTypeErrln("Edges:");
		for (String edge : edges) {
			OutputUtil.outTypeErrln(edge);
		}
		OutputUtil.outTypeErrln("}");

	}
}
