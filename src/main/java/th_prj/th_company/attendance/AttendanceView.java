package th_prj.th_company.attendance;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 홈 화면 출퇴근 포틀릿.
 *
 * @param workDate   현재 근무일 (오전 6시 기준, 6시 전이면 전날)
 * @param checkInAt  출근 시각 (없으면 null)
 * @param checkOutAt 퇴근 시각 (없으면 null)
 */
public record AttendanceView(LocalDate workDate, LocalDateTime checkInAt, LocalDateTime checkOutAt) {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN);

	public String displayWorkDate() {
		return workDate.format(DATE);
	}

	public String displayCheckIn() {
		return checkInAt == null ? "--:--" : checkInAt.format(TIME);
	}

	public String displayCheckOut() {
		if (checkOutAt == null) {
			return "--:--";
		}
		// 다음 날 새벽 퇴근이면 날짜 표시
		return checkOutAt.toLocalDate().isAfter(checkInAt.toLocalDate())
			? "익일 " + checkOutAt.format(TIME) : checkOutAt.format(TIME);
	}

	public boolean isCheckedIn() {
		return checkInAt != null;
	}

	public boolean isCheckedOut() {
		return checkOutAt != null;
	}

	/** 퇴근했으면 출근~퇴근 시간, 아니면 null. 예: "8시간 35분" */
	public String displayWorked() {
		if (checkInAt == null || checkOutAt == null) {
			return null;
		}
		Duration d = Duration.between(checkInAt, checkOutAt);
		return d.toHours() + "시간 " + d.toMinutesPart() + "분";
	}
}
