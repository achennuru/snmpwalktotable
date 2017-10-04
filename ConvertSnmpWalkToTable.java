import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

public class ConvertSnmpWalkToTable {

	private static class Node {
		List<Node> children;
		String label;
		Node parent;

		public Node(String label, List<Node> children, Node parent) {
			super();
			this.label = label;
			this.children = children;
			this.parent = parent;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Node other = (Node) obj;
			if (label == null) {
				if (other.label != null)
					return false;
			} else if (!label.equals(other.label))
				return false;
			if (parent == null) {
				if (other.parent != null)
					return false;
			} else if (!parent.equals(other.parent))
				return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((label == null) ? 0 : label.hashCode());
			result = prime * result + ((parent == null) ? 0 : parent.hashCode());
			return result;
		}

		@Override
		public String toString() {
			return "Node [label=" + label + ", children=" + children + "]";
		}
	}

	private static class Table {
		List<String> columns = new ArrayList<>();
		Map<String, Map<String, String>> rows = new HashMap<>();
		String tableName;

		public Table(final String name) {
			this.tableName = name;
		}
	}

	private static class TableBuilder {
		List<String[]> rows = new LinkedList<String[]>();

		public void addRow(String... cols) {
			rows.add(cols);
		}

		private int[] colWidths() {
			int cols = -1;

			for (String[] row : rows)
				cols = Math.max(cols, row.length);

			int[] widths = new int[cols];

			for (String[] row : rows) {
				for (int colNum = 0; colNum < row.length; colNum++) {
					widths[colNum] = Math.max(widths[colNum], StringUtils.length(row[colNum]));
				}
			}

			return widths;
		}

		@Override
		public String toString() {
			StringBuilder buf = new StringBuilder();

			int[] colWidths = colWidths();

			for (String[] row : rows) {
				for (int colNum = 0; colNum < row.length; colNum++) {
					buf.append(StringUtils.rightPad(StringUtils.defaultString(row[colNum]), colWidths[colNum]));
					buf.append(' ');
				}

				buf.append('\n');
			}

			return buf.toString();
		}

	}

	private static Node getChild(Node current, Node node) {
		for (Node child : current.children) {
			if (child.equals(node)) {
				return child;
			}
		}
		return null;
	}

	private static String getPath(Node row) {
		Stack<String> stack = new Stack<>();
		populatePath(stack, row);
		StringBuilder builder = new StringBuilder();
		while (!stack.isEmpty()) {
			builder.append(stack.pop() + ".");
		}
		if (builder.toString().length() > 0) {
			return builder.toString().substring(0, builder.toString().length() - 1);
		} else {
			return "";
		}
	}

	public static void main(String[] args) throws IOException {
		final String file = args[0];
		final List<String> lines = FileUtils.readLines(new File(file));
		final Map<String, String> keyValues = new HashMap<>();
		String previous = "";
		for (String line : lines) {
			line = line.replaceAll("::", ".");
			String[] split = line.split("=");
			if (split.length == 2) {
				previous = split[0];
				keyValues.put(previous, split[1]);
			} else {
				keyValues.put(previous, keyValues.get(previous) + " " + line);
			}
		}

		final Map<String, Table> tables = new HashMap<>();
		Node root = new Node("ROOT", new ArrayList<>(), null);
		for (String key : keyValues.keySet()) {
			String[] split = key.split("\\.");
			Node current = null;
			for (String s : split) {
				if (current == null) {
					current = root;
				}
				Node node = new Node(s, new ArrayList<>(), current);
				if (!current.children.contains(node)) {
					current.children.add(node);
				} else {
					Node child = getChild(current, node);
					if (child == null) {
						child = node;
					}
					node = child;
				}
				current = node;
			}
		}
		List<Node> leafNodes = new ArrayList<>();
		populateLeafNodes(leafNodes, root);
		// Leaf = Row
		// Leaf Parent = Column
		// Leaf Parent Parent = Table
		List<Node> nodeTables = new ArrayList<>();
		for (Node leaf : leafNodes) {
			if (!nodeTables.contains(leaf.parent.parent)) {
				nodeTables.add(leaf.parent.parent);
			}
		}
		for (Node nodeTable : nodeTables) {
			String tableName = getPath(nodeTable);
			tables.put(tableName, new Table(tableName));
			final Map<String, Map<String, String>> rowMappings = new HashMap<>();
			if (nodeTable == null) {
				continue;
			}
			for (Node column : nodeTable.children) {
				if (!tables.get(tableName).columns.contains(column.label)) {
					tables.get(tableName).columns.add(column.label);
				}
				for (Node row : column.children) {
					Map<String, String> mapping = rowMappings.get(row.label);
					if (mapping == null) {
						mapping = new HashMap<>();
						rowMappings.put(row.label, mapping);
					}
					mapping.put(column.label, keyValues.get(getPath(row)));
				}

			}
			tables.get(tableName).rows.putAll(rowMappings);
		}
		String type = "text";
		if (args.length == 2) {
			type = args[1];
		}
		switch (type) {
		case "text":
			printCLITable(tables);
			break;
		case "html":
			printHTMLTable(tables);
			break;
		}
	}

