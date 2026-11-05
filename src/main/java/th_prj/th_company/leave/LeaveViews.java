package th_prj.th_company.leave;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 휴가 화면용 값들.
 */
public final class LeaveViews {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private LeaveViews() {
	}

	/** 1 → "1", 1.5 → "1.5" */
	public static String days(double d) {
		return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
	}

	static String period(LocalDate start, LocalDate end) {
		return start.equals(end) ? start.format(DATE) : start.format(DATE) + " ~ " + end.format(DATE);
	}

	/** 그해 연차 현황. pending: 결재 중인 일수 (남은 일수에서 미리 뺀다) */
	public record Summary(int year, double granted, double used, double pending, boolean custom) {

		public double remaining() {
			return granted - used - pending;
		}

		public String displayGranted() { return days(granted); }
		public String displayUsed() { return days(used); }
		public String displayPending() { return days(pending); }
		public String displayRemaining() { return days(remaining()); }
	}

	/**
	 * 휴가 한 건.
	 *
	 * @param canCancel 지금 이 화면의 사용자가 취소할 수 있음
	 */
	public record Row(Long id, Long memberId, String memberName, String deptName, LeaveType type,
			LocalDate startDate, LocalDate endDate, double days, String reason, LeaveStatus status,
			Long documentId, boolean canCancel) {

		public String period() {
			return LeaveViews.period(startDate, endDate);
		}

		public String displayDays() {
			return LeaveViews.days(days) + "일";
		}
	}

	/** 근태관리자: 직원별 연차 현황 */
	public record MemberRow(Long memberId, String name, String deptName, LocalDate hireDate, Summary summary) {

		public String displayHireDate() {
			return hireDate == null ? "-" : hireDate.format(DATE);
		}
	}
}
