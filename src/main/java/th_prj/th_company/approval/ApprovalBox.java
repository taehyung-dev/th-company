package th_prj.th_company.approval;

import java.util.Arrays;
import java.util.Optional;

/**
 * 전자결재 좌측 메뉴의 문서함.
 */
public enum ApprovalBox {
	PENDING("pending", "미결문서", "내가 결재할 차례인 문서"),
	PROGRESS("progress", "진행문서", "내가 기안했거나 결재선에 있는 진행 중 문서"),
	COMPLETED("completed", "완결문서", "승인 또는 반려로 끝난 문서"),
	CC("cc", "참조문서", "내가 참조자로 지정된 문서"),
	DRAFTS("drafts", "보관함", "임시저장하거나 회수한 내 문서");

	private final String path;
	private final String label;
	private final String description;

	ApprovalBox(String path, String label, String description) {
		this.path = path;
		this.label = label;
		this.description = description;
	}

	public static Optional<ApprovalBox> fromPath(String path) {
		return Arrays.stream(values()).filter(b -> b.path.equals(path)).findFirst();
	}

	public String getPath() { return path; }
	public String getLabel() { return label; }
	public String getDescription() { return description; }
}
