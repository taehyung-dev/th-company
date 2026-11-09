package th_prj.th_company.org;

import java.util.List;

/**
 * 사람 목록을 조직도처럼 보여 주는 화면의 데이터. 좌측은 부서 트리, 우측은 선택 부서에 직접 소속된 사람이다.
 * 사용자 관리, 전체 휴가 현황처럼 관리 화면에서 쓴다. {@link OrgChartService#deptView}로 만든다.
 *
 * @param departments 좌측 부서 트리. memberCount 는 이 화면 목록 기준이고, 사용 안 함 부서도 들어 있다
 * @param selectedId  선택 부서. 부서 없는 사람들이면 {@link #NO_DEPT}, 검색 결과면 null
 * @param title       우측 제목
 * @param noDeptCount 부서가 없는 사람 수. 0 이면 트리에 [부서 없음]이 나오지 않는다
 * @param members     우측 목록
 */
public record DeptView<T>(List<OrgChart.Dept> departments, Long selectedId, String title,
		long noDeptCount, List<T> members) {

	/** 트리의 [부서 없음]을 뜻하는 dept 값 */
	public static final long NO_DEPT = 0L;

	/** 부서와 상관없는 검색 결과로 우측을 바꾼다 */
	public DeptView<T> searchResult(List<T> found) {
		return new DeptView<>(departments, null, "검색 결과", noDeptCount, found);
	}
}
