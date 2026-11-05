package th_prj.th_company.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 부서. parent 가 null 이면 최상위 부서.
 */
@Entity
@Table(name = "department")
public class Department {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 50)
	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Department parent;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean enabled = true;

	protected Department() {
	}

	public Department(String name, Department parent, int sortOrder) {
		this.name = name;
		this.parent = parent;
		this.sortOrder = sortOrder;
	}

	public void update(String name, Department parent, int sortOrder, boolean enabled) {
		this.name = name;
		this.parent = parent;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public Department getParent() { return parent; }
	public int getSortOrder() { return sortOrder; }
	public boolean isEnabled() { return enabled; }
}
