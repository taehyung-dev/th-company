package th_prj.th_company.file;

/**
 * 첨부파일이 달린 대상. 게시글 첨부는 예전부터 쓰던 post_attachment 를 그대로 쓴다.
 */
public enum FileOwner {
	APPROVAL,
	SCHEDULE,
	RESERVATION,
	/** 반복 예약 전체에 달린 파일 (같은 반복의 모든 예약에서 보인다) */
	RESERVATION_SERIES
}
