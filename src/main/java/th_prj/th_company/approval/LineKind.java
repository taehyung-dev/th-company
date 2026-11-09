package th_prj.th_company.approval;

/**
 * 결재선 한 사람의 역할. 결재자는 승인·반려·전결을 하고, 합의자는 동의(합의)하거나 반려한다.
 */
public enum LineKind {
	APPROVAL("결재"),
	AGREEMENT("합의");

	private final String label;

	LineKind(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }
}
