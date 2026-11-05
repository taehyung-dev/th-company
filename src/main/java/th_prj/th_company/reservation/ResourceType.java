package th_prj.th_company.reservation;

/**
 * 예약 자원 종류. css 는 화면 색 구분용 클래스 접미사 (res-room 등).
 */
public enum ResourceType {
	ROOM("회의실", "room"),
	EQUIPMENT("비품·장비", "equip"),
	VEHICLE("차량", "vehicle");

	private final String label;
	private final String css;

	ResourceType(String label, String css) {
		this.label = label;
		this.css = css;
	}

	public String getLabel() { return label; }
	public String getCss() { return css; }
}
