package th_prj.th_company.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

	Optional<Attendance> findByMemberIdAndWorkDate(Long memberId, LocalDate workDate);

	boolean existsByMemberIdAndWorkDate(Long memberId, LocalDate workDate);

	List<Attendance> findByMemberIdAndWorkDateBetweenOrderByWorkDateAsc(Long memberId, LocalDate from, LocalDate to);

	List<Attendance> findByWorkDate(LocalDate workDate);
}
