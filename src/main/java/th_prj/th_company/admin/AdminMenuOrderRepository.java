package th_prj.th_company.admin;

import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AdminMenuOrderRepository extends JpaRepository<AdminMenuOrder, AdminSection> {

	/** 없어진 구분의 저장된 순서를 지운다 (남아 있으면 엔티티로 읽을 때 오류가 난다) */
	@Modifying
	@Query(value = "delete from admin_menu_order where section not in (:names)", nativeQuery = true)
	int deleteRemovedSections(Collection<String> names);
}
