package th_prj.th_company.leave;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.approval.ApprovalDecidedEvent;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.approval.ApprovalTemplateInitializer;
import th_prj.th_company.approval.DocStatus;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.holiday.HolidayService;
import th_prj.th_company.leave.LeaveViews.MemberRow;
import th_prj.th_company.leave.LeaveViews.Row;
import th_prj.th_company.leave.LeaveViews.Summary;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.notification.NotificationService;

/**
 * 휴가. 규칙:
 * <ul>
 *   <li>신청하면 휴가신청서가 전자결재로 올라가고, 최종 승인되면 휴가가 확정된다. 반려되면 반려.</li>
 *   <li>일수는 주말과 공휴일을 뺀다. 반차는 0.5일. 해를 넘기는 휴가는 해별로 나누어 신청한다.</li>
 *   <li>연차·반차는 남은 연차 안에서만 신청할 수 있다 (결재 중인 일수도 미리 뺀다). 병가·경조사·공가는 차감하지 않는다.</li>
 *   <li>연차 일수: 입사 1년 미만은 다 채운 달마다 1일(최대 11일), 1년 이상은 15일에 2년마다 1일 가산(최대 25일).
 *       입사일이 없으면 15일. 해가 바뀌면 다시 계산하고 이월하지 않는다. 근태관리자가 직접 정할 수 있다.</li>
 *   <li>취소: 결재 중이면 본인. 승인된 휴가는 시작일 전까지 본인, 그 뒤로는 근태관리자.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LeaveService {

	private static final Set<LeaveStatus> ACTIVE = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

	private final LeaveRequestRepository requestRepository;
	private final LeaveBalanceRepository balanceRepository;
	private final MemberRepository memberRepository;
	private final ApprovalService approvalService;
	private final HolidayService holidayService;
	private final NotificationService notificationService;
	private final Clock clock;

	public LeaveService(LeaveRequestRepository requestRepository, LeaveBalanceRepository balanceRepository,
			MemberRepository memberRepository, ApprovalService approvalService, HolidayService holidayService,
			NotificationService notificationService, Clock clock) {
		this.requestRepository = requestRepository;
		this.balanceRepository = balanceRepository;
		this.memberRepository = memberRepository;
		this.approvalService = approvalService;
		this.holidayService = holidayService;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	// ---------- 조회 ----------

	public int currentYear() {
		return today().getYear();
	}

	public Summary summary(String loginId, int year) {
		Member me = member(loginId);
		List<LeaveRequest> mine = requestRepository.findByMember(me.getId(), yearStart(year), yearEnd(year));
		return summary(me, year, balanceRepository.findByMemberIdAndBalanceYear(me.getId(), year).orElse(null), mine);
	}

	public List<Row> myRequests(String loginId, int year) {
		Member me = member(loginId);
		return requestRepository.findByMember(me.getId(), yearStart(year), yearEnd(year)).stream()
			.map(r -> row(r, me, canCancelOwn(r)))
			.toList();
	}

	/** 근태관리자: 재직자별 연차 현황 */
	public List<MemberRow> memberRows(int year) {
		Map<Long, List<LeaveRequest>> byMember = requestRepository.findAllBetween(yearStart(year), yearEnd(year)).stream()
			.collect(Collectors.groupingBy(r -> r.getMember().getId()));
		Map<Long, LeaveBalance> balances = balanceRepository.findByBalanceYear(year).stream()
			.collect(Collectors.toMap(b -> b.getMember().getId(), b -> b));
		return memberRepository.findActiveWithDepartment().stream()
			.sorted(Comparator.comparing((Member m) -> m.getDepartment().getName()).thenComparing(Member::getName))
			.map(m -> new MemberRow(m.getId(), m.getName(), m.getDepartment().getName(), m.getHireDate(),
				summary(m, year, balances.get(m.getId()), byMember.getOrDefault(m.getId(), List.of()))))
			.toList();
	}

	/** 근태관리자: 그해 전체 휴가 신청 */
	public List<Row> allRequests(int year) {
		return requestRepository.findAllBetween(yearStart(year), yearEnd(year)).stream()
			.map(r -> row(r, r.getMember(), r.getStatus() == LeaveStatus.APPROVED))
			.toList();
	}

	private Summary summary(Member m, int year, LeaveBalance balance, List<LeaveRequest> requests) {
		double used = 0;
		double pending = 0;
		for (LeaveRequest r : requests) {
			if (r.getType().isDeducted() && r.getStatus() == LeaveStatus.APPROVED) {
				used += r.getDays();
			} else if (r.getType().isDeducted() && r.getStatus() == LeaveStatus.PENDING) {
				pending += r.getDays();
			}
		}
		double granted = balance != null ? balance.getGrantedDays() : autoDays(m.getHireDate(), year, today());
		return new Summary(year, granted, used, pending, balance != null);
	}

	/** 입사일로 계산한 그해 연차 */
	public static double autoDays(LocalDate hireDate, int year, LocalDate today) {
		if (hireDate == null) {
			return 15;
		}
		LocalDate yearStart = LocalDate.of(year, 1, 1);
		long serviceYears = hireDate.isAfter(yearStart) ? 0 : ChronoUnit.YEARS.between(hireDate, yearStart);
		if (serviceYears >= 1) {
			return Math.min(15 + (serviceYears - 1) / 2, 25);
		}
		LocalDate until = yearEnd(year).isBefore(today) ? yearEnd(year) : today;
		return until.isBefore(hireDate) ? 0 : Math.min(ChronoUnit.MONTHS.between(hireDate, until), 11);
	}

	// ---------- 신청 ----------

	public LeaveForm newForm() {
		LeaveForm form = new LeaveForm();
		form.setStartDate(today());
		form.setEndDate(today());
		return form;
	}

	@Transactional
	public Long request(LeaveForm form, String loginId) {
		Member me = member(loginId);
		LocalDate start = form.getStartDate();
		LocalDate end = form.getType().isHalf() ? start : form.getEndDate();
		if (end.isBefore(start)) {
			throw new BusinessException("종료일이 시작일보다 빠를 수 없습니다.");
		}
		if (start.getYear() != end.getYear()) {
			throw new BusinessException("해를 넘기는 휴가는 해별로 나누어 신청하세요.");
		}
		double days = workingDays(start, end) * (form.getType().isHalf() ? 0.5 : 1);
		if (days == 0) {
			throw new BusinessException("주말과 공휴일만 있는 기간입니다. 날짜를 확인하세요.");
		}
		List<LeaveRequest> overlapping = requestRepository.findOverlapping(me.getId(), start, end, ACTIVE);
		// 같은 날 오전반차와 오후반차는 함께 쓸 수 있다
		boolean halves = form.getType().isHalf() && overlapping.stream()
			.allMatch(r -> r.getType().isHalf() && r.getType() != form.getType());
		if (!overlapping.isEmpty() && !halves) {
			throw new BusinessException("같은 기간에 이미 신청한 휴가가 있습니다.");
		}
		if (form.getType().isDeducted()) {
			Summary s = summary(loginId, start.getYear());
			if (days > s.remaining()) {
				throw new BusinessException("남은 연차(" + s.displayRemaining() + "일)보다 많이 신청할 수 없습니다.");
			}
		}

		String reason = form.getReason() == null || form.getReason().isBlank() ? null : form.getReason().trim();
		String period = LeaveViews.period(start, end);
		String title = "휴가 신청 - " + form.getType().getLabel() + " (" + period + ")";
		String content = "휴가 종류: " + form.getType().getLabel() + "\n기간: " + period + "\n일수: " + LeaveViews.days(days) + "일"
			+ "\n사유: " + (reason == null ? "-" : reason);
		Long documentId = approvalService.submitLinked(ApprovalTemplateInitializer.LEAVE_CODE, loginId, title, content,
			form.getApproverIds());
		LeaveRequest request = new LeaveRequest(me, form.getType(), start, end, days, reason);
		request.linkDocument(documentId);
		return requestRepository.save(request).getId();
	}

	/** 주말과 공휴일을 뺀 날 수 */
	private long workingDays(LocalDate start, LocalDate end) {
		Set<LocalDate> holidays = holidayService.between(start, end).keySet();
		return start.datesUntil(end.plusDays(1))
			.filter(d -> !HolidayService.isWeekend(d) && !holidays.contains(d))
			.count();
	}

	/** 휴가신청서의 결재가 끝나면 휴가에 반영한다 (같은 트랜잭션) */
	@EventListener
	@Transactional
	public void onApprovalDecided(ApprovalDecidedEvent event) {
		requestRepository.findByDocumentId(event.documentId()).ifPresent(r -> {
			if (r.getStatus() == LeaveStatus.PENDING) {
				r.changeStatus(event.status() == DocStatus.APPROVED ? LeaveStatus.APPROVED : LeaveStatus.REJECTED);
			}
		});
	}

	// ---------- 취소 ----------

	@Transactional
	public void cancel(Long id, String loginId) {
		LeaveRequest r = find(id);
		if (!r.getMember().getLoginId().equals(loginId)) {
			throw new AccessDeniedException("신청한 사람만 취소할 수 있습니다.");
		}
		if (!canCancelOwn(r)) {
			throw new BusinessException(r.getStatus() == LeaveStatus.APPROVED
				? "이미 시작한 휴가는 근태관리자에게 취소를 요청하세요."
				: "취소할 수 없는 상태입니다.");
		}
		doCancel(r);
	}

	/** 근태관리자: 승인된 휴가를 취소한다 */
	@Transactional
	public void cancelByManager(Long id, String loginId) {
		LeaveRequest r = find(id);
		if (r.getStatus() != LeaveStatus.APPROVED) {
			throw new BusinessException("승인된 휴가만 취소할 수 있습니다.");
		}
		doCancel(r);
		if (!r.getMember().getLoginId().equals(loginId)) {
			notificationService.send(r.getMember(), "휴가",
				r.getType().getLabel() + " (" + LeaveViews.period(r.getStartDate(), r.getEndDate()) + ")이 근태관리자에 의해 취소되었습니다.",
				"/attendance/leave?year=" + r.getStartDate().getYear());
		}
	}

	private void doCancel(LeaveRequest r) {
		if (r.getStatus() == LeaveStatus.PENDING) {
			approvalService.cancelLinked(r.getDocumentId());
		}
		r.changeStatus(LeaveStatus.CANCELED);
	}

	private boolean canCancelOwn(LeaveRequest r) {
		return r.getStatus() == LeaveStatus.PENDING
			|| (r.getStatus() == LeaveStatus.APPROVED && r.getStartDate().isAfter(today()));
	}

	// ---------- 연차 일수 지정 ----------

	/** days 가 null 이면 직접 정한 값을 지우고 입사일 기준으로 되돌린다 */
	@Transactional
	public void setGranted(Long memberId, int year, Double days) {
		if (days != null && (days < 0 || days > 100 || days * 2 != Math.rint(days * 2))) {
			throw new BusinessException("연차 일수는 0~100 사이에서 0.5일 단위로 입력하세요.");
		}
		Member m = memberRepository.findById(memberId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		var existing = balanceRepository.findByMemberIdAndBalanceYear(memberId, year);
		if (days == null) {
			existing.ifPresent(balanceRepository::delete);
		} else if (existing.isPresent()) {
			existing.get().changeGranted(days);
		} else {
			balanceRepository.save(new LeaveBalance(m, year, days));
		}
	}

	// ---------- 내부 ----------

	private static Row row(LeaveRequest r, Member m, boolean canCancel) {
		return new Row(r.getId(), m.getId(), m.getName(), m.getDepartment() == null ? null : m.getDepartment().getName(),
			r.getType(), r.getStartDate(), r.getEndDate(), r.getDays(), r.getReason(), r.getStatus(),
			r.getDocumentId(), canCancel);
	}

	private LeaveRequest find(Long id) {
		return requestRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static LocalDate yearStart(int year) {
		return LocalDate.of(year, 1, 1);
	}

	private static LocalDate yearEnd(int year) {
		return LocalDate.of(year, 12, 31);
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private Member member(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}

	/** 그날 승인된 휴가 (회원 id → 종류). 같은 날 오전·오후 반차면 연차로 본다 */
	public Map<Long, LeaveType> approvedOn(LocalDate date) {
		Map<Long, LeaveType> result = new HashMap<>();
		for (LeaveRequest r : requestRepository.findOn(date, LeaveStatus.APPROVED)) {
			result.merge(r.getMember().getId(), r.getType(), (a, b) -> LeaveType.ANNUAL);
		}
		return result;
	}

	/** 한 사람의 기간 안 승인된 휴가 (날짜 → 종류) */
	public Map<LocalDate, LeaveType> approvedBetween(Long memberId, LocalDate from, LocalDate to) {
		Map<LocalDate, LeaveType> result = new HashMap<>();
		for (LeaveRequest r : requestRepository.findByMember(memberId, from, to)) {
			if (r.getStatus() == LeaveStatus.APPROVED) {
				r.getStartDate().datesUntil(r.getEndDate().plusDays(1))
					.forEach(d -> result.merge(d, r.getType(), (a, b) -> LeaveType.ANNUAL));
			}
		}
		return result;
	}
}
