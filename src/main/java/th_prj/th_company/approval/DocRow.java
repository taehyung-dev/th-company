package th_prj.th_company.approval;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 문서함 목록의 한 줄.
 *
 * @param date            진행/미결은 기안일, 완결은 완료일, 보관함은 수정일
 * @param currentApprover 진행 중이면 지금 결재할 사람 이름
 */
public record DocRow(Long id, String docNo, String templateName, String title, String drafterName,
		LocalDateTime date, DocStatus status, String currentApprover) {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

	public String displayDate() {
		return date == null ? "" : date.format(DATE);
	}
}
