package th_prj.th_company.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 직급/직책 같은 인사 코드. sortOrder 가 작을수록 상위.
 */
@Entity
@Table(name = "hr_code", uniqueConstraints = @UniqueConstraint(columnNames = {"type", "name"}))
public class HrCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HrCodeType type;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private int sortOrder;

	@Column(nullable = false)
	private boolean enabled = true;

	protected HrCode() {
	}

	public HrCode(HrCodeType type, String name, int sortOrder) {
		this.type = type;
		this.name = name;
		this.sortOrder = sortOrder;
	}

	public void update(String name, int sortOrder, boolean enabled) {
		this.name = name;
		this.sortOrder = sortOrder;
		this.enabled = enabled;
	}

	public Long getId() { return id; }
	public HrCodeType getType() { return type; }
	public String getName() { return name; }
	public int getSortOrder() { return sortOrder; }
	public boolean isEnabled() { return enabled; }
}
