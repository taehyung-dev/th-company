package th_prj.th_company.reservation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * 자원예약 화면용 값들.
 */
public final class ReservationViews {

	private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter MD_HM = DateTimeFormatter.ofPattern("M.d HH:mm");
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private ReservationViews() {
	}

	/** 하루 안이면 시각만, 며칠에 걸치면 날짜도 */
	static String period(LocalDateTime start, LocalDateTime end) {
		if (start.toLocalDate().equals(end.toLocalDate())) {
			return start.format(HM) + " ~ " + end.format(HM);
		}
		return start.format(MD_HM) + " ~ " + end.format(MD_HM);
	}

	static String displayDate(LocalDate d) {
		return d.getYear() + "년 " + d.getMonthValue() + "월 " + d.getDayOfMonth() + "일 ("
			+ d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN) + ")";
	}

	/**
	 * 일간 예약 현황. 자원별 한 줄에 startHour ~ endHour 를 가로로 펼친 타임라인.
	 * nowPct: 오늘이면 현재 시각 위치(%), 아니면 null
	 */
	public record DayBoard(ReservationKind kind, LocalDate date, ResourceType type, int startHour, int endHour,
			List<Integer> hours, List<Row> rows, Double nowPct, boolean today) {

		public String displayDate() {
			return ReservationViews.displayDate(date);
		}

		public boolean weekend() {
			return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
		}

		public LocalDate prev() { return date.minusDays(1); }
		public LocalDate next() { return date.plusDays(1); }

		public int reservationCount() {
			return rows.stream().mapToInt(r -> r.blocks().size()).sum();
		}
	}

	public record Row(Long resourceId, ResourceType type, String name, String location, Integer capacity,
			String description, boolean enabled, List<Block> blocks) {
	}

	/** 타임라인의 예약 막대. left/width 는 % */
	public record Block(Long id, String title, String ownerName, String ownerDept, String time,
			double left, double width, boolean mine) {

		public String style() {
			return String.format(Locale.ROOT, "left:%.3f%%;width:%.3f%%", left, width);
		}
	}

	/** 예약 한 건 (상세, 내 예약 목록) */
	public record Item(Long id, ReservationKind kind, Long resourceId, ResourceType resourceType, String resourceName, String location,
			String title, String ownerName, String ownerDept, LocalDateTime startAt, LocalDateTime endAt,
			String memo, boolean editable, boolean ended, Long seriesId, RepeatRule repeat, LocalDate repeatUntil,
			List<String> attendeeNames, boolean attending) {

		public String displayDate() {
			return ReservationViews.displayDate(startAt.toLocalDate());
		}

		/** 목록용: 2026.10.29 (목) */
		public String shortDate() {
			LocalDate d = startAt.toLocalDate();
			return String.format("%d.%02d.%02d (%s)", d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
				d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
		}

		public String period() {
			return ReservationViews.period(startAt, endAt);
		}

		public boolean multiDay() {
			return !startAt.toLocalDate().equals(endAt.toLocalDate());
		}

		/** 상세 화면: 2026년 10월 28일 (수) 10:00 ~ 11:00 */
		public String displayPeriod() {
			if (!multiDay()) {
				return displayDate() + " " + period();
			}
			return ReservationViews.displayDate(startAt.toLocalDate()) + " " + startAt.format(HM) + " ~ "
				+ ReservationViews.displayDate(endAt.toLocalDate()) + " " + endAt.format(HM);
		}

		public String repeatLabel() {
			return repeat == null ? null : repeat.getLabel() + " 반복 · " + repeatUntil.format(YMD) + "까지";
		}
	}

	/** 저장 결과. count: 만든 예약 수, skipped: 겹쳐서 건너뛴 날짜 수 */
	public record Saved(Long id, int count, int skipped) {
	}

	/** 예약 화면에서 같은 날 이미 잡힌 시간 */
	public record Taken(String time, String title, String ownerName, boolean self) {
	}

	/** 관리자 자원 목록 */
	public record ResourceRow(Long id, ResourceType type, String name, String location, Integer capacity,
			String description, int sortOrder, boolean enabled, long reservationCount) {
	}
}
