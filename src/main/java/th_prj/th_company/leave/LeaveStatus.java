package th_prj.th_company.leave;

public enum LeaveStatus {
	/** 전자결재 진행 중 */
	PENDING("결재중", "tag-info"),
	APPROVED("승인", "tag-ok"),
	REJECTED("반려", "tag-bad"),
	CANCELED("취소", "tag-muted");

	private final String label;
	private final String css;

	LeaveStatus(String label, String css) {
		this.label = label;
		this.css = css;
	}

	public String getLabel() { return label; }
	public String getCss() { return css; }
}
