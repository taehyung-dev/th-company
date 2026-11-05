package th_prj.th_company.approval;

public enum DocStatus {
	/** 임시저장 또는 회수된 문서 (보관함) */
	DRAFT("임시저장"),
	IN_PROGRESS("진행중"),
	APPROVED("완료"),
	REJECTED("반려"),
	/** 연결된 신청(휴가)을 취소해 결재를 멈춘 문서 */
	CANCELED("취소");

	private final String label;

	DocStatus(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }

	public boolean isCompleted() {
		return this == APPROVED || this == REJECTED || this == CANCELED;
	}
}
