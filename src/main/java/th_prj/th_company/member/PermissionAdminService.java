package th_prj.th_company.member;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.DepartmentRow;
import th_prj.th_company.org.DepartmentService;

/**
 * 관리자 화면의 권한 관리 (게시판관리자/근태관리자/일정관리자).
 * 대상은 사용자 또는 부서. 부서는 "하위 부서 포함" 여부를 함께 지정한다.
 */
@Service
@Transactional(readOnly = true)
public class PermissionAdminService {

	private final MemberRepository memberRepository;
	private final DepartmentPermissionRepository departmentPermissionRepository;
	private final DepartmentService departmentService;

	public PermissionAdminService(MemberRepository memberRepository,
			DepartmentPermissionRepository departmentPermissionRepository, DepartmentService departmentService) {
		this.memberRepository = memberRepository;
		this.departmentPermissionRepository = departmentPermissionRepository;
		this.departmentService = departmentService;
	}

	/** 권한별 대상 (부서 + 사용자) */
	public record PermissionGroup(Permission permission, List<DeptHolder> departments, List<Holder> holders) {

		public int size() {
			return departments.size() + holders.size();
		}
	}

	public record DeptHolder(Long deptId, String deptName, boolean includeSub, boolean enabled) {
	}

	public record Holder(Long memberId, String loginId, String name, String deptName, String gradeName,
			boolean enabled) {
	}

	/** 가운데 조직도의 부서 한 줄. 트리 순서로 펼쳐져 있고 depth 0 이 최상위. 인원 없는 부서도 포함. */
	public record OrgNode(Long deptId, String deptName, int depth, List<Holder> members) {
	}

	/** 추가 결과 요약 */
	public record GrantResult(int departments, int members, int skipped) {
	}

	public List<PermissionGroup> overview() {
		List<Member> withPermissions = memberRepository.findWithPermissions();
		List<DepartmentPermission> deptGrants = departmentPermissionRepository.findAllWithDepartment();
		List<PermissionGroup> groups = new ArrayList<>();
		for (Permission p : Permission.values()) {
			groups.add(new PermissionGroup(p,
				deptGrants.stream()
					.filter(dp -> dp.getPermission() == p)
					.map(dp -> new DeptHolder(dp.getDepartment().getId(), dp.getDepartment().getName(),
						dp.isIncludeSubDepartments(), dp.getDepartment().isEnabled()))
					.toList(),
				withPermissions.stream()
					.filter(m -> m.has(p))
					.map(PermissionAdminService::holder)
					.toList()));
		}
		return groups;
	}

	/**
	 * 조직도: 부서 트리 순서, 부서 안에서는 직책→직급→이름. 사용 중지된 사용자와
	 * 사용 안 함 부서(하위 포함)는 뺀다.
	 */
	public List<OrgNode> orgTree() {
		Map<Long, List<Member>> byDept = memberRepository.findActiveWithDepartment().stream()
			.collect(Collectors.groupingBy(m -> m.getDepartment().getId()));
		Comparator<Member> order = Comparator
			.comparingInt((Member m) -> m.getDuty() == null ? Integer.MAX_VALUE : m.getDuty().getSortOrder())
			.thenComparingInt(m -> m.getGrade() == null ? Integer.MAX_VALUE : m.getGrade().getSortOrder())
			.thenComparing(Member::getName);

		List<OrgNode> nodes = new ArrayList<>();
		int hiddenDepth = Integer.MAX_VALUE;
		for (DepartmentRow d : departmentService.tree()) {
			if (d.depth() > hiddenDepth) {
				continue;
			}
			hiddenDepth = Integer.MAX_VALUE;
			if (!d.enabled()) {
				hiddenDepth = d.depth();
				continue;
			}
			nodes.add(new OrgNode(d.id(), d.name(), d.depth(), byDept.getOrDefault(d.id(), List.of()).stream()
				.sorted(order)
				.map(PermissionAdminService::holder)
				.toList()));
		}
		return nodes;
	}

	/**
	 * 체크한 부서와 사용자에게 권한을 준다. 이미 대상인 것은 건너뛴다.
	 *
	 * @param includeSub 부서에 줄 때 하위 부서까지 포함할지
	 */
	@Transactional
	public GrantResult grant(Permission permission, List<Long> deptIds, List<Long> memberIds, boolean includeSub) {
		List<Long> depts = deptIds == null ? List.of() : deptIds;
		List<Long> members = memberIds == null ? List.of() : memberIds;
		if (depts.isEmpty() && members.isEmpty()) {
			throw new BusinessException("가운데 조직도에서 부서나 사용자를 체크하세요.");
		}

		int addedDepts = 0;
		int addedMembers = 0;
		int skipped = 0;
		for (Long deptId : depts) {
			if (departmentPermissionRepository.findByDepartmentIdAndPermission(deptId, permission).isPresent()) {
				skipped++;
				continue;
			}
			Department d = departmentService.find(deptId);
			departmentPermissionRepository.save(new DepartmentPermission(d, permission, includeSub));
			addedDepts++;
		}
		for (Long memberId : members) {
			Member m = find(memberId);
			if (m.has(permission) || !m.isEnabled()) {
				skipped++;
				continue;
			}
			m.grant(permission);
			addedMembers++;
		}
		return new GrantResult(addedDepts, addedMembers, skipped);
	}

	@Transactional
	public String revoke(Permission permission, Long memberId) {
		Member m = find(memberId);
		m.revoke(permission);
		return m.getName();
	}

	@Transactional
	public String revokeDepartment(Permission permission, Long deptId) {
		DepartmentPermission dp = deptGrant(permission, deptId);
		String name = dp.getDepartment().getName();
		departmentPermissionRepository.delete(dp);
		return name;
	}

	/** 부서 대상의 "하위 부서 포함" 변경 */
	@Transactional
	public String changeIncludeSub(Permission permission, Long deptId, boolean includeSub) {
		DepartmentPermission dp = deptGrant(permission, deptId);
		dp.changeIncludeSubDepartments(includeSub);
		return dp.getDepartment().getName();
	}

	private DepartmentPermission deptGrant(Permission permission, Long deptId) {
		return departmentPermissionRepository.findByDepartmentIdAndPermission(deptId, permission)
			.orElseThrow(() -> new BusinessException("해당 부서는 이 권한의 대상이 아닙니다."));
	}

	private Member find(Long id) {
		return memberRepository.findById(id)
			.orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
	}

	private static Holder holder(Member m) {
		return new Holder(m.getId(), m.getLoginId(), m.getName(),
			m.getDepartment() == null ? null : m.getDepartment().getName(),
			m.getGrade() == null ? null : m.getGrade().getName(),
			m.isEnabled());
	}
}
