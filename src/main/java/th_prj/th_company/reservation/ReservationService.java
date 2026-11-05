package th_prj.th_company.reservation;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import th_prj.th_company.notification.NotificationService;
import th_prj.th_company.reservation.ReservationViews.Block;
import th_prj.th_company.reservation.ReservationViews.DayBoard;
import th_prj.th_company.reservation.ReservationViews.Item;
import th_prj.th_company.reservation.ReservationViews.Row;
import th_prj.th_company.reservation.ReservationViews.Saved;
import th_prj.th_company.reservation.ReservationViews.Taken;

/**
 * 회의실·자원 예약. 규칙:
 * <ul>
 *   <li>같은 자원은 시간이 겹치게 예약할 수 없다 (끝나는 시각에 바로 다음 예약은 가능)</li>
 *   <li>10분 단위, 지난 시간에는 새로 예약할 수 없다</li>
 *   <li>회의실은 하루 안에서만 예약한다. 자원은 시작일과 종료일을 따로 정해 최대 90일까지 예약할 수 있다</li>
 *   <li>회의실은 새로 예약할 때 반복할 수 있다 (최대 1년). 날짜마다 예약이 하나씩 만들어져 따로 고치거나 취소할 수 있다</li>
 *   <li>수정·취소: 예약자 본인, 또는 관리자와 그 구분의 관리 권한자(회의실관리자, 자원관리자). 끝난 예약은 관리자만</li>
 *   <li>사용 안 함 자원은 새 예약을 받지 않는다</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ReservationService {

	/** 타임라인 기본 표시 시간 (이보다 이르거나 늦은 예약이 있으면 넓힌다) */
	static final int DAY_START_HOUR = 7;
	static final int DAY_END_HOUR = 21;
	static final int SLOT_MINUTES = 10;
	/** 지난 예약은 이 기간만 내 예약에 보인다 */
	private static final int PAST_DAYS = 30;
	private static final int MAX_RESOURCE_DAYS = 90;

	private final ReservationRepository reservationRepository;
	private final ResourceRepository resourceRepository;
	private final MemberRepository memberRepository;
	private final ReservationSeriesRepository seriesRepository;
	private final HolidayService holidayService;
	private final FileAttachmentService fileService;
	private final NotificationService notificationService;
	private final Clock clock;

	public ReservationService(ReservationRepository reservationRepository, ResourceRepository resourceRepository,
			MemberRepository memberRepository, ReservationSeriesRepository seriesRepository, HolidayService holidayService,
			FileAttachmentService fileService, NotificationService notificationService, Clock clock) {
		this.reservationRepository = reservationRepository;
		this.resourceRepository = resourceRepository;
		this.memberRepository = memberRepository;
		this.seriesRepository = seriesRepository;
		this.holidayService = holidayService;
		this.fileService = fileService;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	// ---------- 조회 ----------

	/** 일간 현황 (회의실 또는 자원). type 이 null 이거나 kind 의 종류가 아니면 kind 의 전체 */
	public DayBoard day(ReservationKind kind, LocalDate date, ResourceType type, String loginId) {
		Member me = member(loginId);
		ResourceType filter = type != null && kind.includes(type) ? type : null;
		LocalDateTime dayStart = date.atStartOfDay();
		LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
		List<Reservation> reservations = reservationRepository.findOverlapping(dayStart, dayEnd).stream()
			.filter(r -> kind.includes(r.getResource().getType()))
			.toList();

		int startHour = DAY_START_HOUR;
		int endHour = DAY_END_HOUR;
		// 그날 시작하거나 끝나는 시각만 표시 범위를 넓힌다 (며칠에 걸친 예약이 하루 전체로 넓히지 않게)
		for (Reservation r : reservations) {
			if (!r.getStartAt().isBefore(dayStart)) {
				startHour = Math.min(startHour, r.getStartAt().getHour());
				endHour = Math.max(endHour, r.getStartAt().getHour() + 1);
			}
			if (!r.getEndAt().isAfter(dayEnd)) {
				LocalDateTime e = r.getEndAt();
				int h = e.equals(dayEnd) ? 24 : e.getHour() + (e.getMinute() > 0 ? 1 : 0);
				endHour = Math.max(endHour, h);
				startHour = Math.min(startHour, Math.max(h - 1, 0));
			}
		}
		LocalDateTime from = dayStart.plusHours(startHour);
		LocalDateTime to = dayStart.plusHours(endHour);
		double total = (endHour - startHour) * 60.0;

		// 사용 중인 자원 + 그날 예약이 있는 사용 안 함 자원
		Map<Long, List<Block>> blocks = new LinkedHashMap<>();
		for (Reservation r : reservations) {
			LocalDateTime s = r.getStartAt().isBefore(from) ? from : r.getStartAt();
			LocalDateTime e = r.getEndAt().isAfter(to) ? to : r.getEndAt();
			double left = Duration.between(from, s).toMinutes() / total * 100;
			double width = Duration.between(s, e).toMinutes() / total * 100;
			Member owner = r.getOwner();
			blocks.computeIfAbsent(r.getResource().getId(), k -> new ArrayList<>())
				.add(new Block(r.getId(), r.getTitle(), owner.getName(), deptName(owner),
					ReservationViews.period(r.getStartAt(), r.getEndAt()), left, width,
					owner.getId().equals(me.getId())));
		}
		List<Row> rows = new ArrayList<>();
		for (Resource res : resourceRepository.findAllOrdered()) {
			if (!kind.includes(res.getType()) || (filter != null && res.getType() != filter)) {
				continue;
			}
			if (!res.isEnabled() && !blocks.containsKey(res.getId())) {
				continue;
			}
			rows.add(new Row(res.getId(), res.getType(), res.getName(), res.getLocation(), res.getCapacity(),
				res.getDescription(), res.isEnabled(), blocks.getOrDefault(res.getId(), List.of())));
		}

		List<Integer> hours = new ArrayList<>();
		for (int h = startHour; h < endHour; h++) {
			hours.add(h);
		}
		LocalDateTime now = now();
		boolean today = now.toLocalDate().equals(date);
		Double nowPct = null;
		if (today && !now.isBefore(from) && now.isBefore(dayStart.plusHours(endHour))) {
			nowPct = Duration.between(from, now).toMinutes() / total * 100;
		}
		return new DayBoard(kind, date, filter, startHour, endHour, hours, rows, nowPct, today);
	}

	/** managed: 관리 권한이 있는 예약 구분 (그 구분의 남의 예약도 바꿀 수 있다) */
	public Item get(Long id, String loginId, Set<ReservationKind> managed) {
		Reservation r = find(id);
		return toItem(r, member(loginId), managed.contains(ReservationKind.of(r.getResource())));
	}

	/** 내 예약: 다가오는 것 */
	public List<Item> myUpcoming(ReservationKind kind, String loginId) {
		Member me = member(loginId);
		return reservationRepository.findUpcoming(me.getId(), now()).stream()
			.filter(r -> kind.includes(r.getResource().getType()))
			.map(r -> toItem(r, me, false))
			.toList();
	}

	/** 내 예약: 최근 30일 동안 끝난 것 */
	public List<Item> myPast(ReservationKind kind, String loginId) {
		Member me = member(loginId);
		LocalDateTime now = now();
		return reservationRepository.findPast(me.getId(), now, now.minusDays(PAST_DAYS)).stream()
			.filter(r -> kind.includes(r.getResource().getType()))
			.map(r -> toItem(r, me, false))
			.toList();
	}

	/** 예약 화면의 자원 선택 목록 (kind 의 사용 중인 자원). 수정 중인 예약의 자원이 사용 안 함이어도 넣는다 */
	public List<Resource> selectableResources(ReservationKind kind, Long currentResourceId) {
		List<Resource> list = new ArrayList<>(resourceRepository.findEnabled().stream()
			.filter(r -> kind.includes(r.getType()))
			.toList());
		if (currentResourceId != null && list.stream().noneMatch(r -> r.getId().equals(currentResourceId))) {
			resourceRepository.findById(currentResourceId)
				.filter(r -> kind.includes(r.getType()))
				.ifPresent(list::add);
		}
		return list;
	}

	/** 상세 화면의 첨부파일: 그 예약의 파일과 반복 예약 전체의 파일 */
	public List<FileInfo> files(Item item) {
		List<FileInfo> files = new ArrayList<>(fileService.list(FileOwner.RESERVATION, item.id()));
		if (item.seriesId() != null) {
			files.addAll(fileService.list(FileOwner.RESERVATION_SERIES, item.seriesId()));
		}
		return files;
	}

	/** 수정 화면의 첨부파일: 그 예약의 파일만 */
	public List<FileInfo> ownFiles(Long id) {
		return fileService.list(FileOwner.RESERVATION, id);
	}

	/** 예약 화면: 고른 자원의 그 기간 예약 (겹치지 않게 고르도록) */
	public List<Taken> taken(Long resourceId, LocalDate date, LocalDate endDate, Long editingId, String loginId) {
		if (resourceId == null || date == null) {
			return List.of();
		}
		Member me = member(loginId);
		LocalDate last = endDate == null || endDate.isBefore(date) ? date : endDate;
		return reservationRepository.findConflicts(resourceId, date.atStartOfDay(), last.plusDays(1).atStartOfDay(),
				editingId == null ? 0L : editingId).stream()
			.map(r -> new Taken(ReservationViews.period(r.getStartAt(), r.getEndAt()), r.getTitle(),
				r.getOwner().getName(), r.getOwner().getId().equals(me.getId())))
			.toList();
	}

	// ---------- 예약/수정/취소 ----------

	/**
	 * 새 예약 화면 기본값. start 를 주면 그 시각부터 1시간,
	 * 없으면 오늘은 다음 30분 단위 시각, 다른 날은 09:00 부터 1시간.
	 */
	public ReservationForm newForm(ReservationKind kind, Long resourceId, LocalDate date, LocalTime start) {
		LocalDateTime now = now();
		ReservationForm form = new ReservationForm();
		form.setKind(kind);
		// 다른 구분의 자원 번호가 넘어오면 무시
		form.setResourceId(resourceId == null ? null : resourceRepository.findById(resourceId)
			.filter(r -> kind.includes(r.getType())).map(Resource::getId).orElse(null));
		form.setDate(date == null ? now.toLocalDate() : date);
		form.setEndDate(form.getDate());
		LocalTime s = start;
		if (s == null) {
			s = LocalTime.of(9, 0);
			if (form.getDate().equals(now.toLocalDate()) && now.toLocalTime().isAfter(s)) {
				int minutes = now.getHour() * 60 + now.getMinute();
				int next = (minutes / 30 + 1) * 30;
				s = next >= 23 * 60 ? LocalTime.of(23, 0) : LocalTime.of(next / 60, next % 60);
			}
		}
		form.setStartTime(s);
		form.setEndTime(s.isAfter(LocalTime.of(22, 50)) ? LocalTime.of(23, 50) : s.plusHours(1));
		return form;
	}

	public ReservationForm getForm(Long id, String loginId, Set<ReservationKind> managed) {
		Reservation r = editable(id, loginId, managed);
		ReservationForm form = new ReservationForm();
		form.setId(r.getId());
		form.setKind(ReservationKind.of(r.getResource()));
		form.setResourceId(r.getResource().getId());
		form.setTitle(r.getTitle());
		form.setDate(r.getStartAt().toLocalDate());
		form.setEndDate(r.getEndAt().toLocalDate());
		form.setStartTime(r.getStartAt().toLocalTime());
		form.setEndTime(r.getEndAt().toLocalTime());
		form.setMemo(r.getMemo());
		return form;
	}

	@Transactional
	public Saved save(ReservationForm form, String loginId, Set<ReservationKind> managed, List<MultipartFile> uploads) {
		Member me = member(loginId);
		Reservation r = form.getId() == null ? new Reservation(me) : editable(form.getId(), loginId, managed);
		// 회의실 예약은 회의실끼리, 자원 예약은 자원끼리만 바꿀 수 있다
		if (r.getId() != null && ReservationKind.of(r.getResource()) != form.getKind()) {
			throw new BusinessException("예약 구분을 바꿀 수 없습니다.");
		}

		LocalTime startTime = form.getStartTime().truncatedTo(ChronoUnit.MINUTES);
		LocalTime endTime = form.getEndTime().truncatedTo(ChronoUnit.MINUTES);
		if (startTime.getMinute() % SLOT_MINUTES != 0 || endTime.getMinute() % SLOT_MINUTES != 0) {
			throw new BusinessException("시간은 " + SLOT_MINUTES + "분 단위로 입력하세요.");
		}
		boolean multiDayAllowed = form.getKind() == ReservationKind.RESOURCE && form.getEndDate() != null;
		LocalDate endDate = multiDayAllowed ? form.getEndDate() : form.getDate();
		LocalDateTime start = form.getDate().atTime(startTime);
		LocalDateTime end = endDate.atTime(endTime);
		if (!end.isAfter(start)) {
			throw new BusinessException(endDate.equals(form.getDate()) ? "종료 시간은 시작 시간보다 늦어야 합니다."
				: "종료일이 시작일보다 빠를 수 없습니다.");
		}
		if (ChronoUnit.DAYS.between(form.getDate(), endDate) >= MAX_RESOURCE_DAYS) {
			throw new BusinessException("한 번에 " + MAX_RESOURCE_DAYS + "일까지 예약할 수 있습니다.");
		}

		// 지난 시간에는 새로 잡을 수 없다. 이미 시작한 예약은 시작 시각을 그대로 두고 종료만 바꿀 수 있다.
		LocalDateTime now = now();
		boolean keepsStart = r.getId() != null && start.equals(r.getStartAt());
		if (!keepsStart && start.isBefore(now.truncatedTo(ChronoUnit.MINUTES).minusMinutes(SLOT_MINUTES - 1))) {
			throw new BusinessException("지난 시간에는 예약할 수 없습니다.");
		}
		if (!end.isAfter(now)) {
			throw new BusinessException("이미 지난 시간입니다. 종료 시간을 확인하세요.");
		}

		// 자원 행을 잠근 뒤 겹침 검사 → 같은 자원에 동시에 들어온 예약은 차례로 처리된다
		Resource resource = resourceRepository.findForUpdate(form.getResourceId())
			.filter(res -> form.getKind().includes(res.getType()))
			.orElseThrow(() -> new BusinessException(form.getKind().getLabel() + "을(를) 찾을 수 없습니다."));
		boolean sameResource = r.getId() != null && r.getResource().getId().equals(resource.getId());
		if (!resource.isEnabled() && !sameResource) {
			throw new BusinessException("'" + resource.getName() + "'은(는) 지금 예약을 받지 않습니다.");
		}
		String title = form.getTitle().trim();
		String memo = form.getMemo() == null || form.getMemo().isBlank() ? null : form.getMemo().trim();
		if (r.getId() == null && form.getKind() == ReservationKind.ROOM && form.getRepeat() != null
				&& form.getRepeat() != RepeatRule.NONE) {
			return saveSeries(form, me, resource, title, memo, startTime, endTime, uploads);
		}
		List<Reservation> conflicts = reservationRepository.findConflicts(resource.getId(), start, end,
			r.getId() == null ? 0L : r.getId());
		if (!conflicts.isEmpty()) {
			Reservation c = conflicts.get(0);
			throw new BusinessException("'" + resource.getName() + "'은(는) "
				+ ReservationViews.period(c.getStartAt(), c.getEndAt()) + " " + c.getOwner().getName()
				+ " 님의 예약(" + c.getTitle() + ")과 시간이 겹칩니다.");
		}

		r.update(resource, title, start, end, memo);
		Long id = reservationRepository.save(r).getId();
		fileService.sync(FileOwner.RESERVATION, id, uploads, form.getDeleteFileIds());
		if (!r.getOwner().getId().equals(me.getId())) {
			notifyOwner(r, me, "수정");
		}
		return new Saved(id, 1, 0);
	}

	/** 반복 예약: 날짜마다 예약을 만든다. 첨부파일은 묶음에 달아 모든 날짜에서 보이게 한다 */
	private Saved saveSeries(ReservationForm form, Member me, Resource resource, String title, String memo,
			LocalTime startTime, LocalTime endTime, List<MultipartFile> uploads) {
		LocalDate first = form.getDate();
		LocalDate until = form.getRepeatUntil();
		if (until == null) {
			throw new BusinessException("반복 종료일을 입력하세요.");
		}
		if (until.isBefore(first)) {
			throw new BusinessException("반복 종료일이 예약 날짜보다 빠를 수 없습니다.");
		}
		if (until.isAfter(first.plusYears(1))) {
			throw new BusinessException("반복 예약은 1년까지 할 수 있습니다.");
		}
		Set<LocalDate> holidays = form.isSkipHolidays() ? holidayService.between(first, until).keySet() : Set.of();
		List<LocalDate> dates = new ArrayList<>();
		List<String> conflicts = new ArrayList<>();
		for (LocalDate d : form.getRepeat().dates(first, until)) {
			if (holidays.contains(d)) {
				continue;
			}
			if (reservationRepository.findConflicts(resource.getId(), d.atTime(startTime), d.atTime(endTime), 0L).isEmpty()) {
				dates.add(d);
			} else {
				conflicts.add(d.getMonthValue() + "/" + d.getDayOfMonth());
			}
		}
		if (!conflicts.isEmpty() && !form.isSkipConflicts()) {
			String sample = String.join(", ", conflicts.subList(0, Math.min(conflicts.size(), 5)));
			throw new BusinessException("이미 예약된 날이 " + conflicts.size() + "일 있습니다 (" + sample
				+ (conflicts.size() > 5 ? " 등" : "") + "). 날짜를 바꾸거나 [겹치는 날 건너뛰기]를 선택하세요.");
		}
		if (dates.isEmpty()) {
			throw new BusinessException("예약할 수 있는 날이 없습니다.");
		}
		ReservationSeries series = seriesRepository.save(new ReservationSeries(form.getRepeat(), until));
		Long firstId = null;
		for (LocalDate d : dates) {
			Reservation r = new Reservation(me);
			r.update(resource, title, d.atTime(startTime), d.atTime(endTime), memo);
			r.joinSeries(series);
			Long id = reservationRepository.save(r).getId();
			firstId = firstId == null ? id : firstId;
		}
		fileService.sync(FileOwner.RESERVATION_SERIES, series.getId(), uploads, List.of());
		return new Saved(firstId, dates.size(), conflicts.size());
	}

	@Transactional
	public void cancel(Long id, String loginId, Set<ReservationKind> managed) {
		Reservation r = editable(id, loginId, managed);
		remove(List.of(r), r.getSeries(), member(loginId));
	}

	/** 반복 예약에서 이 예약과 그 뒤의 예약을 모두 취소한다 */
	@Transactional
	public int cancelFollowing(Long id, String loginId, Set<ReservationKind> managed) {
		Reservation r = editable(id, loginId, managed);
		if (r.getSeries() == null) {
			throw new BusinessException("반복 예약이 아닙니다.");
		}
		List<Reservation> targets = reservationRepository
			.findBySeriesIdAndStartAtGreaterThanEqual(r.getSeries().getId(), r.getStartAt());
		remove(targets, r.getSeries(), member(loginId));
		return targets.size();
	}

	private void remove(List<Reservation> targets, ReservationSeries series, Member me) {
		Reservation first = targets.get(0);
		if (!first.getOwner().getId().equals(me.getId())) {
			notifyOwner(first, me, "취소");
		}
		for (Reservation r : targets) {
			fileService.deleteAll(FileOwner.RESERVATION, r.getId());
		}
		reservationRepository.deleteAll(targets);
		reservationRepository.flush();
		if (series != null && reservationRepository.countBySeriesId(series.getId()) == 0) {
			fileService.deleteAll(FileOwner.RESERVATION_SERIES, series.getId());
			seriesRepository.delete(series);
		}
	}

	/** 관리자가 남의 예약을 바꾸면 예약자에게 알린다 */
	private void notifyOwner(Reservation r, Member by, String action) {
		ReservationKind kind = ReservationKind.of(r.getResource());
		notificationService.send(r.getOwner(), "예약", by.getName() + " 님이 '" + r.getTitle() + "' " + kind.getLabel() + " 예약("
			+ ReservationViews.displayDate(r.getStartAt().toLocalDate()) + ")을 " + action + "했습니다.",
			"/reservations/" + kind.getPath() + "/my");
	}

	// ---------- 내부 ----------

	/** 본인 또는 그 구분의 관리자. 끝난 예약은 관리자만 */
	private Reservation editable(Long id, String loginId, Set<ReservationKind> managed) {
		Reservation r = find(id);
		if (!managed.contains(ReservationKind.of(r.getResource()))) {
			if (!r.getOwner().getLoginId().equals(loginId)) {
				throw new AccessDeniedException("예약한 사람만 바꿀 수 있습니다.");
			}
			if (!r.getEndAt().isAfter(now())) {
				throw new BusinessException("이미 끝난 예약은 바꾸거나 취소할 수 없습니다.");
			}
		}
		return r;
	}

	private Item toItem(Reservation r, Member me, boolean manager) {
		Resource res = r.getResource();
		Member owner = r.getOwner();
		boolean ended = !r.getEndAt().isAfter(now());
		boolean editable = manager || (owner.getId().equals(me.getId()) && !ended);
		return new Item(r.getId(), ReservationKind.of(res), res.getId(), res.getType(), res.getName(), res.getLocation(), r.getTitle(),
			owner.getName(), deptName(owner), r.getStartAt(), r.getEndAt(), r.getMemo(), editable, ended,
			r.getSeries() == null ? null : r.getSeries().getId(),
			r.getSeries() == null ? null : r.getSeries().getRule(),
			r.getSeries() == null ? null : r.getSeries().getUntilDate());
	}

	private static String deptName(Member m) {
		return m.getDepartment() == null ? null : m.getDepartment().getName();
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	private Reservation find(Long id) {
		return reservationRepository.findDetail(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private Member member(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}
}
