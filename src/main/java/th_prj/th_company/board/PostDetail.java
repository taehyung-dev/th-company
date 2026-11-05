package th_prj.th_company.board;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 게시글 상세 화면 데이터.
 */
public record PostDetail(Long id, Long boardId, String title, String content, boolean pinned,
		String authorLoginId, String authorName, String authorDepartment, String authorGrade,
		LocalDateTime createdAt, LocalDateTime updatedAt, int viewCount,
		List<FileInfo> attachments, List<CommentInfo> comments) {

	static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

	public String displayCreatedAt() {
		return createdAt.format(DATE_TIME);
	}

	/** 작성 후 1분 이상 지나 수정된 경우 */
	public boolean isEdited() {
		return updatedAt.isAfter(createdAt.plusMinutes(1));
	}

	public String displayUpdatedAt() {
		return updatedAt.format(DATE_TIME);
	}

	public record FileInfo(Long id, String name, long size) {

		public String displaySize() {
			if (size < 1024) {
				return size + " B";
			}
			if (size < 1024 * 1024) {
				return "%.1f KB".formatted(size / 1024.0);
			}
			return "%.1f MB".formatted(size / (1024.0 * 1024));
		}
	}

	public record CommentInfo(Long id, String authorLoginId, String authorName, String authorDepartment,
			String content, LocalDateTime createdAt) {

		public String displayCreatedAt() {
			return createdAt.format(DATE_TIME);
		}
	}
}
