package th_prj.th_company.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 마이페이지 비밀번호 변경 */
public class PasswordChangeForm {

	@NotBlank(message = "현재 비밀번호를 입력하세요.")
	private String currentPassword;

	@NotBlank(message = "새 비밀번호를 입력하세요.")
	@Size(max = 100, message = "비밀번호는 100자 이하로 입력하세요.")
	private String newPassword;

	@NotBlank(message = "새 비밀번호를 한 번 더 입력하세요.")
	private String confirmPassword;

	public String getCurrentPassword() { return currentPassword; }
	public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
	public String getNewPassword() { return newPassword; }
	public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
	public String getConfirmPassword() { return confirmPassword; }
	public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
