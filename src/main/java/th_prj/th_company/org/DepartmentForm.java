package th_prj.th_company.org;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DepartmentForm {

	private Long id;

	@NotBlank(message = "부서명을 입력하세요.")
	@Size(max = 50, message = "부서명은 50자 이하로 입력하세요.")
	private String name;

	private Long parentId;

	@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
	private int sortOrder;

	private boolean enabled = true;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public Long getParentId() { return parentId; }
	public void setParentId(Long parentId) { this.parentId = parentId; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
