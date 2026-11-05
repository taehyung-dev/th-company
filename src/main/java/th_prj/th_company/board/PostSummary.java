package th_prj.th_company.board;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 게시글 목록의 한 줄.
 */
public record PostSummary(Long id, String title, String authorName, LocalDateTime createdAt,
		int viewCount, boolean pinned, long commentCount, long attachmentCount) {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	/** 오늘 글은 시:분, 그 외는 날짜 */
	public String displayDate() {
		return createdAt.toLocalDate().equals(LocalDate.now()) ? createdAt.format(TIME) : createdAt.format(DATE);
	}

	public boolean isNew() {
		return createdAt.isAfter(LocalDateTime.now().minusDays(1));
	}
}
