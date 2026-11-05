package th_prj.th_company.member;

import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.org.DepartmentService;
import th_prj.th_company.org.HrCode;
import th_prj.th_company.org.HrCodeService;
import th_prj.th_company.org.HrCodeType;

/**
 * 관리자 화면의 사용자(임직원) 관리.
 */
@Service
@Transactional(readOnly = true)
public class MemberAdminService {

	private final MemberRepository memberRepository;
	private final DepartmentService departmentService;
	private final HrCodeService hrCodeService;
	private final PasswordEncoder passwordEncoder;

	public MemberAdminService(MemberRepository memberRepository, DepartmentService departmentService,
			HrCodeService hrCodeService, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.departmentService = departmentService;
		this.hrCodeService = hrCodeService;
		this.passwordEncoder = passwordEncoder;
	}

	public List<MemberRow> search(String keyword, Long departmentId, Boolean enabled) {
		String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
		return memberRepository.search(kw, departmentId, enabled).stream()
			.map(MemberRow::from)
			.toList();
	}

	public MemberForm getForm(Long id) {
		Member m = find(id);
		MemberForm form = new MemberForm();
		form.setId(m.getId());
		form.setLoginId(m.getLoginId());
		form.setName(m.getName());
		form.setEmployeeNo(m.getEmployeeNo());
		form.setEmail(m.getEmail());
		form.setPhone(m.getPhone());
		form.setDepartmentId(m.getDepartment() == null ? null : m.getDepartment().getId());
		form.setGradeId(m.getGrade() == null ? null : m.getGrade().getId());
		form.setDutyId(m.getDuty() == null ? null : m.getDuty().getId());
		form.setHireDate(m.getHireDate());
		form.setRole(m.getRole());
		form.setEnabled(m.isEnabled());
		form.setAddress(m.getAddress());
		return form;
	}

	/** 현재 프로필 사진 주소 (없으면 null) */
	public String photoUrl(Long id) {
		return find(id).getPhotoUrl();
	}

	// ---------- 중복 확인 ----------

	/** 중복 확인 대상 필드 (폼 필드명 → 화면 표시명) */
	private static final Map<String, String> UNIQUE_FIELDS = Map.of(
		"loginId", "아이디",
		"employeeNo", "사번",
		"email", "이메일");

	public record DuplicateCheck(boolean available, String message) {
	}

	/**
	 * 관리자 화면의 [중복확인] 버튼용.
	 *
	 * @param excludeId 수정 중인 사용자 id (자기 자신은 중복으로 보지 않음). 등록이면 null.
	 */
	public DuplicateCheck check(String field, String value, Long excludeId) {
		String label = UNIQUE_FIELDS.get(field);
		if (label == null) {
			throw new BusinessException("중복 확인을 할 수 없는 항목입니다.");
		}
		String v = blankToNull(value);
		if (v == null) {
			return new DuplicateCheck(false, label + "을(를) 입력하세요.");
		}
		boolean duplicated = switch (field) {
			case "loginId" -> excludeId == null
				? memberRepository.existsByLoginIdIgnoreCase(v)
				: memberRepository.existsByLoginIdIgnoreCaseAndIdNot(v, excludeId);
			case "employeeNo" -> excludeId == null
				? memberRepository.existsByEmployeeNo(v)
				: memberRepository.existsByEmployeeNoAndIdNot(v, excludeId);
			default -> excludeId == null
				? memberRepository.existsByEmailIgnoreCase(v)
				: memberRepository.existsByEmailIgnoreCaseAndIdNot(v, excludeId);
		};
		return duplicated
			? new DuplicateCheck(false, "이미 사용 중인 " + label + "입니다.")
			: new DuplicateCheck(true, "사용할 수 있는 " + label + "입니다.");
	}

	/** 저장 전 중복 검사. 아이디는 등록할 때만 (수정 화면에서는 바꿀 수 없음). */
	private void requireUnique(MemberForm form, Long excludeId) {
		List<String> fields = excludeId == null ? List.of("loginId", "employeeNo", "email") : List.of("employeeNo", "email");
		for (String field : fields) {
			String value = switch (field) {
				case "loginId" -> form.getLoginId();
				case "employeeNo" -> form.getEmployeeNo();
				default -> form.getEmail();
			};
			if (isBlank(value)) {
				continue;
			}
			DuplicateCheck result = check(field, value, excludeId);
			if (!result.available()) {
				throw new DuplicateValueException(field, result.message());
			}
		}
	}

	// ---------- 등록 / 수정 ----------

	@Transactional
	public Long create(MemberForm form) {
		String loginId = form.getLoginId().trim();
		requireUnique(form, null);
		if (isBlank(form.getPassword())) {
			throw new BusinessException("초기 비밀번호를 입력하세요.");
		}
		String employeeNo = blankToNull(form.getEmployeeNo());

		Member m = new Member(loginId, passwordEncoder.encode(form.getPassword()), form.getName().trim(), form.getRole());
		applyProfile(m, form, employeeNo);
		m.changeAccess(form.getRole(), form.isEnabled());
		return memberRepository.save(m).getId();
	}

	/**
	 * @param currentLoginId 현재 로그인한 관리자. 자기 계정을 잠그거나 관리자 권한을 빼는 것을 막는다.
	 */
	@Transactional
	public void update(Long id, MemberForm form, String currentLoginId) {
		Member m = find(id);
		requireUnique(form, id);
		String employeeNo = blankToNull(form.getEmployeeNo());
		if (m.getLoginId().equals(currentLoginId) && (!form.isEnabled() || form.getRole() != Role.ADMIN)) {
			throw new BusinessException("본인 계정은 사용 중지하거나 관리자 권한을 해제할 수 없습니다.");
		}

		applyProfile(m, form, employeeNo);
		m.changeAccess(form.getRole(), form.isEnabled());
		if (!isBlank(form.getPassword())) {
			m.changePassword(passwordEncoder.encode(form.getPassword()));
		}
	}

	private void applyProfile(Member m, MemberForm form, String employeeNo) {
		m.updateProfile(
			form.getName().trim(),
			employeeNo,
			blankToNull(form.getEmail()),
			blankToNull(form.getPhone()),
			form.getDepartmentId() == null ? null : departmentService.find(form.getDepartmentId()),
			code(HrCodeType.GRADE, form.getGradeId()),
			code(HrCodeType.DUTY, form.getDutyId()),
			form.getHireDate());
		m.changeAddress(form.toAddress());
	}

	private HrCode code(HrCodeType type, Long id) {
		return id == null ? null : hrCodeService.find(type, id);
	}

	private Member find(Long id) {
		return memberRepository.findById(id)
			.orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String blankToNull(String s) {
		return isBlank(s) ? null : s.trim();
	}
}
