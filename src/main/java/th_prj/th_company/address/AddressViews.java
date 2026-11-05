package th_prj.th_company.address;

import java.util.List;

/**
 * 주소록 화면용 데이터.
 */
public final class AddressViews {

	private AddressViews() {
	}

	/** 사내 주소록 한 줄 */
	public record Employee(Long memberId, String name, String gradeName, String dutyName,
			String deptName, String email, String phone) {
	}

	/** 부서 선택 목록 (트리 순서, depth 로 들여쓰기) */
	public record DeptOption(Long id, String name, int depth) {

		public String indentedName() {
			return "  ".repeat(depth) + name;
		}
	}

	/** 개인 주소록 한 줄 */
	public record ContactRow(Long id, String name, String company, String department, String position,
			String mobile, String phone, String email, String groupName, boolean favorite) {

		/** "회사 · 부서 · 직위" (빈 값은 뺀다) */
		public String affiliation() {
			return String.join(" · ", java.util.stream.Stream.of(company, department, position)
				.filter(s -> s != null && !s.isBlank()).toList());
		}
	}

	/** 좌측 메뉴: 개인 주소록 개수와 그룹 */
	public record PersonalNav(long total, long favorites, List<GroupItem> groups) {
	}

	public record GroupItem(Long id, String name, long count) {
	}
}
