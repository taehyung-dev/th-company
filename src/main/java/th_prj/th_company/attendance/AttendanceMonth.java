package th_prj.th_company.attendance;

import java.time.YearMonth;
import java.util.List;

/**
 * 한 사람의 월별 근태.
 *
 * @param next 다음 달 (이번 달 이후로는 넘어가지 않으므로 null 일 수 있음)
 */
public record AttendanceMonth(Long memberId, String memberName, String deptName, YearMonth month,
		YearMonth prev, YearMonth next, List<AttendanceDay> days) {

	public long workedDays() {
		return days.stream().filter(d -> d.checkInAt() != null).count();
	}

	public long lateCount() {
		return days.stream().filter(d -> d.late() && !d.isOffDay()).count();
	}

	public long earlyCount() {
		return days.stream().filter(d -> d.early() && !d.isOffDay()).count();
	}

	/** 휴가로 쉰 날 (반차 포함) */
	public long leaveDays() {
		return days.stream().filter(d -> d.leave() != null).count();
	}

	public long absentCount() {
		return count(AttendanceDay.State.ABSENT);
	}

	public long noCheckoutCount() {
		return count(AttendanceDay.State.NO_CHECKOUT);
	}

	public String displayTotalWorked() {
		return hoursMinutes(days.stream().mapToLong(AttendanceDay::workedMinutes).sum());
	}

	/** 퇴근까지 기록된 날의 평균 */
	public String displayAverageWorked() {
		long done = count(AttendanceDay.State.WORKED);
		return done == 0 ? "-" : hoursMinutes(days.stream().mapToLong(AttendanceDay::workedMinutes).sum() / done);
	}

	public String displayMonth() {
		return month.getYear() + "년 " + month.getMonthValue() + "월";
	}

	private long count(AttendanceDay.State state) {
		return days.stream().filter(d -> d.state() == state).count();
	}

	static String hoursMinutes(long minutes) {
		return minutes / 60 + "시간 " + minutes % 60 + "분";
	}
}
