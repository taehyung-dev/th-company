package th_prj.th_company.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 일정 상세/목록용.
 *
 * @param canEdit 현재 사용자가 수정/삭제할 수 있음
 * @param participantNames 지정한 참여자
 */
public record ScheduleItem(Long id, ScheduleType type, String title, String description,
		LocalDateTime startAt, LocalDateTime endAt, boolean allDay,
		String ownerName, String departmentName, boolean canEdit, List<String> participantNames) {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	/** 예: 2026.10.23 (금) 09:00 ~ 10:00, 2026.10.23 (금) ~ 2026.10.25 (일) 종일 */
	public String displayPeriod() {
		LocalDate s = startAt.toLocalDate();
		LocalDate e = endAt.toLocalDate();
		if (allDay) {
			return s.equals(e) ? s.format(DATE) + " 종일" : s.format(DATE) + " ~ " + e.format(DATE) + " 종일";
		}
		if (s.equals(e)) {
			return s.format(DATE) + " " + startAt.format(TIME) + " ~ " + endAt.format(TIME);
		}
		return s.format(DATE) + " " + startAt.format(TIME) + " ~ " + e.format(DATE) + " " + endAt.format(TIME);
	}

	/** 홈 화면용: 종일 또는 시작 시간 */
	public String displayTime() {
		return allDay ? "종일" : startAt.format(TIME);
	}
}
