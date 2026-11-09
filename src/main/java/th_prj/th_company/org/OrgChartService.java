package th_prj.th_company.org;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;

/**
 * 사용자 화면 조직도. 사용 안 함 부서(와 그 하위)와 사용 중지된 사용자는 보이지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class OrgChartService {

	/** 직책 → 직급 → 이름 순. 직책/직급이 없으면 뒤로. */
	public static final Comparator<Member> MEMBER_ORDER = Comparator
		.comparingInt((Member m) -> m.getDuty() == null ? Integer.MAX_VALUE : m.getDuty().getSortOrder())
		.thenComparingInt(m -> m.getGrade() == null ? Integer.MAX_VALUE : m.getGrade().getSortOrder())
		.thenComparing(Member::getName);

	private final DepartmentService departmentService;
	private final MemberRepository memberRepository;

	public OrgChartService(DepartmentService departmentService, MemberRepository memberRepository) {
		this.departmentService = departmentService;
		this.memberRepository = memberRepository;
	}

	/**
	 * 조직도를 여는 사람 기준. 부서를 지정하지 않으면 본인 부서를, 부서가 없으면(sysadmin 등) 맨 위(회사)를 연다.
	 * 본인이 그 부서에 있으면 본인의 상세정보를 먼저 보여준다.
	 */
	public OrgChart load(Long departmentId, String loginId) {
		Member me = memberRepository.findByLoginId(loginId).orElse(null);
		Long deptId = departmentId;
		if (deptId == null && me != null && me.getDepartment() != null) {
			deptId = me.getDepartment().getId();
		}
		OrgChart chart = load(deptId);
		if (me != null && chart.members().stream().anyMatch(p -> p.id().equals(me.getId()))) {
			return new OrgChart(chart.departments(), chart.selected(), chart.members(), me.getId());
		}
		return chart;
	}

	/**
	 * @param departmentId 선택 부서. null 이거나 보이지 않는 부서면 첫 부서를 선택한다.
	 */
	public OrgChart load(Long departmentId) {
		List<DepartmentRow> rows = visibleRows(departmentService.tree());

		Map<Long, List<Member>> membersByDept = memberRepository.findActiveWithDepartment().stream()
			.collect(Collectors.groupingBy(m -> m.getDepartment().getId()));

		List<OrgChart.Dept> depts = rows.stream()
			.map(r -> new OrgChart.Dept(r.id(), r.name(), r.depth(),
				membersByDept.getOrDefault(r.id(), List.of()).size(), r.enabled()))
			.toList();

		if (depts.isEmpty()) {
			return new OrgChart(depts, null, List.of(), null);
		}

		OrgChart.Dept selected = depts.stream()
			.filter(d -> d.id().equals(departmentId))
			.findFirst()
			.orElse(depts.get(0));

		List<OrgChart.Person> members = membersByDept.getOrDefault(selected.id(), List.of()).stream()
			.sorted(MEMBER_ORDER)
			.map(OrgChartService::toPerson)
			.toList();

		return new OrgChart(depts, selected, members, members.isEmpty() ? null : members.get(0).id());
	}

	/**
	 * 사람 목록을 조직도처럼 나눈다. 관리 화면용이라 사용 안 함 부서도 보여 주어 아무도 빠지지 않게 한다.
	 * 부서를 지정하지 않으면 본인 부서, 본인 부서가 비었으면 사람이 있는 첫 부서를 연다.
	 *
	 * @param rows   보여 줄 사람 전체. 이 순서를 유지한다
	 * @param deptOf 그 사람의 부서 id (없으면 null)
	 */
	public <T> DeptView<T> deptView(List<T> rows, Function<T, Long> deptOf, Long departmentId, String loginId) {
		return deptView(rows, deptOf, departmentId, loginId, true);
	}

	/**
	 * @param includeDisabled false 면 사용자 화면(주소록 등)처럼 사용 안 함 부서와 그 하위 부서를 트리에서 뺀다
	 */
	public <T> DeptView<T> deptView(List<T> rows, Function<T, Long> deptOf, Long departmentId, String loginId,
			boolean includeDisabled) {
		Map<Long, List<T>> byDept = rows.stream().collect(Collectors.groupingBy(r -> {
			Long id = deptOf.apply(r);
			return id == null ? DeptView.NO_DEPT : id;
		}));
		List<DepartmentRow> tree = includeDisabled ? departmentService.tree() : visibleRows(departmentService.tree());
		List<OrgChart.Dept> depts = tree.stream()
			.map(r -> new OrgChart.Dept(r.id(), r.name(), r.depth(),
				byDept.getOrDefault(r.id(), List.of()).size(), r.enabled()))
			.toList();
		long noDept = byDept.getOrDefault(DeptView.NO_DEPT, List.of()).size();

		Long selected = selectDept(depts, noDept, departmentId, loginId);
		String title = depts.stream().filter(d -> d.id().equals(selected)).map(OrgChart.Dept::name).findFirst()
			.orElse(selected == null ? null : "부서 없음");
		return new DeptView<>(depts, selected, title, noDept, byDept.getOrDefault(selected, List.of()));
	}

	private Long selectDept(List<OrgChart.Dept> depts, long noDept, Long requested, String loginId) {
		if (requested != null) {
			if (requested == DeptView.NO_DEPT && noDept > 0) {
				return requested;
			}
			if (depts.stream().anyMatch(d -> d.id().equals(requested))) {
				return requested;
			}
		}
		Member me = memberRepository.findByLoginId(loginId).orElse(null);
		if (me != null && me.getDepartment() != null) {
			Long mine = me.getDepartment().getId();
			if (depts.stream().anyMatch(d -> d.id().equals(mine) && d.memberCount() > 0)) {
				return mine;
			}
		}
		return depts.stream().filter(d -> d.memberCount() > 0).map(OrgChart.Dept::id).findFirst()
			.orElseGet(() -> noDept > 0 ? Long.valueOf(DeptView.NO_DEPT) : depts.isEmpty() ? null : depts.get(0).id());
	}

	/** 부서 트리 순서 (엑셀처럼 회사 전체를 부서 순서로 늘어놓을 때). 부서가 없으면 맨 뒤 */
	public Comparator<Long> departmentOrder() {
		List<DepartmentRow> tree = departmentService.tree();
		Map<Long, Integer> index = new java.util.HashMap<>();
		for (int i = 0; i < tree.size(); i++) {
			index.put(tree.get(i).id(), i);
		}
		return Comparator.comparingInt(id -> id == null ? Integer.MAX_VALUE : index.getOrDefault(id, Integer.MAX_VALUE));
	}

	/** 사용 안 함 부서와 그 하위 부서를 뺀다. rows 는 트리 순서여야 한다. */
	public static List<DepartmentRow> visibleRows(List<DepartmentRow> rows) {
		List<DepartmentRow> visible = new ArrayList<>();
		int hiddenDepth = Integer.MAX_VALUE;
		for (DepartmentRow r : rows) {
			if (r.depth() > hiddenDepth) {
				continue;
			}
			hiddenDepth = Integer.MAX_VALUE;
			if (!r.enabled()) {
				hiddenDepth = r.depth();
				continue;
			}
			visible.add(r);
		}
		return visible;
	}

	private static OrgChart.Person toPerson(Member m) {
		return new OrgChart.Person(m.getId(), m.getName(),
			m.getGrade() == null ? null : m.getGrade().getName(),
			m.getDuty() == null ? null : m.getDuty().getName(),
			m.getEmployeeNo(), m.getEmail(), m.getPhone(), m.getDepartment().getName(), m.getHireDate(),
			m.getPhotoUrl());
	}
}
