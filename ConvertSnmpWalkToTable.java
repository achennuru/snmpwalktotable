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
