package th_prj.th_company.board;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PostForm {

	@NotBlank(message = "제목을 입력하세요.")
	@Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
	private String title;

	@NotBlank(message = "내용을 입력하세요.")
	@Size(max = 100_000, message = "내용이 너무 깁니다.")
	private String content;

	/** 상단 고정 (관리자만 반영) */
	private boolean pinned;

	/** 수정 시 삭제할 기존 첨부파일 id */
	private List<Long> deleteAttachmentIds = new ArrayList<>();

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
	public boolean isPinned() { return pinned; }
	public void setPinned(boolean pinned) { this.pinned = pinned; }
	public List<Long> getDeleteAttachmentIds() { return deleteAttachmentIds; }
	public void setDeleteAttachmentIds(List<Long> deleteAttachmentIds) { this.deleteAttachmentIds = deleteAttachmentIds; }
}
