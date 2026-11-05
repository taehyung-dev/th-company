package th_prj.th_company.reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	/** [from, to) 와 겹치는 예약 (일간 현황) */
	@Query("""
		select r from Reservation r
		join fetch r.owner
		join fetch r.resource
		where r.startAt < :to and r.endAt > :from
		order by r.startAt, r.id
		""")
	List<Reservation> findOverlapping(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

	/** 같은 자원에 [start, end) 와 겹치는 다른 예약 (excludeId 는 수정 중인 자기 자신, 새 예약이면 0) */
	@Query("""
		select r from Reservation r
		join fetch r.owner
		where r.resource.id = :resourceId and r.startAt < :end and r.endAt > :start and r.id <> :excludeId
		order by r.startAt
		""")
	List<Reservation> findConflicts(@Param("resourceId") Long resourceId, @Param("start") LocalDateTime start,
			@Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

	/** 내가 예약했거나 참석하는 예약 중 아직 끝나지 않은 것 */
	@Query("""
		select r from Reservation r
		join fetch r.resource
		join fetch r.owner
		left join fetch r.series
		where (r.owner.id = :memberId or exists (select a from r.attendees a where a.id = :memberId))
			and r.endAt > :now
		order by r.startAt
		""")
	List<Reservation> findUpcoming(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

	/** 내가 예약했거나 참석한 지난 예약 (since 이후, 최근 것부터) */
	@Query("""
		select r from Reservation r
		join fetch r.resource
		join fetch r.owner
		left join fetch r.series
		where (r.owner.id = :memberId or exists (select a from r.attendees a where a.id = :memberId))
			and r.endAt <= :now and r.startAt >= :since
		order by r.startAt desc
		""")
	List<Reservation> findPast(@Param("memberId") Long memberId, @Param("now") LocalDateTime now,
			@Param("since") LocalDateTime since);

	@Query("select r from Reservation r join fetch r.owner join fetch r.resource left join fetch r.series where r.id = :id")
	Optional<Reservation> findDetail(@Param("id") Long id);

	/** 반복 예약에서 from 이후의 것 */
	List<Reservation> findBySeriesIdAndStartAtGreaterThanEqual(Long seriesId, LocalDateTime from);

	long countBySeriesId(Long seriesId);

	long countByResourceId(Long resourceId);

	@Query("select r.resource.id, count(r) from Reservation r group by r.resource.id")
	List<Object[]> countGroupByResource();
}
