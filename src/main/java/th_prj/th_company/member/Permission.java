package th_prj.th_company.member;

/**
 * 관리자(ADMIN)가 아닌 사용자에게 기능별로 주는 관리 권한. 한 사람이 여러 개를 가질 수 있다.
 * 로그인 시 "PERM_" + name() 권한(authority)으로 실린다 (예: PERM_BOARD_MANAGER).
 */
public enum Permission {
	BOARD_MANAGER("게시판관리자", "게시판 구성 관리와 모든 게시글·댓글 관리"),
	ATTENDANCE_MANAGER("근태관리자", "전 직원 출퇴근 기록 조회와 정정"),
	SCHEDULE_MANAGER("일정관리자", "회사일정 등록과 모든 일정 수정·삭제"),
	APPROVAL_MANAGER("결재양식관리자", "전자결재 양식 만들기와 수정"),
	ROOM_MANAGER("회의실관리자", "회의실 등록·수정과 모든 회의실 예약 변경·취소"),
	RESOURCE_MANAGER("자원관리자", "비품·장비·차량 등록·수정과 모든 자원 예약 변경·취소");

	private final String label;
	private final String description;

	Permission(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String authority() {
		return "PERM_" + name();
	}

	/** URL 용 (board-manager) */
	public String getPath() {
		return name().toLowerCase().replace('_', '-');
	}

	public static Permission fromPath(String path) {
		for (Permission p : values()) {
			if (p.getPath().equals(path)) {
				return p;
			}
		}
		return null;
	}

	public String getLabel() { return label; }
	public String getDescription() { return description; }
}
