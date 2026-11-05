package th_prj.th_company.member;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.org.Department;

/**
 * 사용자가 실제로 가진 관리 권한 = 본인에게 준 권한 + 소속 부서에 준 권한
 * + (하위 부서 포함으로 지정된) 상위 부서에 준 권한.
 */
@Component
public class PermissionResolver {

	private final DepartmentPermissionRepository departmentPermissionRepository;

	public PermissionResolver(DepartmentPermissionRepository departmentPermissionRepository) {
		this.departmentPermissionRepository = departmentPermissionRepository;
	}

	@Transactional(readOnly = true)
	public Set<Permission> effective(Member member) {
		Set<Permission> result = EnumSet.noneOf(Permission.class);
		result.addAll(member.getPermissions());

		Department own = member.getDepartment();
		if (own == null) {
			return result;
		}
		// 소속 부서부터 최상위까지
		List<Long> chain = new ArrayList<>();
		for (Department d = own; d != null; d = d.getParent()) {
			chain.add(d.getId());
		}
		for (DepartmentPermission dp : departmentPermissionRepository.findByDepartmentIdIn(chain)) {
			boolean ownDept = dp.getDepartment().getId().equals(own.getId());
			if (ownDept || dp.isIncludeSubDepartments()) {
				result.add(dp.getPermission());
			}
		}
		return result;
	}
}
