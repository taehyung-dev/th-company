package th_prj.th_company.board;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 홈 화면 위젯용 게시글 링크.
 */
public record PostLink(Long id, Long boardId, String boardName, String title, LocalDateTime createdAt) {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM.dd");

	public String displayDate() {
		return createdAt.format(DATE);
	}
}
