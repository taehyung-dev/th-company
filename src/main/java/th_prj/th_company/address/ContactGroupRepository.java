package th_prj.th_company.address;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactGroupRepository extends JpaRepository<ContactGroup, Long> {

	List<ContactGroup> findByOwnerIdOrderByNameAsc(Long ownerId);

	boolean existsByOwnerIdAndName(Long ownerId, String name);

	boolean existsByOwnerIdAndNameAndIdNot(Long ownerId, String name, Long id);
}
