package th_prj.th_company.address;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactRepository extends JpaRepository<Contact, Long> {

	/**
	 * 개인 주소록 검색. favoriteOnly, groupId, keyword 는 지정한 것만 조건이 된다.
	 */
	@Query(value = """
		select c from Contact c
		left join fetch c.group g
		where c.owner.id = :ownerId
			and (:favoriteOnly = false or c.favorite = true)
			and (:groupId is null or g.id = :groupId)
			and (:keyword is null
				or lower(c.name) like lower(concat('%', :keyword, '%'))
				or lower(c.company) like lower(concat('%', :keyword, '%'))
				or lower(c.department) like lower(concat('%', :keyword, '%'))
				or lower(c.email) like lower(concat('%', :keyword, '%'))
				or c.mobile like concat('%', :keyword, '%')
				or c.phone like concat('%', :keyword, '%'))
		order by c.name, c.id
		""",
		countQuery = """
		select count(c) from Contact c
		left join c.group g
		where c.owner.id = :ownerId
			and (:favoriteOnly = false or c.favorite = true)
			and (:groupId is null or g.id = :groupId)
			and (:keyword is null
				or lower(c.name) like lower(concat('%', :keyword, '%'))
				or lower(c.company) like lower(concat('%', :keyword, '%'))
				or lower(c.department) like lower(concat('%', :keyword, '%'))
				or lower(c.email) like lower(concat('%', :keyword, '%'))
				or c.mobile like concat('%', :keyword, '%')
				or c.phone like concat('%', :keyword, '%'))
		""")
	Page<Contact> search(@Param("ownerId") Long ownerId, @Param("favoriteOnly") boolean favoriteOnly,
			@Param("groupId") Long groupId, @Param("keyword") String keyword, Pageable pageable);

	@Query("select c from Contact c left join fetch c.group where c.owner.id = :ownerId order by c.name, c.id")
	List<Contact> findAllByOwner(@Param("ownerId") Long ownerId);

	long countByOwnerId(Long ownerId);

	long countByOwnerIdAndFavoriteTrue(Long ownerId);

	/** [그룹 id, 연락처 수] 목록 */
	@Query("select c.group.id, count(c) from Contact c where c.owner.id = :ownerId and c.group is not null group by c.group.id")
	List<Object[]> countByGroup(@Param("ownerId") Long ownerId);

	boolean existsByOwnerIdAndNameAndEmail(Long ownerId, String name, String email);

	/** 그룹을 지울 때 소속 연락처는 그룹 없음으로 */
	@Modifying
	@Query("update Contact c set c.group = null where c.group.id = :groupId")
	int clearGroup(@Param("groupId") Long groupId);
}
