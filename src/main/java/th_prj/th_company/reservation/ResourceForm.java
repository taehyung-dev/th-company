package th_prj.th_company.reservation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ResourceForm {

	private Long id;

	@NotNull
	private ResourceType type = ResourceType.ROOM;

	@NotBlank(message = "이름을 입력하세요.")
	@Size(max = 50, message = "이름은 50자 이하로 입력하세요.")
	private String name;

	@Size(max = 100, message = "위치는 100자 이하로 입력하세요.")
	private String location;

	@Min(value = 1, message = "수용 인원은 1 이상이어야 합니다.")
	@Max(value = 999, message = "수용 인원은 999 이하로 입력하세요.")
	private Integer capacity;

	@Size(max = 300, message = "설명은 300자 이하로 입력하세요.")
	private String description;

	@Min(value = 0, message = "정렬 순서는 0 이상이어야 합니다.")
	private int sortOrder;

	private boolean enabled = true;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public ResourceType getType() { return type; }
	public void setType(ResourceType type) { this.type = type; }
	public String getName() { return name; }
	public void setName(String name) { this.name = name; }
	public String getLocation() { return location; }
	public void setLocation(String location) { this.location = location; }
	public Integer getCapacity() { return capacity; }
	public void setCapacity(Integer capacity) { this.capacity = capacity; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public int getSortOrder() { return sortOrder; }
	public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
