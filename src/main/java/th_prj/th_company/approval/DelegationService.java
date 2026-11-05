package th_prj.th_company.approval;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Member;
import th_prj.th_company.member.MemberRepository;
import th_prj.th_company.notification.NotificationService;

/**
 * 대결 설정. 결재자가 자리를 비우는 기간에 대신 결재할 사람을 스스로 지정한다.
 * 기간이 겹치게 두 사람을 지정할 수 없고, 대결자가 다시 다른 사람에게 넘길 수는 없다 (한 단계만).
 */
@Service
@Transactional(readOnly = true)
public class DelegationService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	private final ApprovalDelegationRepository repository;
	private final MemberRepository memberRepository;
	private final NotificationService notificationService;
	private final Clock clock;

	public DelegationService(ApprovalDelegationRepository repository, MemberRepository memberRepository,
			NotificationService notificationService, Clock clock) {
		this.repository = repository;
		this.memberRepository = memberRepository;
		this.notificationService = notificationService;
		this.clock = clock;
	}

	/**
	 * @param personName 내가 맡긴 목록은 대결자, 내가 맡은 목록은 원 결재자
	 * @param active     오늘이 기간 안
	 */
	public record Row(Long id, String personName, LocalDate startDate, LocalDate endDate, String reason,
			boolean active, boolean ended) {

		public String period() {
			return startDate.format(DATE) + " ~ " + endDate.format(DATE);
		}
	}

	public List<Row> mine(String loginId) {
		LocalDate today = today();
		return repository.findByDelegator(member(loginId).getId()).stream()
			.map(d -> row(d, d.getDelegate(), today))
			.toList();
	}

	public List<Row> assignedToMe(String loginId) {
		LocalDate today = today();
		return repository.findCurrentByDelegate(member(loginId).getId(), today).stream()
			.map(d -> row(d, d.getDelegator(), today))
			.toList();
	}

	public DelegationForm newForm() {
		DelegationForm form = new DelegationForm();
		form.setStartDate(today());
		form.setEndDate(today());
		return form;
	}

	@Transactional
	public void save(DelegationForm form, String loginId) {
		Member me = member(loginId);
		if (form.getDelegateId().equals(me.getId())) {
			throw new BusinessException("본인을 대결자로 지정할 수 없습니다.");
		}
		if (form.getEndDate().isBefore(form.getStartDate())) {
			throw new BusinessException("종료일이 시작일보다 빠를 수 없습니다.");
		}
		if (form.getEndDate().isBefore(today())) {
			throw new BusinessException("이미 지난 기간입니다.");
		}
		if (repository.existsOverlapping(me.getId(), form.getStartDate(), form.getEndDate())) {
			throw new BusinessException("같은 기간에 지정한 대결자가 있습니다. 먼저 해제하세요.");
		}
		Member delegate = memberRepository.findById(form.getDelegateId())
			.filter(Member::isEnabled)
			.orElseThrow(() -> new BusinessException("대결자로 지정할 수 없는 사용자입니다."));
		String reason = form.getReason() == null || form.getReason().isBlank() ? null : form.getReason().trim();
		repository.save(new ApprovalDelegation(me, delegate, form.getStartDate(), form.getEndDate(), reason));
		notificationService.send(delegate, "결재",
			me.getName() + " 님이 " + form.getStartDate().format(DATE) + " ~ " + form.getEndDate().format(DATE)
				+ " 대결자로 지정했습니다.", "/approval/delegation");
	}

	@Transactional
	public void delete(Long id, String loginId) {
		ApprovalDelegation d = repository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!d.getDelegator().getLoginId().equals(loginId)) {
			throw new AccessDeniedException("지정한 사람만 해제할 수 있습니다.");
		}
		repository.delete(d);
	}

	private static Row row(ApprovalDelegation d, Member person, LocalDate today) {
		boolean ended = d.getEndDate().isBefore(today);
		return new Row(d.getId(), person.getName(), d.getStartDate(), d.getEndDate(), d.getReason(),
			!ended && !d.getStartDate().isAfter(today), ended);
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

	private Member member(String loginId) {
		return memberRepository.findByLoginId(loginId)
			.orElseThrow(() -> new AccessDeniedException("사용자 정보를 찾을 수 없습니다."));
	}
}
