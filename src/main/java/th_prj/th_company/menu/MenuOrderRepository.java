package th_prj.th_company.menu;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MenuOrderRepository extends JpaRepository<MenuOrder, MainMenu> {

	/** 없어진 메뉴의 저장된 순서를 지운다 (남아 있으면 엔티티로 읽을 때 오류가 난다) */
	@Modifying
	@Query(value = "delete from menu_order where menu not in (:names)", nativeQuery = true)
	int deleteRemovedMenus(Collection<String> names);
}
