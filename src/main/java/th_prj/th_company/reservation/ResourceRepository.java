package th_prj.th_company.reservation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

	/** 종류(회의실 → 비품·장비 → 차량, enum 선언 순서), 정렬 순서, 이름 순. DB 에는 종류가 문자열이라 여기서 정렬 */
	Comparator<Resource> DISPLAY_ORDER = Comparator.comparing(Resource::getType)
		.thenComparingInt(Resource::getSortOrder)
		.thenComparing(Resource::getName);

	default List<Resource> findAllOrdered() {
		return findAll().stream().sorted(DISPLAY_ORDER).toList();
	}

	default List<Resource> findEnabled() {
		return findByEnabledTrue().stream().sorted(DISPLAY_ORDER).toList();
	}

	List<Resource> findByEnabledTrue();

	/** 예약 저장 전에 자원 행을 잠가서, 같은 자원에 동시에 들어온 예약이 겹쳐 저장되지 않게 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Resource r where r.id = :id")
	Optional<Resource> findForUpdate(@Param("id") Long id);
}
