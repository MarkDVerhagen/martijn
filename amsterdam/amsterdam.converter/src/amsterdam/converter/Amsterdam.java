package amsterdam.converter;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import aQute.lib.converter.Converter;
import aQute.lib.strings.Strings;

public class Amsterdam {

	static class Record {
		public String	ZAAKNAAM;
		public int		XCOORD;
		public int		YCOORD;
		public int		WERKOPP;
		public double	score_maartje;
		public double	m2_score;
		public double	impact_factor;

		@Override
		public String toString() {
			return "Record [ZAAKNAAM=" + ZAAKNAAM + ", XCOORD=" + XCOORD + ", YCOORD="
					+ YCOORD + ", WERKOPP=" + WERKOPP + ", score_maarje=" + score_maartje
					+ ", m2_score="
					+ m2_score + ", impact_factor=" + impact_factor + "]";
		}
	}

	static double[] distanceWeight = { 1, 0.7, 0.3, 0.0, 0.0, 0 };

	static class Stats {
		@Override
		public String toString() {
			return "Stats [left=" + left + ", right=" + right + ", top=" + top + ", bottom=" + bottom + "]";
		}

		int			left		= Integer.MAX_VALUE;
		int			right		= 0;
		int			top			= 0;
		int			bottom		= Integer.MAX_VALUE;

		double[][]	convolution	= new double[11][11];
		double[][]	result;

		public void init() {

			System.out.println("Calcuulating 5x5 convulation matrix on distance weights: " + Arrays.toString(distanceWeight));
			
			for (int y = -5; y <= 5; y++) {
				for (int x = -5; x <= 5; x++) {
					double dist = Math.sqrt(x * x + y * y);
					int l = (int) Math.round(dist);
					if (l > 5)
						continue;

					double weight = distanceWeight[l];
					convolution[y + 5][x + 5] = weight;
				}
			}

			left = left / 100 - 6;
			right = (right + 99) / 100 + 6;
			bottom = (bottom / 100) - 6;
			top = (top + 99) / 100 + 6;

			int height = top - bottom;
			int width = right - left;
			result = new double[height][width];
			System.out.println("w=" + width + " h=" + height + " l=" + width * height);
		}

		void weight(int x, int y, double score) {
			int gridX = ((x + 50) / 100) - left;
			int gridY = ((y + 50) / 100) - bottom;

			for (int yy = -5; yy <= 5; yy++) {
				for (int xx = -5; xx <= 5; xx++) {
					double weight = convolution[yy + 5][xx + 5];
					if (weight == 0.0D)
						continue;

					result[gridY + yy][gridX + xx] += weight * score;
				}

			}
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.out.println(
					"Converteer Amsterdamse voedsel locaties to buurt impact. Input and output moeten CSV files zijn");
			System.out.println("<tabel.csv> <output.csv>");
			return;
		}

		String input = args[0];
		File i = new File(input).getAbsoluteFile();
		if (!i.isFile()) {
			System.out.println(input + " is not an input file");
			return;
		}

		String output = args[1];
		File o = new File(output).getAbsoluteFile();
		o.getParentFile().mkdirs();

		List<Record> list = convert(input, Record.class);
		System.out.printf("%s records found%n", list.size());
		
		Stats stats = new Stats();
		list.forEach(r -> {
			stats.left = Math.min(r.XCOORD, stats.left);
			stats.right = Math.max(r.XCOORD, stats.right);
			stats.top = Math.max(r.YCOORD, stats.top);
			stats.bottom = Math.min(r.YCOORD, stats.bottom);
		});

		stats.init();

		list.forEach(r -> {
			stats.weight(r.XCOORD, r.YCOORD, r.impact_factor);
		});

		try (PrintWriter pw = new PrintWriter(output)) {
			print(stats, pw);
		}
	}

	private static void print(Stats stats, PrintWriter pw) {
		Locale.setDefault(Locale.GERMAN);

		pw.println("C28992R100;score");
		int n = 0;
		for (int y = 0; y < stats.result.length; y++) {
			for (int x = 0; x < stats.result[y].length; x++) {

				double result = stats.result[y][x];
				n++;

				if (Math.abs(result) < 0.001)
					continue;

				pw.printf("E%04dN%04d;%.2f\n", x + stats.left, y + stats.bottom, result);
			}
		}
		System.out.println("n=" + n);
	}

	static <T> List<T> convert(String fileName, Class<T> type) throws Exception {

		List<String> collect = Files.lines(Paths.get(fileName)).collect(Collectors.toList());
		List<T> records = new ArrayList<>();
		Map<String, Integer> map = new HashMap<>();
		List<String> headers = toFields(collect.remove(0));

		for (int i = 0; i < headers.size(); i++) {
			map.put(headers.get(i), i);
		}

		for (String line : collect) {
			T rcrd = convert(line, map, type);
			records.add(rcrd);
		}
		return records;
	}

	private static List<String> toFields(String line) {
		List<String> parts = Strings.split("\\s*;\\s*", line.trim());
		for (int i = 0; i < parts.size(); i++) {
			String part = parts.get(i);
			StringBuilder sb = new StringBuilder(part);

			for (int j = 0; j < sb.length(); j++) {
				char c = sb.charAt(j);
				switch (c) {
				case '\uFEFF': // don't ask ...
					sb.delete(j, j + 1);
					break;
				}

			}
			part = sb.toString();
			if (part.startsWith("\"")) {
				part = part.substring(1, part.length() - 2);
				part = part.replaceAll("\"\"", "\"");
			}
			parts.set(i, part);
		}
		return parts;
	}

	static <T> T convert(String line, Map<String, Integer> headers, Class<T> type) throws Exception {
		List<String> parts = toFields(line);
		T rcrd = type.newInstance();

		for (Field f : type.getFields()) {

			if (Modifier.isStatic(f.getModifiers()))
				continue;

			String name = f.getName();
			Integer i = headers.get(name);
			if (i == null) {
				System.out.println("No such header " + name + " in " + headers);
			} else {
				String source = parts.get(i);
				if (Number.class.isAssignableFrom(f.getType()) || f.getType().isPrimitive()) {
					source = source.replaceAll(",", ".");
				}
				Object object = Converter.cnv(f.getGenericType(), source);
				f.set(rcrd, object);
			}
		}
		return rcrd;
	}
}
