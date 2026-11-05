package th_prj.th_company.address;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import th_prj.th_company.common.AddressForm;

public class ContactForm extends AddressForm {

	private Long id;

	private Long groupId;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
	private String name;

	@Size(max = 100, message = "회사는 100자 이하로 입력하세요.")
	private String company;

	@Size(max = 100, message = "부서는 100자 이하로 입력하세요.")
	private String department;

	@Size(max = 50, message = "직위는 50자 이하로 입력하세요.")
	private String position;

	@Size(max = 30, message = "휴대폰은 30자 이하로 입력하세요.")
	private String mobile;

	@Size(max = 30, message = "전화는 30자 이하로 입력하세요.")
	private String phone;

	@Email(message = "이메일 형식이 아닙니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력하세요.")
	private String email;

	@Size(max = 1000, message = "메모는 1000자 이하로 입력하세요.")
	private String memo;

	private boolean favorite;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getGroupId() { return groupId; }
	public void setGroupId(Long groupId) { this.groupId = groupId; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getCompany() { return company; }
	public void setCompany(String company) { this.company = company; }
	public String getDepartment() { return department; }
	public void setDepartment(String department) { this.department = department; }
	public String getPosition() { return position; }
	public void setPosition(String position) { this.position = position; }
	public String getMobile() { return mobile; }
	public void setMobile(String mobile) { this.mobile = mobile; }
	public String getPhone() { return phone; }
	public void setPhone(String phone) { this.phone = phone; }
	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getMemo() { return memo; }
	public void setMemo(String memo) { this.memo = memo; }
	public boolean isFavorite() { return favorite; }
	public void setFavorite(boolean favorite) { this.favorite = favorite; }
}
