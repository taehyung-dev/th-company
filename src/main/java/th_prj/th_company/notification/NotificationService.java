package th_prj.th_company.notification;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import th_prj.th_company.member.Member;

/**
 * 알림. 각 기능의 서비스가 자기 트랜잭션 안에서 send 를 부른다 (작업이 취소되면 알림도 남지 않는다).
 * 커밋되면 NotificationPush 가 열려 있는 화면에 바로 보낸다.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

	public static final int PAGE_SIZE = 20;
	private static final int RECENT = 8;
	private static final int MAX_MESSAGE = 300;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm");

	private final NotificationRepository repository;
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public NotificationService(NotificationRepository repository, Clock clock, ApplicationEventPublisher events) {
		this.repository = repository;
		this.clock = clock;
		this.events = events;
	}

	public record Row(Long id, String category, String message, String time, boolean read) {
	}

	@Transactional
	public void send(Member to, String category, String message, String link) {
		String text = message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE - 1) + "…" : message;
		Notification saved = repository.save(new Notification(to, category, text, link, LocalDateTime.now(clock)));
		events.publishEvent(new NotificationPush.Created(saved.getId(), to.getLoginId(), category, text));
	}

	public long unreadCount(String loginId) {
		return repository.countUnread(loginId);
	}

	/** 종 아이콘을 눌렀을 때 나오는 최근 알림 */
	public Page<Row> recent(String loginId) {
		return page(loginId, 0, RECENT);
	}

	/** page 는 0부터 */
	public Page<Row> list(String loginId, int page) {
		return page(loginId, Math.max(page, 0), PAGE_SIZE);
	}

	private Page<Row> page(String loginId, int page, int size) {
		return repository.findMine(loginId, PageRequest.of(page, size))
			.map(n -> new Row(n.getId(), n.getCategory(), n.getMessage(), n.getCreatedAt().format(TIME), n.getReadAt() != null));
	}

	/** 읽음으로 표시하고 이동할 경로를 돌려준다. 남의 알림은 없는 것으로 본다 */
	@Transactional
	public String open(Long id, String loginId) {
		Notification n = repository.findById(id)
			.filter(x -> x.getRecipient().getLoginId().equals(loginId))
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		n.read(LocalDateTime.now(clock));
		return n.getLink();
	}

	@Transactional
	public void markAllRead(String loginId) {
		repository.markAllRead(loginId, LocalDateTime.now(clock));
	}
}
