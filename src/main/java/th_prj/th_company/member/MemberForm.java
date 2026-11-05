package th_prj.th_company.member;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import th_prj.th_company.common.AddressForm;

/**
 * 관리자 사용자 등록/수정 폼 (주소 칸은 AddressForm). password 는 등록 시 필수, 수정 시 입력하면 변경.
 */
public class MemberForm extends AddressForm {

	private Long id;

	@NotBlank(message = "아이디를 입력하세요.")
	@Pattern(regexp = "[A-Za-z0-9._-]{3,50}", message = "아이디는 영문, 숫자, . _ - 로 3~50자 입력하세요.")
	private String loginId;

	@Size(max = 100, message = "비밀번호는 100자 이하로 입력하세요.")
	private String password;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
	private String name;

	@Size(max = 30, message = "사번은 30자 이하로 입력하세요.")
	private String employeeNo;

	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
	private String email;

	@Size(max = 30, message = "연락처는 30자 이하로 입력하세요.")
	private String phone;

	private Long departmentId;
	private Long gradeId;
	private Long dutyId;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate hireDate;

	@NotNull
	private Role role = Role.USER;

	private boolean enabled = true;

	public boolean isNew() { return id == null; }

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getLoginId() { return loginId; }
	public void setLoginId(String loginId) { this.loginId = loginId; }
	public String getPassword() { return password; }
	public void setPassword(String password) { this.password = password; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getEmployeeNo() { return employeeNo; }
	public void setEmployeeNo(String employeeNo) { this.employeeNo = employeeNo; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public Long getDepartmentId() { return departmentId; }
	public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
	public Long getGradeId() { return gradeId; }
	public void setGradeId(Long gradeId) { this.gradeId = gradeId; }
	public Long getDutyId() { return dutyId; }
	public void setDutyId(Long dutyId) { this.dutyId = dutyId; }
	public LocalDate getHireDate() { return hireDate; }
	public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
	public Role getRole() { return role; }
	public void setRole(Role role) { this.role = role; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
