package th_prj.th_company.org;

import java.util.Arrays;
import java.util.Optional;

/**
 * 인사 코드 종류. path 는 관리자 URL 에 쓰인다 (/admin/codes/{path}).
 */
public enum HrCodeType {
	GRADE("grade", "직급", "사원, 대리, 과장처럼 호칭과 서열을 나타냅니다."),
	DUTY("duty", "직책", "팀장, 본부장처럼 맡은 역할을 나타냅니다.");

	private final String path;
	private final String label;
	private final String description;

	HrCodeType(String path, String label, String description) {
		this.path = path;
		this.label = label;
		this.description = description;
	}

	public static Optional<HrCodeType> fromPath(String path) {
		return Arrays.stream(values()).filter(t -> t.path.equals(path)).findFirst();
	}

	public String getPath() { return path; }
	public String getLabel() { return label; }
	public String getDescription() { return description; }
}
