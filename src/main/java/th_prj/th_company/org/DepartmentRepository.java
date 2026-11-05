package th_prj.th_company.org;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

	List<Department> findAllByOrderBySortOrderAscNameAsc();

	boolean existsByParentId(Long parentId);
}
