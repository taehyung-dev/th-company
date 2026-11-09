package th_prj.th_company.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 엑셀(.xlsx) 파일을 만든다. xlsx 는 XML 파일 몇 개를 zip 으로 묶은 것이라 라이브러리 없이 쓴다.
 * 시트마다 첫 줄은 굵은 머리글이고 스크롤해도 고정된다. 값은 글자와 숫자만 받는다.
 *
 * <pre>
 * XlsxWriter x = new XlsxWriter();
 * XlsxWriter.Sheet s = x.sheet("근태", 12, 10);
 * s.header("이름", "출근");
 * s.row("홍길동", "09:00");
 * byte[] file = x.toBytes();
 * </pre>
 */
public class XlsxWriter {

	public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	private final List<Sheet> sheets = new ArrayList<>();

	/** @param widths 열 너비 (글자 수 기준). 모자라는 열은 기본 너비 */
	public Sheet sheet(String name, double... widths) {
		Sheet s = new Sheet(safeName(name, sheets.size() + 1), widths);
		sheets.add(s);
		return s;
	}

	public static class Sheet {
		private final String name;
		private final double[] widths;
		private final List<Object[]> rows = new ArrayList<>();
		private boolean hasHeader;

		private Sheet(String name, double[] widths) {
			this.name = name;
			this.widths = widths;
		}

		/** 첫 줄에만 쓴다 */
		public Sheet header(String... titles) {
			if (!rows.isEmpty()) {
				throw new IllegalStateException("머리글은 첫 줄에만 쓸 수 있습니다.");
			}
			rows.add(titles);
			hasHeader = true;
			return this;
		}

		/** 값: String, Number, null(빈 칸). 그 밖의 값은 toString() */
		public Sheet row(Object... values) {
			rows.add(values);
			return this;
		}
	}

	public byte[] toBytes() {
		if (sheets.isEmpty()) {
			sheet("Sheet1");
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
			put(zip, "[Content_Types].xml", contentTypes());
			put(zip, "_rels/.rels", """
				<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
				<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
				<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
				</Relationships>""");
			put(zip, "xl/workbook.xml", workbook());
			put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
			put(zip, "xl/styles.xml", STYLES);
			for (int i = 0; i < sheets.size(); i++) {
				put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	// ---------- XML ----------

	/** 스타일 0: 기본, 1: 머리글 (굵게, 회색 바탕, 테두리) */
	private static final String STYLES = """
		<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
		<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
		<fonts count="2"><font><sz val="11"/><name val="맑은 고딕"/></font><font><b/><sz val="11"/><name val="맑은 고딕"/></font></fonts>\
		<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>\
		<fill><patternFill patternType="solid"><fgColor rgb="FFF2F3F5"/><bgColor indexed="64"/></patternFill></fill></fills>\
		<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border>\
		<border><left/><right/><top/><bottom style="thin"><color rgb="FFC9CED8"/></bottom><diagonal/></border></borders>\
		<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
		<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
		<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/></cellXfs>\
		<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>\
		</styleSheet>""";

	private String contentTypes() {
		StringBuilder sb = new StringBuilder("""
			<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
			<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
			<Default Extension="xml" ContentType="application/xml"/>\
			<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
			<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""");
		for (int i = 1; i <= sheets.size(); i++) {
			sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
				.append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
		}
		return sb.append("</Types>").toString();
	}

	private String workbook() {
		StringBuilder sb = new StringBuilder("""
			<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" \
			xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""");
		for (int i = 1; i <= sheets.size(); i++) {
			sb.append("<sheet name=\"").append(escape(sheets.get(i - 1).name)).append("\" sheetId=\"").append(i)
				.append("\" r:id=\"rId").append(i).append("\"/>");
		}
		return sb.append("</sheets></workbook>").toString();
	}

	private String workbookRels() {
		StringBuilder sb = new StringBuilder("""
			<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""");
		for (int i = 1; i <= sheets.size(); i++) {
			sb.append("<Relationship Id=\"rId").append(i)
				.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
				.append(i).append(".xml\"/>");
		}
		int styles = sheets.size() + 1;
		sb.append("<Relationship Id=\"rId").append(styles)
			.append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
		return sb.append("</Relationships>").toString();
	}

	private static String sheetXml(Sheet s) {
		StringBuilder sb = new StringBuilder("""
			<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
			<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""");
		if (s.hasHeader) {
			sb.append("<sheetViews><sheetView workbookViewId=\"0\">")
				.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>")
				.append("</sheetView></sheetViews>");
		}
		if (s.widths.length > 0) {
			sb.append("<cols>");
			for (int i = 0; i < s.widths.length; i++) {
				sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
					.append("\" width=\"").append(s.widths[i]).append("\" customWidth=\"1\"/>");
			}
			sb.append("</cols>");
		}
		sb.append("<sheetData>");
		for (int r = 0; r < s.rows.size(); r++) {
			int style = s.hasHeader && r == 0 ? 1 : 0;
			sb.append("<row r=\"").append(r + 1).append("\">");
			Object[] values = s.rows.get(r);
			for (int c = 0; c < values.length; c++) {
				Object v = values[c];
				if (v == null && style == 0) {
					continue;
				}
				String ref = column(c) + (r + 1);
				sb.append("<c r=\"").append(ref).append('"');
				if (style != 0) {
					sb.append(" s=\"").append(style).append('"');
				}
				if (v instanceof Number n && style == 0) {
					sb.append("><v>").append(number(n)).append("</v></c>");
				} else {
					sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
						.append(escape(v == null ? "" : v.toString())).append("</t></is></c>");
				}
			}
			sb.append("</row>");
		}
		return sb.append("</sheetData></worksheet>").toString();
	}

	/** 0 → A, 25 → Z, 26 → AA */
	static String column(int index) {
		StringBuilder sb = new StringBuilder();
		for (int n = index + 1; n > 0; n = (n - 1) / 26) {
			sb.insert(0, (char) ('A' + (n - 1) % 26));
		}
		return sb.toString();
	}

	private static String number(Number n) {
		double d = n.doubleValue();
		return d == Math.rint(d) && !Double.isInfinite(d) ? Long.toString((long) d) : Double.toString(d);
	}

	/** XML 에 쓸 수 없는 제어 문자는 뺀다 */
	static String escape(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			switch (ch) {
				case '&' -> sb.append("&amp;");
				case '<' -> sb.append("&lt;");
				case '>' -> sb.append("&gt;");
				case '"' -> sb.append("&quot;");
				default -> {
					if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
						sb.append(ch);
					}
				}
			}
		}
		return sb.toString();
	}

	/** 시트 이름: 31자까지, : \ / ? * [ ] 는 쓸 수 없다 */
	private static String safeName(String name, int index) {
		String n = name == null ? "" : name.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
		if (n.isEmpty()) {
			n = "Sheet" + index;
		}
		return n.length() > 31 ? n.substring(0, 31) : n;
	}

	private static void put(ZipOutputStream zip, String name, String xml) throws IOException {
		zip.putNextEntry(new ZipEntry(name));
		zip.write(xml.getBytes(StandardCharsets.UTF_8));
		zip.closeEntry();
	}

	/** 내려받기 응답. 파일 이름은 한글이어도 된다 */
	public ResponseEntity<byte[]> download(String fileName) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
			.contentType(MediaType.parseMediaType(CONTENT_TYPE))
			.body(toBytes());
	}
}
