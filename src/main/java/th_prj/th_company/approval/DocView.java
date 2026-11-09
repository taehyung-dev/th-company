package th_prj.th_company.approval;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 결재 문서 상세 화면.
 *
 * @param canDecide   지금 내가 승인/반려할 수 있음
 * @param canWithdraw 기안자이고 아직 아무도 승인하지 않음
 * @param canEdit     보관함 문서의 기안자
 * @param canFinalApprove 전결할 수 있음 (뒤에 결재자가 남아 있고 본인 차례)
 * @param agreeing    내 차례가 합의라서 [합의]/[반려] 버튼을 보여 준다
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
		boolean canRedraft, String originDocNo, Long originId, boolean agreeing) {

	static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
	static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

	public String displaySubmittedAt() {
		return submittedAt == null ? "-" : submittedAt.format(DATE_TIME);
	}

	public String displayCompletedAt() {
		return completedAt == null ? "-" : completedAt.format(DATE_TIME);
	}

	/** 결재상태 창: 높은 사람이 위로 가도록 마지막 결재자부터 */
	public List<LineView> linesTopDown() {
		List<LineView> reversed = new ArrayList<>(lines);
		Collections.reverse(reversed);
		return reversed;
	}

	/** 결재란에 들어갈 결재자 */
	public List<LineView> approvalLines() {
		return lines.stream().filter(l -> l.kind() == LineKind.APPROVAL).toList();
	}

	/** 합의란에 들어갈 합의자 */
	public List<LineView> agreementLines() {
		return lines.stream().filter(l -> l.kind() == LineKind.AGREEMENT).toList();
	}

	/**
	 * @param stage    처리 차례. 같은 stage 는 동시에 처리한다
	 * @param parallel 같은 stage 에 다른 사람이 있음 (병렬 합의)
	 */
	public record LineView(int step, String approverName, String deptName, String gradeName,
			LineStatus status, String comment, LocalDateTime actedAt, String actedByName, boolean finalApproval,
			LineKind kind, int stage, boolean parallel) {

		public String displayActedAt() {
			return actedAt == null ? "" : actedAt.format(SHORT);
		}

		public boolean agreement() {
			return kind == LineKind.AGREEMENT;
		}

		/** 합의자는 승인 대신 합의, 결재중 대신 합의중 */
		public String statusLabel() {
			if (kind != LineKind.AGREEMENT) {
				return status.getLabel();
			}
			return switch (status) {
				case APPROVED -> "합의";
				case PENDING -> "합의중";
				default -> status.getLabel();
			};
		}

		/** 결재란 도장 글자 */
		public String stampText() {
			if (kind == LineKind.AGREEMENT) {
				return actedByName != null ? "대리합의" : "합의";
			}
			return finalApproval ? "전결" : actedByName != null ? "대결" : "승인";
		}

		/** 단계 이름: 1차, 병렬 합의는 "2차 병렬" */
		public String stageLabel() {
			return stage + "차" + (parallel ? " 병렬" : "");
		}
	}
}
