package th_prj.th_company.member;

import java.time.LocalDate;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.Address;
import th_prj.th_company.common.BusinessException;

/**
 * 마이페이지: 내 정보 보기, 연락처·주소 수정, 비밀번호 변경.
 * 이름, 사번, 부서, 직급·직책, 입사일, 권한은 관리자 화면에서만 바꾼다.
 */
@Service
@Transactional(readOnly = true)
public class MyPageService {

	/** 새 비밀번호 규칙: 8자 이상, 영문과 숫자를 모두 포함 */
	static final int MIN_PASSWORD_LENGTH = 8;

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	public MyPageService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
		this.memberRepository = memberRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/** 마이페이지에 보일 내 정보 */
	public record MyProfile(Long id, String loginId, String name, String employeeNo, String email, String phone,
			String departmentName, String gradeName, String dutyName, LocalDate hireDate, Address address,
			String photoUrl, boolean admin, List<String> permissions) {

		public String initial() {
			return name == null || name.isEmpty() ? "?" : name.substring(0, 1);
		}

		/** 직급 · 직책 (없는 것은 빼고) */
		public String position() {
			if (gradeName == null) {
				return dutyName;
			}
			return dutyName == null ? gradeName : gradeName + " · " + dutyName;
		}
	}

	public MyProfile profile(String loginId) {
		Member m = me(loginId);
		List<String> permissions = m.getPermissions().stream()
			.sorted()
			.map(Permission::getLabel)
			.toList();
		return new MyProfile(m.getId(), m.getLoginId(), m.getName(), m.getEmployeeNo(), m.getEmail(), m.getPhone(),
			m.getDepartment() == null ? null : m.getDepartment().getName(),
			m.getGrade() == null ? null : m.getGrade().getName(),
			m.getDuty() == null ? null : m.getDuty().getName(),
			m.getHireDate(), m.getAddress(), m.getPhotoPath() == null ? null : m.getPhotoUrl(),
			m.getRole() == Role.ADMIN, permissions);
	}

	public MyProfileForm editForm(String loginId) {
		Member m = me(loginId);
		MyProfileForm form = new MyProfileForm();
		form.setEmail(m.getEmail());
		form.setPhone(m.getPhone());
		form.setAddress(m.getAddress());
		return form;
	}

	/** @return 내 id (사진 저장에 쓴다) */
	@Transactional
	public Long updateProfile(String loginId, MyProfileForm form) {
		Member m = me(loginId);
		String email = blankToNull(form.getEmail());
		if (email != null && memberRepository.existsByEmailIgnoreCaseAndIdNot(email, m.getId())) {
			throw new BusinessException("이미 다른 사람이 쓰고 있는 이메일입니다.");
		}
		m.changeContact(email, blankToNull(form.getPhone()));
		m.changeAddress(form.toAddress());
		return m.getId();
	}

	@Transactional
	public void changePassword(String loginId, PasswordChangeForm form) {
		Member m = me(loginId);
		if (!passwordEncoder.matches(form.getCurrentPassword(), m.getPassword())) {
			throw new BusinessException("현재 비밀번호가 맞지 않습니다.");
		}
		String pw = form.getNewPassword();
		if (pw.length() < MIN_PASSWORD_LENGTH || !pw.matches(".*[A-Za-z].*") || !pw.matches(".*\\d.*")) {
			throw new BusinessException("새 비밀번호는 영문과 숫자를 섞어 " + MIN_PASSWORD_LENGTH + "자 이상으로 입력하세요.");
		}
		if (pw.chars().anyMatch(Character::isWhitespace)) {
			throw new BusinessException("새 비밀번호에 공백을 넣을 수 없습니다.");
		}
		if (!pw.equals(form.getConfirmPassword())) {
			throw new BusinessException("새 비밀번호 확인이 일치하지 않습니다.");
		}
		if (passwordEncoder.matches(pw, m.getPassword())) {
			throw new BusinessException("지금 쓰는 비밀번호와 다른 비밀번호를 입력하세요.");
		}
		m.changePassword(passwordEncoder.encode(pw));
	}

	private Member me(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.trim();
	}
}
