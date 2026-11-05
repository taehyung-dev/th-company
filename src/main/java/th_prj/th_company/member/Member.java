package th_prj.th_company.member;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import th_prj.th_company.common.Address;
import th_prj.th_company.org.Department;
import th_prj.th_company.org.HrCode;

/**
 * 사용자(임직원).
 */
@Entity
@Table(name = "member")
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String loginId;

	@Column(nullable = false, length = 100)
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	/** 사번 */
	@Column(unique = true, length = 30)
	private String employeeNo;

	@Column(length = 100)
	private String email;

	@Column(length = 30)
	private String phone;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	/** 직급 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "grade_id")
	private HrCode grade;

	/** 직책 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "duty_id")
	private HrCode duty;

	/** 자택 주소 */
	@Embedded
	@AttributeOverride(name = "detail", column = @Column(name = "address_detail", length = 200))
	private Address address;

	/** 입사일 */
	private LocalDate hireDate;

	/** 프로필 사진 저장 이름 (FileStorage). 서버에서 JPEG 로 변환해 저장한다 */
	@Column(length = 100)
	private String photoPath;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(nullable = false)
	private boolean enabled = true;

	/** 기능별 관리 권한 (게시판관리자 등) */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "member_permission", joinColumns = @JoinColumn(name = "member_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "permission", nullable = false, length = 30)
	private Set<Permission> permissions = new HashSet<>();

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Member() {
	}

	public Member(String loginId, String encodedPassword, String name, Role role) {
		this.loginId = loginId;
		this.password = encodedPassword;
		this.name = name;
		this.role = role;
	}

	@PrePersist
	void onCreate() {
		createdAt = updatedAt = LocalDateTime.now();
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	public void updateProfile(String name, String employeeNo, String email, String phone,
			Department department, HrCode grade, HrCode duty, LocalDate hireDate) {
		this.name = name;
		this.employeeNo = employeeNo;
		this.email = email;
		this.phone = phone;
		this.department = department;
		this.grade = grade;
		this.duty = duty;
		this.hireDate = hireDate;
	}

	/** 마이페이지에서 본인이 바꾸는 연락처 */
	public void changeContact(String email, String phone) {
		this.email = email;
		this.phone = phone;
	}

	public void changeAddress(Address address) {
		this.address = address;
	}

	/** @return 이전 사진 저장 이름 (파일을 지우는 데 쓴다) */
	public String changePhoto(String photoPath) {
		String old = this.photoPath;
		this.photoPath = photoPath;
		return old;
	}

	public void changeAccess(Role role, boolean enabled) {
		this.role = role;
		this.enabled = enabled;
	}

	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	public void grant(Permission permission) {
		permissions.add(permission);
	}

	public void revoke(Permission permission) {
		permissions.remove(permission);
	}

	public boolean has(Permission permission) {
		return permissions.contains(permission);
	}

	public Long getId() { return id; }
	public String getLoginId() { return loginId; }
	public String getPassword() { return password; }
	public String getName() { return name; }
	public String getEmployeeNo() { return employeeNo; }
	public String getEmail() { return email; }
	public String getPhone() { return phone; }
	public Department getDepartment() { return department; }
	public HrCode getGrade() { return grade; }
	public HrCode getDuty() { return duty; }
	public Address getAddress() { return address; }
	public LocalDate getHireDate() { return hireDate; }
	public String getPhotoPath() { return photoPath; }
	/** 사진 주소. 사진을 바꾸면 주소도 바뀌어 브라우저 캐시가 갱신된다. 사진이 없으면 null */
	public String getPhotoUrl() {
		return photoPath == null ? null : "/members/" + id + "/photo?v=" + Integer.toHexString(photoPath.hashCode());
	}
	public Role getRole() { return role; }
	public boolean isEnabled() { return enabled; }
	public Set<Permission> getPermissions() {
		return permissions.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(permissions));
	}
	public LocalDateTime getCreatedAt() { return createdAt; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
