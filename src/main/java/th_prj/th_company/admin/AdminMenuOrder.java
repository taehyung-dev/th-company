package th_prj.th_company.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 관리자 화면 상단 구분 순서. 저장된 줄이 없으면 AdminSection 선언 순서를 쓴다.
 */
@Entity
@Table(name = "admin_menu_order")
public class AdminMenuOrder {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private AdminSection section;

	@Column(nullable = false)
	private int sortOrder;

	protected AdminMenuOrder() {
	}

	public AdminMenuOrder(AdminSection section, int sortOrder) {
		this.section = section;
		this.sortOrder = sortOrder;
	}

	public void changeOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public AdminSection getSection() { return section; }
	public int getSortOrder() { return sortOrder; }
}
