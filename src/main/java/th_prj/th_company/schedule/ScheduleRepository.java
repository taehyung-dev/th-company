package th_prj.th_company.schedule;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

	/**
	 * [from, to) 기간과 겹치고 사용자에게 보이는 일정.
	 * 회사일정 전부 + 내 부서의 부서일정 + 내 개인일정.
	 */
	@Query("""
		select s from Schedule s
		join fetch s.owner
		left join fetch s.department
		where s.startAt < :to and s.endAt >= :from
			and (s.type = th_prj.th_company.schedule.ScheduleType.COMPANY
				or (s.type = th_prj.th_company.schedule.ScheduleType.DEPARTMENT and s.department.id = :deptId)
				or (s.type = th_prj.th_company.schedule.ScheduleType.PERSONAL and s.owner.id = :memberId))
		order by s.allDay desc, s.startAt, s.id
		""")
	List<Schedule> findVisible(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
			@Param("memberId") Long memberId, @Param("deptId") Long deptId);

	@Query("select s from Schedule s join fetch s.owner left join fetch s.department where s.id = :id")
	Optional<Schedule> findDetail(@Param("id") Long id);
}
