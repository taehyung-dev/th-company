package th_prj.th_company.reservation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import th_prj.th_company.holiday.HolidayService;

/**
 * 회의실 반복 예약 규칙.
 */
public enum RepeatRule {
	NONE("반복 안 함"),
	DAILY("매일 (주말 제외)"),
	WEEKLY("매주"),
	BIWEEKLY("2주마다"),
	MONTHLY("매월");

	private final String label;

	RepeatRule(String label) {
		this.label = label;
	}

	public String getLabel() { return label; }

	/** first 부터 until 까지 예약할 날짜. 매월은 그 날짜가 없는 달(31일 등)을 건너뛴다 */
	public List<LocalDate> dates(LocalDate first, LocalDate until) {
		List<LocalDate> result = new ArrayList<>();
		switch (this) {
			case NONE -> result.add(first);
			case DAILY -> first.datesUntil(until.plusDays(1))
				.filter(d -> !HolidayService.isWeekend(d))
				.forEach(result::add);
			case WEEKLY -> addEvery(result, first, until, 7);
			case BIWEEKLY -> addEvery(result, first, until, 14);
			case MONTHLY -> {
				for (int i = 0; !first.plusMonths(i).isAfter(until); i++) {
					LocalDate d = first.plusMonths(i);
					if (d.getDayOfMonth() == first.getDayOfMonth()) {
						result.add(d);
					}
				}
			}
		}
		return result;
	}

	private static void addEvery(List<LocalDate> result, LocalDate first, LocalDate until, int days) {
		for (LocalDate d = first; !d.isAfter(until); d = d.plusDays(days)) {
			result.add(d);
		}
	}
}
