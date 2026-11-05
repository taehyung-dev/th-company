package th_prj.th_company.org;

import java.time.LocalDate;
import java.util.List;

/**
 * 조직도 화면 데이터.
 *
 * @param departments 좌측 부서 트리 (사용 중인 부서만, 트리 순서)
 * @param selected    선택된 부서 (부서가 하나도 없으면 null)
 * @param members     선택 부서에 직접 소속된 인원 (하위 부서 인원은 제외)
 * @param focusId     처음 상세정보를 보여줄 사람 (본인이 있으면 본인, 없으면 목록 맨 위). 인원이 없으면 null
 */
public record OrgChart(List<Dept> departments, Dept selected, List<Person> members, Long focusId) {

	/** memberCount: 이 부서에 직접 소속된 인원 수 */
	public record Dept(Long id, String name, int depth, long memberCount) {
	}

	/** photoUrl: 프로필 사진 주소 (없으면 null). 자택 주소 같은 개인정보는 넣지 않는다 */
	public record Person(Long id, String name, String gradeName, String dutyName,
			String employeeNo, String email, String phone, String departmentName, LocalDate hireDate,
			String photoUrl) {

		public String initial() {
			return name == null || name.isEmpty() ? "?" : name.substring(0, 1);
		}
	}
}
