package th_prj.th_company.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;

/**
 * 공휴일. 양력으로 고정된 법정 공휴일은 버튼 한 번으로 넣고,
 * 음력 공휴일(설날, 추석, 부처님오신날)과 대체공휴일, 회사 지정 휴일은 날짜를 직접 넣는다.
 */
@Service
@Transactional(readOnly = true)
public class HolidayService {

	private static final Map<MonthDay, String> FIXED = fixed();

	private final HolidayRepository holidayRepository;

	public HolidayService(HolidayRepository holidayRepository) {
		this.holidayRepository = holidayRepository;
	}

	private static Map<MonthDay, String> fixed() {
		Map<MonthDay, String> m = new LinkedHashMap<>();
		m.put(MonthDay.of(1, 1), "신정");
		m.put(MonthDay.of(3, 1), "삼일절");
		m.put(MonthDay.of(5, 5), "어린이날");
		m.put(MonthDay.of(6, 6), "현충일");
		m.put(MonthDay.of(8, 15), "광복절");
		m.put(MonthDay.of(10, 3), "개천절");
		m.put(MonthDay.of(10, 9), "한글날");
		m.put(MonthDay.of(12, 25), "성탄절");
		return m;
	}

	public record Row(Long id, LocalDate date, String name) {

		public String displayDate() {
			return String.format("%d.%02d.%02d (%s)", date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
				date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
		}

		public boolean weekend() {
			return HolidayService.isWeekend(date);
		}
	}

	public List<Row> list(int year) {
		return holidayRepository
			.findByHolidayDateBetweenOrderByHolidayDate(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
			.map(h -> new Row(h.getId(), h.getHolidayDate(), h.getName()))
			.toList();
	}

	/** 기간 안의 공휴일 (날짜 → 이름) */
	public Map<LocalDate, String> between(LocalDate from, LocalDate to) {
		Map<LocalDate, String> result = new LinkedHashMap<>();
		holidayRepository.findByHolidayDateBetweenOrderByHolidayDate(from, to)
			.forEach(h -> result.put(h.getHolidayDate(), h.getName()));
		return result;
	}

	public static boolean isWeekend(LocalDate d) {
		return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

	@Transactional
	public void add(HolidayForm form) {
		if (holidayRepository.existsByHolidayDate(form.getDate())) {
			throw new BusinessException("이미 등록된 날짜입니다.");
		}
		holidayRepository.save(new Holiday(form.getDate(), form.getName().trim()));
	}

	@Transactional
	public void delete(Long id) {
		holidayRepository.deleteById(id);
	}

	/** 그해의 양력 고정 공휴일 중 없는 것을 넣고, 넣은 개수를 돌려준다 */
	@Transactional
	public int addFixed(int year) {
		int added = 0;
		for (Map.Entry<MonthDay, String> e : FIXED.entrySet()) {
			LocalDate date = e.getKey().atYear(year);
			if (!holidayRepository.existsByHolidayDate(date)) {
				holidayRepository.save(new Holiday(date, e.getValue()));
				added++;
			}
		}
		return added;
	}
}
