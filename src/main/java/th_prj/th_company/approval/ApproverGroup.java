package th_prj.th_company.approval;

import java.util.List;

/**
 * 결재선 지정 팝업의 조직도 한 줄 (부서). 트리 순서로 펼쳐져 있고 depth 0 이 최상위.
 * 소속 인원이 없는 부서도 트리 모양을 위해 포함한다.
 */
public record ApproverGroup(Long departmentId, String departmentName, int depth, List<Option> members) {

	/**
	 * @param label 목록 표시용 "이름 직급 (부서)"
	 * @param name  결재란/조직도 표시용
	 * @param grade 직급 (없으면 null)
	 * @param dept  부서명 (없으면 null)
	 */
	public record Option(Long id, String label, String name, String grade, String dept) {
	}
}
