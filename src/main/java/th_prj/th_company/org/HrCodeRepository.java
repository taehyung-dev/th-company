package th_prj.th_company.org;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HrCodeRepository extends JpaRepository<HrCode, Long> {

	List<HrCode> findByTypeOrderBySortOrderAscNameAsc(HrCodeType type);

	boolean existsByTypeAndName(HrCodeType type, String name);

	boolean existsByTypeAndNameAndIdNot(HrCodeType type, String name, Long id);
}
