package th_prj.th_company.org;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class HrCodeForm {

	private Long id;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
	private String name;

	@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
	private int sortOrder;

	private boolean enabled = true;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
