package th_prj.th_company.attendance;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import th_prj.th_company.leave.LeaveType;

/**
 * 하루 근태 (내 근태 월별 표, 전체 근태 현황의 한 줄).
 *
 * @param late  출근 시각이 근무 시작 시각보다 늦음
 * @param early 퇴근 시각이 근무 종료 시각보다 이름
 * @param holiday 공휴일 이름, 아니면 null
 * @param leave 그날의 승인된 휴가 (주말·공휴일 제외), 없으면 null
 */
public record AttendanceDay(LocalDate date, LocalDateTime checkInAt, LocalDateTime checkOutAt, State state,
		boolean late, boolean early, String correctedBy, String correctionNote, String holiday, LeaveType leave) {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M.d (E)", Locale.KOREAN);

	public enum State {
		/** 출근·퇴근 모두 기록 */
		WORKED,
		/** 오늘 출근, 아직 퇴근 전 */
		WORKING,
		/** 지난 날인데 퇴근 기록 없음 */
		NO_CHECKOUT,
		/** 지난 평일인데 출근 기록 없음 */
		ABSENT,
		/** 오늘(평일) 아직 출근 기록 없음 */
		NOT_YET,
		/** 주말·공휴일, 기록 없음 */
		OFF,
		/** 휴가 (출근 기록 없음) */
		LEAVE,
		/** 앞으로의 날짜 또는 입사 전 */
		NONE
	}

	/** 화면 표시용 상태 꼬리표 */
	public record Tag(String text, String css) {
	}

	public boolean isWeekend() {
		return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
	}

	/** 주말 또는 공휴일 */
	public boolean isOffDay() {
		return isWeekend() || holiday != null;
	}

	public boolean isCorrected() {
		return correctedBy != null;
	}

	public String displayDate() {
		return date.format(DAY);
	}

	public String displayCheckIn() {
		return checkInAt == null ? "" : checkInAt.format(TIME);
	}

	public String displayCheckOut() {
		if (checkOutAt == null) {
			return "";
		}
		return checkOutAt.toLocalDate().isAfter(date) ? "익일 " + checkOutAt.format(TIME) : checkOutAt.format(TIME);
	}

	/** 출근~퇴근 분. 퇴근 전이면 0 */
	public long workedMinutes() {
		return checkInAt == null || checkOutAt == null ? 0 : Duration.between(checkInAt, checkOutAt).toMinutes();
	}

	public String displayWorked() {
		return state == State.WORKED ? AttendanceMonth.hoursMinutes(workedMinutes()) : "";
	}

	/** 엑셀용 상태 글자: 화면의 태그를 이어 붙인다 */
	public String statusText() {
		String text = String.join(", ", tags().stream().map(Tag::text).toList());
		return isCorrected() ? text + (text.isEmpty() ? "" : ", ") + "정정" : text;
	}

	public List<Tag> tags() {
		List<Tag> tags = new ArrayList<>();
		if (leave != null) {
			tags.add(new Tag(leave.getLabel(), "tag-info"));
		}
		switch (state) {
			case WORKED, WORKING, NO_CHECKOUT -> {
				if (isOffDay()) {
					tags.add(new Tag("휴일근무", "tag-info"));
				} else {
					if (late) {
						tags.add(new Tag("지각", "tag-warn"));
					}
					if (early) {
						tags.add(new Tag("조퇴", "tag-warn"));
					}
				}
				if (state == State.WORKING) {
					tags.add(new Tag("근무중", "tag-info"));
				} else if (state == State.NO_CHECKOUT) {
					tags.add(new Tag("퇴근 미기록", "tag-bad"));
				} else if (tags.isEmpty()) {
					tags.add(new Tag("정상", "tag-ok"));
				}
			}
			case ABSENT -> tags.add(new Tag("결근", "tag-bad"));
			case NOT_YET -> tags.add(new Tag("미출근", "tag-muted"));
			case OFF -> tags.add(holiday != null ? new Tag(holiday, "tag-info") : new Tag("휴무", "tag-muted"));
			case LEAVE, NONE -> {
			}
		}
		return tags;
	}
}
