package th_prj.th_company.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 월간 달력. weeks 는 일요일부터 토요일까지 7칸씩.
 */
public record MonthView(YearMonth month, List<List<Day>> weeks) {

	public YearMonth previous() {
		return month.minusMonths(1);
	}

	public YearMonth next() {
		return month.plusMonths(1);
	}

	public String title() {
		return month.getYear() + "년 " + month.getMonthValue() + "월";
	}

	/** @param holiday 공휴일 이름, 아니면 null */
	public record Day(LocalDate date, boolean inMonth, boolean today, String holiday, List<Item> items) {

		public boolean isSunday() {
			return date.getDayOfWeek() == DayOfWeek.SUNDAY;
		}

		public boolean isSaturday() {
			return date.getDayOfWeek() == DayOfWeek.SATURDAY;
		}
	}

	/**
	 * 달력 칸 안의 일정 한 줄.
	 *
	 * @param timeLabel 그날 시작하는 시간 일정이면 "09:00", 종일/이어지는 일정이면 빈 값
	 * @param continued 전날부터 이어지는 일정
	 */
	public record Item(Long id, ScheduleType type, String title, String timeLabel, boolean allDay, boolean continued) {
	}
}
