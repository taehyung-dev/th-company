package th_prj.th_company.board;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class BoardForm {

	private Long id;

	@NotNull
	private BoardType type = BoardType.BOARD;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
	private String name;

	@Size(max = 200, message = "설명은 200자 이하로 입력하세요.")
	private String description;

	private Long parentId;

	@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
	private int sortOrder;

	private boolean enabled = true;

	private boolean adminOnlyWrite;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public BoardType getType() { return type; }
	public void setType(BoardType type) { this.type = type; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public Long getParentId() { return parentId; }
	public void setParentId(Long parentId) { this.parentId = parentId; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public boolean isAdminOnlyWrite() { return adminOnlyWrite; }
	public void setAdminOnlyWrite(boolean adminOnlyWrite) { this.adminOnlyWrite = adminOnlyWrite; }
}
