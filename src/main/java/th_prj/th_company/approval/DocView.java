package th_prj.th_company.approval;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 결재 문서 상세 화면.
 *
 * @param canDecide   지금 내가 승인/반려할 수 있음
 * @param canWithdraw 기안자이고 아직 아무도 승인하지 않음
 * @param canEdit     보관함 문서의 기안자
 * @param canFinalApprove 전결할 수 있음 (뒤에 결재자가 남아 있고 본인 차례)
 * @param deputyFor   내가 대결로 결재하면 원 결재자 이름, 아니면 null
 * @param ccNames     참조자
 * @param canRedraft  반려된 내 문서라서 재기안할 수 있음
 * @param originDocNo 재기안 문서면 원래 문서 번호
 * @param originId    재기안 문서의 원래 문서 id. 기안자에게만 준다 (다른 사람은 원문을 볼 권한이 없을 수 있다)
 */
public record DocView(Long id, String docNo, String templateName, String title, String content, List<FieldValue> fields,
		String drafterName, String drafterDept, String drafterGrade,
		DocStatus status, LocalDateTime submittedAt, LocalDateTime completedAt,
		List<LineView> lines, boolean canDecide, boolean canWithdraw, boolean canEdit,
		boolean canFinalApprove, String deputyFor, List<String> ccNames,
		boolean canRedraft, String originDocNo, Long originId) {

	static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
	static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

	public String displaySubmittedAt() {
		return submittedAt == null ? "-" : submittedAt.format(DATE_TIME);
	}

	public String displayCompletedAt() {
		return completedAt == null ? "-" : completedAt.format(DATE_TIME);
	}

	public record LineView(int step, String approverName, String deptName, String gradeName,
			LineStatus status, String comment, LocalDateTime actedAt, String actedByName, boolean finalApproval) {

		public String displayActedAt() {
			return actedAt == null ? "" : actedAt.format(SHORT);
		}
	}
}
