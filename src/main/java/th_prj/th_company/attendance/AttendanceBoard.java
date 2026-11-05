package th_prj.th_company.attendance;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 근태관리자 화면: 하루 동안 재직자 전체(또는 부서)의 근태.
 */
public record AttendanceBoard(LocalDate date, LocalDate prev, LocalDate next, List<Row> rows) {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);

	public record Row(Long memberId, String name, String deptName, String gradeName, AttendanceDay day) {
	}

	public String displayDate() {
		return date.format(DATE);
	}

	public long checkedIn() {
		return rows.stream().filter(r -> r.day().checkInAt() != null).count();
	}

	public long late() {
		return rows.stream().filter(r -> r.day().late() && !r.day().isOffDay()).count();
	}

	public long missing() {
		return rows.stream().filter(r -> r.day().state() == AttendanceDay.State.ABSENT
			|| r.day().state() == AttendanceDay.State.NOT_YET).count();
	}

	public long onLeave() {
		return rows.stream().filter(r -> r.day().state() == AttendanceDay.State.LEAVE).count();
	}

	public long checkedOut() {
		return rows.stream().filter(r -> r.day().checkOutAt() != null).count();
	}
}
