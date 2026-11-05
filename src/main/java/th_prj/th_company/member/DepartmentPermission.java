package th_prj.th_company.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import th_prj.th_company.org.Department;

/**
 * 부서에 준 관리 권한. 그 부서 소속원은 모두 권한을 가진다 (나중에 들어온 사람 포함).
 * includeSubDepartments 이면 하위 부서 소속원도 포함한다.
 */
@Entity
@Table(name = "department_permission",
	uniqueConstraints = @UniqueConstraint(columnNames = {"department_id", "permission"}))
public class DepartmentPermission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "department_id", nullable = false)
	private Department department;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private Permission permission;

	@Column(nullable = false)
	private boolean includeSubDepartments;

	protected DepartmentPermission() {
	}

	public DepartmentPermission(Department department, Permission permission, boolean includeSubDepartments) {
		this.department = department;
		this.permission = permission;
		this.includeSubDepartments = includeSubDepartments;
	}

	void changeIncludeSubDepartments(boolean include) {
		this.includeSubDepartments = include;
	}

	public Long getId() { return id; }
	public Department getDepartment() { return department; }
	public Permission getPermission() { return permission; }
	public boolean isIncludeSubDepartments() { return includeSubDepartments; }
}
