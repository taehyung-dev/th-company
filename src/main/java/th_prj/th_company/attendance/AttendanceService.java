package th_prj.th_company.attendance;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.holiday.HolidayService;
import th_prj.th_company.leave.LeaveService;
import th_prj.th_company.leave.LeaveType;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.org.DepartmentRow;
import th_prj.th_company.org.DepartmentService;

/**
 * 출퇴근.
 * <ul>
 *   <li>하루는 오전 6시에 시작한다. 00:00~05:59 는 전날 근무일에 속한다.</li>
 *   <li>출근: 오전 6시 이후 그날 처음 로그인한 시각을 자동 기록 (00:00~05:59 로그인은 기록하지 않음).</li>
 *   <li>퇴근: [퇴근] 버튼을 누른 시각. 다시 누르면 마지막 시각으로 바뀐다. 다음 날 05:59 까지는 전날 근무로 퇴근 가능.</li>
 *   <li>지각/조퇴: 근무 시작(app.attendance.work-start, 기본 09:00)보다 늦게 출근하면 지각,
 *       근무 종료(app.attendance.work-end, 기본 18:00)보다 일찍 퇴근하면 조퇴. 주말에는 따지지 않는다.</li>
 *   <li>결근: 입사일(없으면 계정 생성일) 이후 지난 평일에 출근 기록이 없음. 공휴일과 승인된 휴가는 결근이 아니다.</li>
 *   <li>오전반차는 지각, 오후반차는 조퇴를 따지지 않는다. 반차인데 출근 기록이 없으면 결근이다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AttendanceService {

	public static final LocalTime DAY_START = LocalTime.of(6, 0);

	private final AttendanceRepository attendanceRepository;
	private final MemberRepository memberRepository;
	private final DepartmentService departmentService;
	private final HolidayService holidayService;
	private final LeaveService leaveService;
	private final Clock clock;
	private final LocalTime workStart;
	private final LocalTime workEnd;

	public AttendanceService(AttendanceRepository attendanceRepository, MemberRepository memberRepository,
			DepartmentService departmentService, HolidayService holidayService, LeaveService leaveService, Clock clock,
			@Value("${app.attendance.work-start:09:00}") String workStart,
			@Value("${app.attendance.work-end:18:00}") String workEnd) {
		this.attendanceRepository = attendanceRepository;
		this.memberRepository = memberRepository;
		this.departmentService = departmentService;
		this.holidayService = holidayService;
		this.leaveService = leaveService;
		this.clock = clock;
		this.workStart = LocalTime.parse(workStart);
		this.workEnd = LocalTime.parse(workEnd);
	}

	public LocalTime getWorkStart() { return workStart; }
	public LocalTime getWorkEnd() { return workEnd; }

	/** 지금 근무일 (오전 6시 전이면 전날) */
	public LocalDate currentWorkDate() {
		return workDateOf(LocalDateTime.now(clock));
	}

	// ---------- 월별 / 일별 조회 ----------

	/** 한 사람의 월별 근태. 이번 달보다 뒤의 달은 이번 달로 바꾼다 */
	public AttendanceMonth month(Long memberId, YearMonth month) {
		Member m = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
		LocalDate today = currentWorkDate();
		YearMonth current = YearMonth.from(today);
		YearMonth ym = month == null || month.isAfter(current) ? current : month;

		Map<LocalDate, Attendance> records = attendanceRepository
			.findByMemberIdAndWorkDateBetweenOrderByWorkDateAsc(m.getId(), ym.atDay(1), ym.atEndOfMonth()).stream()
			.collect(Collectors.toMap(Attendance::getWorkDate, a -> a));
		Map<LocalDate, String> holidays = holidayService.between(ym.atDay(1), ym.atEndOfMonth());
		Map<LocalDate, LeaveType> leaves = leaveService.approvedBetween(m.getId(), ym.atDay(1), ym.atEndOfMonth());
		List<AttendanceDay> days = new ArrayList<>();
		for (LocalDate d = ym.atDay(1); !d.isAfter(ym.atEndOfMonth()); d = d.plusDays(1)) {
			days.add(day(d, records.get(d), today, joinDate(m), holidays.get(d), leaves.get(d)));
		}
		return new AttendanceMonth(m.getId(), m.getName(),
			m.getDepartment() == null ? null : m.getDepartment().getName(),
			ym, ym.minusMonths(1), ym.equals(current) ? null : ym.plusMonths(1), days);
	}

	public AttendanceMonth myMonth(String loginId, YearMonth month) {
		return month(member(loginId).getId(), month);
	}

	/**
	 * 근태관리자 화면: 하루 동안의 재직자(부서가 지정된 사용자) 근태. 이름순이 아니라 부서 → 직책 → 직급 순.
	 *
	 * @param departmentId 지정하면 그 부서와 하위 부서만
	 */
	public AttendanceBoard board(LocalDate date, Long departmentId) {
		LocalDate today = currentWorkDate();
		LocalDate d = date == null || date.isAfter(today) ? today : date;
		Set<Long> deptIds = departmentId == null ? null : Set.copyOf(departmentService.withDescendants(departmentId));
		Map<Long, Attendance> records = attendanceRepository.findByWorkDate(d).stream()
			.collect(Collectors.toMap(a -> a.getMember().getId(), a -> a));
		String holiday = holidayService.between(d, d).get(d);
		Map<Long, LeaveType> leaves = leaveService.approvedOn(d);
		Map<Long, Integer> deptOrder = new HashMap<>();
		List<DepartmentRow> tree = departmentService.tree();
		for (int i = 0; i < tree.size(); i++) {
			deptOrder.put(tree.get(i).id(), i);
		}

		List<AttendanceBoard.Row> rows = memberRepository.findActiveWithDepartment().stream()
			.filter(m -> deptIds == null || deptIds.contains(m.getDepartment().getId()))
			.sorted(Comparator
				.comparingInt((Member m) -> deptOrder.getOrDefault(m.getDepartment().getId(), Integer.MAX_VALUE))
				.thenComparingInt(m -> m.getDuty() == null ? Integer.MAX_VALUE : m.getDuty().getSortOrder())
				.thenComparingInt(m -> m.getGrade() == null ? Integer.MAX_VALUE : m.getGrade().getSortOrder())
				.thenComparing(Member::getName))
			.map(m -> new AttendanceBoard.Row(m.getId(), m.getName(), m.getDepartment().getName(),
				m.getGrade() == null ? null : m.getGrade().getName(),
				day(d, records.get(m.getId()), today, joinDate(m), holiday, leaves.get(m.getId()))))
			.toList();
		return new AttendanceBoard(d, d.minusDays(1), d.equals(today) ? null : d.plusDays(1), rows);
	}

	private AttendanceDay day(LocalDate date, Attendance a, LocalDate today, LocalDate joinDate, String holiday, LeaveType leave) {
		boolean off = holiday != null || date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
		LeaveType workdayLeave = off ? null : leave;
		if (a == null) {
			AttendanceDay.State state;
			if (joinDate != null && date.isBefore(joinDate)) {
				state = AttendanceDay.State.NONE;
			} else if (off) {
				state = date.isAfter(today) ? AttendanceDay.State.NONE : AttendanceDay.State.OFF;
			} else if (workdayLeave != null && (!workdayLeave.isHalf() || date.isAfter(today))) {
				state = AttendanceDay.State.LEAVE;
			} else if (date.isAfter(today)) {
				state = AttendanceDay.State.NONE;
			} else {
				state = date.equals(today) ? AttendanceDay.State.NOT_YET : AttendanceDay.State.ABSENT;
			}
			return new AttendanceDay(date, null, null, state, false, false, null, null, holiday, workdayLeave);
		}
		AttendanceDay.State state = a.getCheckOutAt() != null ? AttendanceDay.State.WORKED
			: date.equals(today) ? AttendanceDay.State.WORKING : AttendanceDay.State.NO_CHECKOUT;
		boolean fullDay = workdayLeave != null && !workdayLeave.isHalf();
		boolean late = !fullDay && workdayLeave != LeaveType.AM_HALF
			&& a.getCheckInAt().toLocalTime().withSecond(0).withNano(0).isAfter(workStart);
		boolean early = !fullDay && workdayLeave != LeaveType.PM_HALF
			&& a.getCheckOutAt() != null && a.getCheckOutAt().isBefore(date.atTime(workEnd));
		return new AttendanceDay(date, a.getCheckInAt(), a.getCheckOutAt(), state, late, early,
			a.getCorrectedBy(), a.getCorrectionNote(), holiday, workdayLeave);
	}

	private static LocalDate joinDate(Member m) {
		if (m.getHireDate() != null) {
			return m.getHireDate();
		}
		return m.getCreatedAt() == null ? null : m.getCreatedAt().toLocalDate();
	}

	// ---------- 정정 (근태관리자) ----------

	public AttendanceEditForm editForm(Long memberId, LocalDate date) {
		AttendanceEditForm form = new AttendanceEditForm();
		form.setMemberId(memberId);
		form.setDate(date);
		attendanceRepository.findByMemberIdAndWorkDate(memberId, date).ifPresent(a -> {
			form.setCheckIn(a.getCheckInAt().toLocalTime().withSecond(0).withNano(0));
			if (a.getCheckOutAt() != null) {
				form.setCheckOut(a.getCheckOutAt().toLocalTime().withSecond(0).withNano(0));
			}
		});
		return form;
	}

	/** 정정 화면 제목용 "김도현 (개발1팀)" */
	public String memberLabel(Long memberId) {
		Member m = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
		return m.getDepartment() == null ? m.getName() : m.getName() + " (" + m.getDepartment().getName() + ")";
	}

	/**
	 * 출근을 비우면 그날 기록을 지운다. 퇴근이 출근보다 이르면 다음 날 새벽(06시 전) 퇴근으로 본다.
	 *
	 * @param managerLoginId 정정한 근태관리자 (기록에 이름이 남는다)
	 */
	@Transactional
	public void correct(AttendanceEditForm form, String managerLoginId) {
		Member target = memberRepository.findById(form.getMemberId())
			.orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다."));
		LocalDate date = form.getDate();
		LocalDateTime now = LocalDateTime.now(clock);
		if (date.isAfter(workDateOf(now))) {
			throw new BusinessException("앞으로의 날짜는 정정할 수 없습니다.");
		}
		Attendance existing = attendanceRepository.findByMemberIdAndWorkDate(target.getId(), date).orElse(null);

		if (form.getCheckIn() == null) {
			if (form.getCheckOut() != null) {
				throw new BusinessException("퇴근 시각만 넣을 수는 없습니다. 출근 시각을 입력하세요.");
			}
			if (existing != null) {
				attendanceRepository.delete(existing);
			}
			return;
		}
		if (form.getCheckIn().isBefore(DAY_START)) {
			throw new BusinessException("출근 시각은 오전 6시 이후여야 합니다. (06시 전은 전날 근무로 봅니다)");
		}
		LocalDateTime in = date.atTime(form.getCheckIn());
		LocalDateTime out = null;
		if (form.getCheckOut() != null) {
			out = date.atTime(form.getCheckOut());
			if (!out.isAfter(in)) {
				if (!form.getCheckOut().isBefore(DAY_START)) {
					throw new BusinessException("퇴근 시각이 출근 시각보다 이릅니다. 다음 날 새벽 퇴근은 06시 전까지만 됩니다.");
				}
				out = out.plusDays(1);
			}
		}
		if (in.isAfter(now) || (out != null && out.isAfter(now))) {
			throw new BusinessException("아직 지나지 않은 시각으로는 정정할 수 없습니다.");
		}
		String by = member(managerLoginId).getName();
		Attendance a = existing != null ? existing : attendanceRepository.save(new Attendance(target, date, in));
		a.correct(in, out, by, form.getNote().trim(), now);
	}

	/** 시각이 속한 근무일 (오전 6시 기준) */
	public static LocalDate workDateOf(LocalDateTime at) {
		return at.toLocalTime().isBefore(DAY_START) ? at.toLocalDate().minusDays(1) : at.toLocalDate();
	}

	/**
	 * 로그인 시 호출. 오전 6시 이후이고 오늘 근무일 기록이 없으면 출근으로 기록한다.
	 *
	 * @return 이번 호출로 출근이 기록되었으면 true
	 */
	@Transactional
	public boolean recordLogin(String loginId) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (now.toLocalTime().isBefore(DAY_START)) {
			return false;
		}
		Member me = member(loginId);
		LocalDate today = now.toLocalDate();
		if (attendanceRepository.existsByMemberIdAndWorkDate(me.getId(), today)) {
			return false;
		}
		attendanceRepository.save(new Attendance(me, today, now));
		return true;
	}

	@Transactional
	public void checkOut(String loginId) {
		LocalDateTime now = LocalDateTime.now(clock);
		Member me = member(loginId);
		Attendance a = attendanceRepository.findByMemberIdAndWorkDate(me.getId(), workDateOf(now))
			.orElseThrow(() -> new BusinessException("출근 기록이 없어 퇴근할 수 없습니다. 오전 6시 이후 다시 로그인하면 출근이 기록됩니다."));
		a.checkOut(now);
	}

	public AttendanceView today(String loginId) {
		LocalDate workDate = workDateOf(LocalDateTime.now(clock));
		return attendanceRepository.findByMemberIdAndWorkDate(member(loginId).getId(), workDate)
			.map(a -> new AttendanceView(workDate, a.getCheckInAt(), a.getCheckOutAt()))
			.orElse(new AttendanceView(workDate, null, null));
	}

	private Member member(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}
}
