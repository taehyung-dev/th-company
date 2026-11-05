package th_prj.th_company.menu;

/**
 * 사용자 화면 상단 메뉴. 선언 순서가 기본 순서이고, 관리자가 바꾼 순서는 menu_order 에 저장한다.
 * key 는 화면의 topbar(active) 값과 같다.
 * 조직도는 메뉴가 아니라 상단 바 오른쪽 아이콘(팝업 창)으로 연다.
 */
public enum MainMenu {

	HOME("home", "홈", "/"),
	BOARD("board", "게시판", "/boards"),
	APPROVAL("approval", "전자결재", "/approval"),
	SCHEDULE("schedule", "일정", "/schedule"),
	ROOM_RESERVATION("room", "회의실예약", "/reservations/rooms"),
	RESERVATION("reservation", "자원예약", "/reservations/resources"),
	ATTENDANCE("attendance", "근태관리", "/attendance"),
	ADDRESS("address", "주소록", "/address");

	private final String key;
	private final String label;
	/** 아직 화면이 없는 메뉴는 null */
	private final String path;

	MainMenu(String key, String label, String path) {
		this.key = key;
		this.label = label;
		this.path = path;
	}

	public String getKey() { return key; }
	public String getLabel() { return label; }
	public String getPath() { return path; }
}
