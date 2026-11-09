package th_prj.th_company.admin;

/**
 * 관리자 화면 상단 구분. 선언 순서가 기본 순서이고, 관리자가 바꾼 순서는 admin_menu_order 에 저장한다.
 * 어떤 구분이 보이는지는 권한에 따라 AdminNavService 가 정한다.
 */
public enum AdminSection {

	HR("인사정보", "사용자, 부서, 직급, 직책 관리"),
	PERMISSION("권한", "권한 관리"),
	ATTENDANCE("근태", "공휴일 관리, 휴가 현황"),
	BOARD("게시판", "게시판 관리"),
	APPROVAL("전자결재", "결재 양식 관리"),
	RESERVATION("예약", "회의실, 자원 관리"),
	SCREEN("화면", "사용자 메뉴 순서, 관리자 메뉴 순서");

	private final String label;
	/** 순서 화면에 보이는 설명 */
	private final String description;

	AdminSection(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String getLabel() { return label; }
	public String getDescription() { return description; }
}
