package th_prj.th_company.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import th_prj.th_company.common.AddressForm;

/**
 * 마이페이지에서 본인이 고칠 수 있는 정보 (이메일, 연락처, 주소). 이름·부서·직급 등 인사정보는 관리자만 바꾼다.
 */
public class MyProfileForm extends AddressForm {

	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
	private String email;

	@Size(max = 30, message = "연락처는 30자 이하로 입력하세요.")
	private String phone;

	public String getEmail() { return email; }
	/** 앞뒤 공백 때문에 형식 오류가 나지 않게 먼저 다듬는다 */
	public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
}
