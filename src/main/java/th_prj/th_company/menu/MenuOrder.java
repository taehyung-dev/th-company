package th_prj.th_company.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 상단 메뉴 순서. 저장된 줄이 없으면 MainMenu 선언 순서를 쓴다.
 */
@Entity
@Table(name = "menu_order")
public class MenuOrder {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private MainMenu menu;

	@Column(nullable = false)
	private int sortOrder;

	protected MenuOrder() {
	}

	public MenuOrder(MainMenu menu, int sortOrder) {
		this.menu = menu;
		this.sortOrder = sortOrder;
	}

	public void changeOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public MainMenu getMenu() { return menu; }
	public int getSortOrder() { return sortOrder; }
}
