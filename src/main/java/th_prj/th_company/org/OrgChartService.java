package th_prj.th_company.org;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
	private static final Comparator<Member> MEMBER_ORDER = Comparator
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
				membersByDept.getOrDefault(r.id(), List.of()).size()))
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
