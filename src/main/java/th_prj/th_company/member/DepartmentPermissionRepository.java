package th_prj.th_company.member;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentPermissionRepository extends JpaRepository<DepartmentPermission, Long> {

	@Query("select dp from DepartmentPermission dp join fetch dp.department d order by d.sortOrder, d.name")
	List<DepartmentPermission> findAllWithDepartment();

	List<DepartmentPermission> findByDepartmentIdIn(Collection<Long> departmentIds);

	Optional<DepartmentPermission> findByDepartmentIdAndPermission(Long departmentId, Permission permission);

	@Modifying
	@Query("delete from DepartmentPermission dp where dp.department.id = :departmentId")
	void deleteByDepartment(@Param("departmentId") Long departmentId);
}
