package th_prj.th_company.schedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.board.PostDetail.FileInfo;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.file.FileAttachmentService;
import th_prj.th_company.file.FileOwner;
import th_prj.th_company.holiday.HolidayService;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.member.MemberSelection;
import th_prj.th_company.notification.NotificationService;

/**
 * 일정. 권한 규칙:
 * <ul>
 *   <li>회사일정: 모두 보기, 관리자만 등록</li>
 *   <li>부서일정: 같은 부서원만 보기, 부서원 누구나 등록</li>
 *   <li>개인일정: 본인만</li>
 *   <li>수정/삭제: 등록자 또는 관리자</li>
 *   <li>참여자: 지정하면 종류와 상관없이 일정을 본다</li>
 *   <li>알림: 저장할 때 선택하면 보낸다. 받는 사람은 지정한 참여자와 그 일정을 보는 사람 (회사일정은 전 직원, 부서일정은 부서원)</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ScheduleService {

	/** 종일 일정의 종료 시각 */
	private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);
	public static final int MAX_PARTICIPANTS = 50;

	private final ScheduleRepository scheduleRepository;
	private final MemberRepository memberRepository;
	private final HolidayService holidayService;
	private final FileAttachmentService fileService;
	private final MemberSelection memberSelection;
	private final NotificationService notificationService;

	public ScheduleService(ScheduleRepository scheduleRepository, MemberRepository memberRepository,
			HolidayService holidayService, FileAttachmentService fileService, MemberSelection memberSelection,
			NotificationService notificationService) {
		this.scheduleRepository = scheduleRepository;
		this.memberRepository = memberRepository;
		this.holidayService = holidayService;
		this.fileService = fileService;
		this.memberSelection = memberSelection;
		this.notificationService = notificationService;
	}

	// ---------- 조회 ----------

	public MonthView month(YearMonth month, String loginId) {
		Member me = member(loginId);
		LocalDate gridStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
		LocalDate gridEnd = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
		List<Schedule> schedules = visible(me, gridStart, gridEnd.plusDays(1));

		var holidays = holidayService.between(gridStart, gridEnd);
		LocalDate today = LocalDate.now();
		List<List<MonthView.Day>> weeks = new ArrayList<>();
		List<MonthView.Day> week = new ArrayList<>();
		for (LocalDate d = gridStart; !d.isAfter(gridEnd); d = d.plusDays(1)) {
			week.add(new MonthView.Day(d, YearMonth.from(d).equals(month), d.equals(today), holidays.get(d), itemsOn(d, schedules)));
			if (week.size() == 7) {
				weeks.add(week);
				week = new ArrayList<>();
			}
		}
		return new MonthView(month, weeks);
	}

	private static List<MonthView.Item> itemsOn(LocalDate day, List<Schedule> schedules) {
		LocalDateTime dayStart = day.atStartOfDay();
		LocalDateTime nextDay = day.plusDays(1).atStartOfDay();
		List<MonthView.Item> items = new ArrayList<>();
		for (Schedule s : schedules) {
			boolean overlaps = s.getStartAt().isBefore(nextDay)
				&& (s.getEndAt().isAfter(dayStart) || !s.getStartAt().isBefore(dayStart));
			if (!overlaps) {
				continue;
			}
			boolean startsToday = s.getStartAt().toLocalDate().equals(day);
			String time = !s.isAllDay() && startsToday ? s.getStartAt().toLocalTime().toString() : "";
			items.add(new MonthView.Item(s.getId(), s.getType(), s.getTitle(), time, s.isAllDay(), !startsToday));
		}
		return items;
	}

	/** 홈 화면: 오늘 일정 */
	public List<ScheduleItem> today(String loginId) {
		Member me = member(loginId);
		LocalDate today = LocalDate.now();
		return visible(me, today, today.plusDays(1)).stream()
			.map(s -> toItem(s, me, false))
			.toList();
	}

	public ScheduleItem get(Long id, String loginId, boolean manager) {
		Member me = member(loginId);
		Schedule s = find(id);
		if (!manager && !canSee(s, me)) {
			throw new AccessDeniedException("이 일정을 볼 권한이 없습니다.");
		}
		return toItem(s, me, manager);
	}

	private List<Schedule> visible(Member me, LocalDate from, LocalDate to) {
		Long deptId = me.getDepartment() == null ? null : me.getDepartment().getId();
		return scheduleRepository.findVisible(from.atStartOfDay(), to.atStartOfDay(), me.getId(), deptId);
	}

	// ---------- 등록/수정/삭제 ----------

	/** 등록 가능한 종류: 회사일정은 관리자·일정관리자, 부서일정은 부서가 있는 사용자 */
	public List<ScheduleType> allowedTypes(String loginId, boolean manager) {
		Member me = member(loginId);
		List<ScheduleType> types = new ArrayList<>();
		if (manager) {
			types.add(ScheduleType.COMPANY);
		}
		if (me.getDepartment() != null) {
			types.add(ScheduleType.DEPARTMENT);
		}
		types.add(ScheduleType.PERSONAL);
		return types;
	}

	public ScheduleForm newForm(LocalDate date) {
		ScheduleForm form = new ScheduleForm();
		form.setStartDate(date);
		form.setEndDate(date);
		form.setStartTime(LocalTime.of(9, 0));
		form.setEndTime(LocalTime.of(10, 0));
		return form;
	}

	public ScheduleForm getForm(Long id, String loginId, boolean manager) {
		Schedule s = editable(id, loginId, manager);
		ScheduleForm form = new ScheduleForm();
		form.setId(s.getId());
		form.setType(s.getType());
		form.setTitle(s.getTitle());
		form.setDescription(s.getDescription());
		form.setAllDay(s.isAllDay());
		form.setStartDate(s.getStartAt().toLocalDate());
		form.setEndDate(s.getEndAt().toLocalDate());
		form.setStartTime(s.isAllDay() ? LocalTime.of(9, 0) : s.getStartAt().toLocalTime());
		form.setEndTime(s.isAllDay() ? LocalTime.of(10, 0) : s.getEndAt().toLocalTime());
		form.setParticipantIds(s.getParticipants().stream().map(Member::getId).toList());
		return form;
	}

	public List<FileInfo> files(Long id) {
		return fileService.list(FileOwner.SCHEDULE, id);
	}

	@Transactional
	public Long save(ScheduleForm form, String loginId, boolean manager) {
		return save(form, loginId, manager, List.of());
	}

	@Transactional
	public Long save(ScheduleForm form, String loginId, boolean manager, List<MultipartFile> uploads) {
		Member me = member(loginId);
		if (!allowedTypes(loginId, manager).contains(form.getType())) {
			throw new BusinessException(form.getType() == ScheduleType.COMPANY
				? "회사일정은 관리자만 등록할 수 있습니다."
				: "소속 부서가 없어 부서일정을 등록할 수 없습니다.");
		}

		LocalDateTime start;
		LocalDateTime end;
		if (form.isAllDay()) {
			start = form.getStartDate().atStartOfDay();
			end = form.getEndDate().atTime(END_OF_DAY);
		} else {
			if (form.getStartTime() == null || form.getEndTime() == null) {
				throw new BusinessException("시작/종료 시간을 입력하세요.");
			}
			start = form.getStartDate().atTime(form.getStartTime());
			end = form.getEndDate().atTime(form.getEndTime());
		}
		if (end.isBefore(start)) {
			throw new BusinessException("종료가 시작보다 빠를 수 없습니다.");
		}

		Schedule s = form.getId() == null ? new Schedule(me) : editable(form.getId(), loginId, manager);
		// 부서일정은 처음 등록한 부서를 유지 (등록자가 부서를 옮겨도 원래 부서 일정으로 남음)
		var department = form.getType() != ScheduleType.DEPARTMENT ? null
			: s.getType() == ScheduleType.DEPARTMENT && s.getDepartment() != null ? s.getDepartment()
			: me.getDepartment();
		s.update(form.getType(), department, form.getTitle().trim(),
			form.getDescription() == null || form.getDescription().isBlank() ? null : form.getDescription().trim(),
			start, end, form.isAllDay());
		s.changeParticipants(memberSelection.resolve(form.getParticipantIds(), s.getOwner(), s.getParticipants(),
			MAX_PARTICIPANTS, "참여자"));
		Long id = scheduleRepository.save(s).getId();
		fileService.sync(FileOwner.SCHEDULE, id, uploads, form.getDeleteFileIds());
		if (form.isNotifyParticipants()) {
			notifyParticipants(s, me, form.getId() == null ? "등록" : "수정");
		}
		return id;
	}

	private void notifyParticipants(Schedule s, Member by, String action) {
		List<Member> audience = new ArrayList<>(s.getParticipants());
		switch (s.getType()) {
			case COMPANY -> audience.addAll(memberRepository.findByEnabledTrue());
			case DEPARTMENT -> audience.addAll(memberRepository.findByDepartmentIdAndEnabledTrue(s.getDepartment().getId()));
			default -> {
			}
		}
		Map<Long, Member> targets = new LinkedHashMap<>();
		audience.forEach(m -> targets.put(m.getId(), m));
		targets.remove(by.getId());
		String message = by.getName() + " 님이 일정 '" + s.getTitle() + "'(" + toItem(s, by, false).displayPeriod() + ")을 " + action + "했습니다.";
		targets.values().forEach(m -> notificationService.send(m, "일정", message, "/schedule/" + s.getId()));
	}

	@Transactional
	public void delete(Long id, String loginId, boolean manager) {
		scheduleRepository.delete(editable(id, loginId, manager));
		fileService.deleteAll(FileOwner.SCHEDULE, id);
	}

	// ---------- 내부 ----------

	/** manager: 관리자 또는 일정관리자 (회사일정 등록, 남의 일정 수정·삭제) */
	private Schedule editable(Long id, String loginId, boolean manager) {
		Schedule s = find(id);
		if (!manager && !s.getOwner().getLoginId().equals(loginId)) {
			throw new AccessDeniedException("등록자만 수정할 수 있습니다.");
		}
		return s;
	}

	private static boolean canSee(Schedule s, Member me) {
		if (s.hasParticipant(me.getId())) {
			return true;
		}
		return switch (s.getType()) {
			case COMPANY -> true;
			case DEPARTMENT -> s.getOwner().getId().equals(me.getId())
				|| (s.getDepartment() != null && me.getDepartment() != null
					&& s.getDepartment().getId().equals(me.getDepartment().getId()));
			case PERSONAL -> s.getOwner().getId().equals(me.getId());
		};
	}

	private static ScheduleItem toItem(Schedule s, Member me, boolean manager) {
		return new ScheduleItem(s.getId(), s.getType(), s.getTitle(), s.getDescription(),
			s.getStartAt(), s.getEndAt(), s.isAllDay(), s.getOwner().getName(),
			s.getDepartment() == null ? null : s.getDepartment().getName(),
			manager || s.getOwner().getId().equals(me.getId()), MemberSelection.displayNames(s.getParticipants()));
	}

	private Schedule find(Long id) {
		return scheduleRepository.findDetail(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private Member member(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}
}
