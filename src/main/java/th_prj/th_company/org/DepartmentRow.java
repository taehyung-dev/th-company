package th_prj.th_company.org;

/**
 * 부서 트리를 위에서부터 펼친 한 줄. depth 0 이 최상위.
 */
public record DepartmentRow(Long id, String name, Long parentId, int depth,
		int sortOrder, boolean enabled, long memberCount) {
}
