package th_prj.th_company.approval;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, Long> {

	/** 내가 맡긴 대결 (최근 것부터) */
	@Query("""
		select d from ApprovalDelegation d
		join fetch d.delegate m
		left join fetch m.department
		left join fetch m.grade
		where d.delegator.id = :memberId
		order by d.startDate desc, d.id desc
		""")
	List<ApprovalDelegation> findByDelegator(@Param("memberId") Long memberId);

	/** 내가 맡은 대결 중 끝나지 않은 것 */
	@Query("""
		select d from ApprovalDelegation d
		join fetch d.delegator m
		left join fetch m.department
		left join fetch m.grade
		where d.delegate.id = :memberId and d.endDate >= :today
		order by d.startDate
		""")
	List<ApprovalDelegation> findCurrentByDelegate(@Param("memberId") Long memberId, @Param("today") LocalDate today);

	/** 오늘 나에게 대결을 맡긴 사람들 */
	@Query("""
		select d.delegator.id from ApprovalDelegation d
		where d.delegate.id = :memberId and d.startDate <= :today and d.endDate >= :today
		""")
	List<Long> findActiveDelegatorIds(@Param("memberId") Long memberId, @Param("today") LocalDate today);

	/** 오늘 이 사람의 대결자 (알림용, 기간이 겹치지 않으므로 많아야 한 명) */
	@Query("""
		select d from ApprovalDelegation d
		join fetch d.delegate
		where d.delegator.id = :memberId and d.startDate <= :today and d.endDate >= :today
		""")
	List<ApprovalDelegation> findActiveByDelegator(@Param("memberId") Long memberId, @Param("today") LocalDate today);

	@Query("""
		select count(d) > 0 from ApprovalDelegation d
		where d.delegator.id = :memberId and d.startDate <= :end and d.endDate >= :start
		""")
	boolean existsOverlapping(@Param("memberId") Long memberId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
