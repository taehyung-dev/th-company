package th_prj.th_company.schedule;

/**
 * 일정 종류. css 는 달력 색 구분용 클래스 접미사 (sch-company 등).
 */
public enum ScheduleType {
	COMPANY("회사일정", "company"),
	DEPARTMENT("부서일정", "dept"),
	PERSONAL("개인일정", "personal");

	private final String label;
	private final String css;

	ScheduleType(String label, String css) {
		this.label = label;
		this.css = css;
	}

	public String getLabel() { return label; }
	public String getCss() { return css; }
}
