package th_prj.th_company.reservation;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 예약할 수 있는 자원 (회의실, 비품·장비, 차량). 관리자 화면에서 등록한다.
 * 사용 안 함으로 바꾸면 새 예약을 받지 않지만 지난 예약은 그대로 남는다.
 */
@Entity
@Table(name = "booking_resource")
public class Resource {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ResourceType type;

	@Column(nullable = false, length = 50)
	private String name;

	/** 위치 (예: 본관 3층) */
	@Column(length = 100)
	private String location;

	/** 수용 인원 (회의실, 차량). 없으면 null */
	private Integer capacity;

	@Column(length = 300)
	private String description;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Resource() {
	}

	public Resource(ResourceType type, String name, String location, Integer capacity, String description, int sortOrder) {
		update(type, name, location, capacity, description, sortOrder, true);
	}

	@PrePersist
	void onCreate() {
		createdAt = LocalDateTime.now();
	}

	public void update(ResourceType type, String name, String location, Integer capacity, String description,
			int sortOrder, boolean enabled) {
		this.type = type;
		this.name = name;
		this.location = location;
		this.capacity = capacity;
		this.description = description;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
	}

	public Long getId() { return id; }
	public ResourceType getType() { return type; }
	public String getName() { return name; }
	public String getLocation() { return location; }
	public Integer getCapacity() { return capacity; }
	public String getDescription() { return description; }
	public int getSortOrder() { return sortOrder; }
	public boolean isEnabled() { return enabled; }
}
