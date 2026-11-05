package th_prj.th_company.member;

import java.util.Set;

/**
 * 관리자 사용자 목록의 한 줄.
 */
public record MemberRow(Long id, String loginId, String name, String employeeNo,
		Long departmentId, String departmentName, String gradeName, String dutyName,
		String email, String phone, Role role, Set<Permission> permissions, boolean enabled, String photoUrl) {

	public String initial() {
		return name == null || name.isEmpty() ? "?" : name.substring(0, 1);
	}

	static MemberRow from(Member m) {
		return new MemberRow(m.getId(), m.getLoginId(), m.getName(), m.getEmployeeNo(),
			m.getDepartment() == null ? null : m.getDepartment().getId(),
			m.getDepartment() == null ? null : m.getDepartment().getName(),
			m.getGrade() == null ? null : m.getGrade().getName(),
			m.getDuty() == null ? null : m.getDuty().getName(),
			m.getEmail(), m.getPhone(), m.getRole(), m.getPermissions(), m.isEnabled(), m.getPhotoUrl());
	}
}
