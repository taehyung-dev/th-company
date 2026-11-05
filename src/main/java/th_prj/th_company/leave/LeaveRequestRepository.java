package th_prj.th_company.leave;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

	/** 한 사람의 기간 안 휴가 (최근 것부터) */
	@Query("""
		select r from LeaveRequest r
		where r.member.id = :memberId and r.startDate <= :to and r.endDate >= :from
		order by r.startDate desc, r.id desc
		""")
	List<LeaveRequest> findByMember(@Param("memberId") Long memberId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 전체 직원의 기간 안 휴가 (근태관리자) */
	@Query("""
		select r from LeaveRequest r
		join fetch r.member m
		left join fetch m.department
		where r.startDate <= :to and r.endDate >= :from
		order by r.startDate desc, r.id desc
		""")
	List<LeaveRequest> findAllBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	/** 기간과 겹치는, 아직 살아 있는 휴가 */
	@Query("""
		select r from LeaveRequest r
		where r.member.id = :memberId and r.startDate <= :end and r.endDate >= :start and r.status in :statuses
		""")
	List<LeaveRequest> findOverlapping(@Param("memberId") Long memberId, @Param("start") LocalDate start,
			@Param("end") LocalDate end, @Param("statuses") Collection<LeaveStatus> statuses);

	/** 그날 승인된 휴가 (전체 근태 현황) */
	@Query("select r from LeaveRequest r where r.status = :status and r.startDate <= :date and r.endDate >= :date")
	List<LeaveRequest> findOn(@Param("date") LocalDate date, @Param("status") LeaveStatus status);

	Optional<LeaveRequest> findByDocumentId(Long documentId);
}
