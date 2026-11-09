package th_prj.th_company.approval;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;

import th_prj.th_company.board.PostDetail.FileInfo;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;

/**
 * 전자결재 문서를 PDF 로 만든다. approval/pdf.html 을 Thymeleaf 로 그린 뒤 openhtmltopdf 로 바꾼다.
 * 한글은 함께 넣어 둔 나눔고딕(OFL)으로 쓰고, 쓰인 글자만 PDF 에 담는다.
 */
@Service
public class ApprovalPdfService {

	private static final String FONT = "NanumGothic";
	private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

	private final TemplateEngine templateEngine;
	private final Clock clock;
	private final MemberRepository memberRepository;

	public ApprovalPdfService(TemplateEngine templateEngine, Clock clock, MemberRepository memberRepository) {
		this.templateEngine = templateEngine;
		this.clock = clock;
		this.memberRepository = memberRepository;
	}

	/** @param loginId 내려받는 사람. 아래쪽에 출력한 사람과 시각을 적는다 */
	public byte[] render(DocView doc, List<FileInfo> files, String loginId) {
		Context context = new Context(Locale.KOREAN);
		context.setVariable("doc", doc);
		context.setVariable("files", files);
		context.setVariable("printedBy", memberRepository.findByLoginId(loginId).map(Member::getName).orElse(loginId));
		context.setVariable("printedAt", LocalDateTime.now(clock).format(PRINTED));
		String html = templateEngine.process("approval/pdf", context);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.useFont(() -> font("NanumGothic-Regular.ttf"), FONT, 400, FontStyle.NORMAL, true);
			builder.useFont(() -> font("NanumGothic-Bold.ttf"), FONT, 700, FontStyle.NORMAL, true);
			// openhtmltopdf 는 올바른 XML 만 읽으므로 jsoup 으로 읽어 DOM 으로 넘긴다
			builder.withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), "/");
			builder.toStream(out);
			builder.run();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/** PDF 파일 이름: 문서번호_제목.pdf (파일 이름에 못 쓰는 글자는 _) */
	public static String fileName(DocView doc) {
		String name = (doc.docNo() == null ? "임시문서" : doc.docNo()) + "_" + doc.title();
		return name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_").trim() + ".pdf";
	}

	private static java.io.InputStream font(String file) {
		try {
			return new ClassPathResource("fonts/" + file).getInputStream();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
