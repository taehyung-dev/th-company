package th_prj.th_company.leave;

/**
 * 휴가 종류. deducted: 연차에서 차감. half: 하루의 반 (0.5일, 시작일과 종료일이 같아야 함).
 */
public enum LeaveType {
	ANNUAL("연차", true, false),
	AM_HALF("오전반차", true, true),
	PM_HALF("오후반차", true, true),
	SICK("병가", false, false),
	FAMILY("경조사", false, false),
	OFFICIAL("공가", false, false);

	private final String label;
	private final boolean deducted;
	private final boolean half;

	LeaveType(String label, boolean deducted, boolean half) {
		this.label = label;
		this.deducted = deducted;
		this.half = half;
	}

	public String getLabel() { return label; }
	public boolean isDeducted() { return deducted; }
	public boolean isHalf() { return half; }
}
