package th_prj.th_company.member;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByLoginId(String loginId);

	boolean existsByLoginId(String loginId);

	boolean existsByLoginIdIgnoreCase(String loginId);

	boolean existsByLoginIdIgnoreCaseAndIdNot(String loginId, Long id);

	boolean existsByEmployeeNo(String employeeNo);

	boolean existsByEmployeeNoAndIdNot(String employeeNo, Long id);

	boolean existsByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

	long countByDepartmentId(Long departmentId);

	boolean existsByGradeIdOrDutyId(Long gradeId, Long dutyId);

	/** 조직도용: 부서가 지정된 재직(사용) 인원 전체 */
	@Query("""
		select m from Member m
		join fetch m.department
		left join fetch m.grade
		left join fetch m.duty
		where m.enabled = true
		""")
	List<Member> findActiveWithDepartment();

	/** 권한 관리 화면: 관리 권한이 하나라도 있는 사용자 */
	@Query("""
		select distinct m from Member m
		join fetch m.permissions
		left join fetch m.department
		left join fetch m.grade
		order by m.name
		""")
	List<Member> findWithPermissions();

	/**
	 * 사내 주소록: 재직(사용) 인원 검색. allDepartments 가 false 면 departmentIds 에 속한 인원만.
	 */
	@Query(value = """
		select m from Member m
		left join fetch m.department d
		left join fetch m.grade
		left join fetch m.duty
		where m.enabled = true
			and (:allDepartments = true or d.id in :departmentIds)
			and (:keyword is null
				or lower(m.name) like lower(concat('%', :keyword, '%'))
				or lower(m.email) like lower(concat('%', :keyword, '%'))
				or m.phone like concat('%', :keyword, '%')
				or lower(d.name) like lower(concat('%', :keyword, '%')))
		order by m.name, m.id
		""",
		countQuery = """
		select count(m) from Member m
		left join m.department d
		where m.enabled = true
			and (:allDepartments = true or d.id in :departmentIds)
			and (:keyword is null
				or lower(m.name) like lower(concat('%', :keyword, '%'))
				or lower(m.email) like lower(concat('%', :keyword, '%'))
				or m.phone like concat('%', :keyword, '%')
				or lower(d.name) like lower(concat('%', :keyword, '%')))
		""")
	Page<Member> searchDirectory(@Param("allDepartments") boolean allDepartments,
			@Param("departmentIds") List<Long> departmentIds,
			@Param("keyword") String keyword, Pageable pageable);

	/** [부서 id, 인원 수] 목록 */
	@Query("select m.department.id, count(m) from Member m where m.department is not null group by m.department.id")
	List<Object[]> countGroupByDepartment();

	/**
	 * 관리자 사용자 목록 검색. 조건이 null 이면 무시한다.
	 */
	@Query("""
		select m from Member m
		left join fetch m.department
		left join fetch m.grade
		left join fetch m.duty
		where (:keyword is null
				or lower(m.name) like lower(concat('%', :keyword, '%'))
				or lower(m.loginId) like lower(concat('%', :keyword, '%'))
				or m.employeeNo like concat('%', :keyword, '%'))
			and (:departmentId is null or m.department.id = :departmentId)
			and (:enabled is null or m.enabled = :enabled)
		order by m.name
		""")
	List<Member> search(@Param("keyword") String keyword,
			@Param("departmentId") Long departmentId,
			@Param("enabled") Boolean enabled);
}