	private static void printCLITable(final Map<String, Table> tables) {
		for (Table table : tables.values()) {
			if (table.tableName == null || table.tableName.length() == 0) {
				continue;
			}
			System.out.println("=================================================================================");
			System.out.println(table.tableName);
			System.out.println("=================================================================================");
			TableBuilder builder = new TableBuilder();
			String[] columns = table.columns.toArray(new String[table.columns.size()]);
			builder.addRow(columns);
			String[] line = new String[columns.length];
			for (int i = 0; i < columns.length; i++) {
				line[i] = "-----------------";
			}
			builder.addRow(line);
			for (Map<String, String> row : table.rows.values()) {
				String[] rowArray = new String[columns.length];
				for (int i = 0; i < columns.length; i++) {
					rowArray[i] = row.get(columns[i]);
				}
				builder.addRow(rowArray);
			}
			System.out.println(builder.toString());
		}
	}

	private static void printHTMLTable(final Map<String, Table> tables) {
		String style = "<style>body {	background: #fafafa url(https://jackrugile.com/images/misc/noise-diagonal.png);	color: #444;	font: 100%/30px 'Helvetica Neue', helvetica, arial, sans-serif;	text-shadow: 0 1px 0 #fff;}strong {	font-weight: bold; }em {	font-style: italic; }table {	background: #f5f5f5;	border-collapse: separate;	box-shadow: inset 0 1px 0 #fff;	font-size: 12px;	line-height: 24px;	#margin: 30px auto;	text-align: left;	width: 800px;}	th {	background: url(https://jackrugile.com/images/misc/noise-diagonal.png), linear-gradient(#777, #444);	border-left: 1px solid #555;	border-right: 1px solid #777;	border-top: 1px solid #555;	border-bottom: 1px solid #333;	box-shadow: inset 0 1px 0 #999;	color: #fff;  font-weight: bold;	padding: 10px 15px;	position: relative;	text-shadow: 0 1px 0 #000;	}th:after {	background: linear-gradient(rgba(255,255,255,0), rgba(255,255,255,.08));	content: '';	display: block;	height: 25%;	left: 0;	margin: 1px 0 0 0;	position: absolute;	top: 25%;	width: 100%;}th:first-child {	border-left: 1px solid #777;		box-shadow: inset 1px 1px 0 #999;}th:last-child {	box-shadow: inset -1px 1px 0 #999;}td {	border-right: 1px solid #fff;	border-left: 1px solid #e8e8e8;	border-top: 1px solid #fff;	border-bottom: 1px solid #e8e8e8;	padding: 10px 15px;	position: relative;	transition: all 300ms;}td:first-child {	box-shadow: inset 1px 0 0 #fff;}	td:last-child {	border-right: 1px solid #e8e8e8;	box-shadow: inset -1px 0 0 #fff;}	tr {	background: url(https://jackrugile.com/images/misc/noise-diagonal.png);	}tr:nth-child(odd) td {	background: #f1f1f1 url(https://jackrugile.com/images/misc/noise-diagonal.png);	}tr:last-of-type td {	box-shadow: inset 0 -1px 0 #fff; }tr:last-of-type td:first-child {	box-shadow: inset 1px -1px 0 #fff;}	tr:last-of-type td:last-child {	box-shadow: inset -1px -1px 0 #fff;}	</style>";
		System.out.println("<html><head><title>SNMP Walk to Tables</title>" + style + "</head><body>");
		for (Table table : tables.values()) {
			if (table.tableName == null || table.tableName.length() == 0) {
				continue;
			}
			System.out.println("<h2>");
			System.out.println(table.tableName);
			System.out.println("</h2>");
			System.out.println("<hr>");
			System.out.println("<table><tr>");
			for (String column : table.columns) {
				System.out.println("<th>" + column + "</th>");
			}
			System.out.println("</tr>");
			for (Map<String, String> row : table.rows.values()) {
				System.out.println("<tr>");
				for (int i = 0; i < table.columns.size(); i++) {
					System.out.println("<td>" + row.get(table.columns.get(i)) + "</td>");
				}
				System.out.println("</tr>");
			}
		}
		System.out.println("</body></html>");
	}

	private static void populateLeafNodes(List<Node> leafNodes, Node root) {
		for (Node child : root.children) {
			if (child.children.isEmpty()) {
				leafNodes.add(child);
			} else {
				populateLeafNodes(leafNodes, child);
			}
		}
	}

	private static void populatePath(Stack<String> stack, Node row) {
		if (row == null || row.label.equals("ROOT")) {
			return;
		}
		stack.push(row.label);
		if (row.parent != null) {
			populatePath(stack, row.parent);
		}
	}
}
