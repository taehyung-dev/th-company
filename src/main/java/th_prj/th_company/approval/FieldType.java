package th_prj.th_company.approval;

/** 결재 양식 입력 항목의 종류 */
public enum FieldType {
	TEXT("한 줄 글"),
	TEXTAREA("여러 줄 글"),
	NUMBER("숫자"),
	MONEY("금액"),
	DATE("날짜"),
	SELECT("선택");

	private final String label;

	FieldType(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }
}
