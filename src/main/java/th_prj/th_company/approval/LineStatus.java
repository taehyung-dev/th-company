package th_prj.th_company.approval;

public enum LineStatus {
	/** 앞 단계 결재를 기다리는 중 */
	WAITING("대기"),
	/** 지금 결재할 차례 */
	PENDING("결재중"),
	APPROVED("승인"),
	REJECTED("반려"),
	/** 앞 단계에서 전결해 결재하지 않고 끝남 */
	SKIPPED("전결로 생략");

	private final String label;

	LineStatus(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }
}
