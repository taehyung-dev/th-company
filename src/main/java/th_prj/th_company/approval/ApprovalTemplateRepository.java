package th_prj.th_company.approval;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTemplateRepository extends JpaRepository<ApprovalTemplate, Long> {

	List<ApprovalTemplate> findByEnabledTrueOrderBySortOrderAscNameAsc();

	List<ApprovalTemplate> findAllByOrderBySortOrderAscNameAsc();

	boolean existsByCode(String code);

	Optional<ApprovalTemplate> findByCode(String code);

	boolean existsByCodeAndIdNot(String code, Long id);

	long countByEnabledTrue();
}
