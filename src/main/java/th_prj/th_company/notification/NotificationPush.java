package th_prj.th_company.notification;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 알림을 화면에 바로 보낸다 (Server-Sent Events). 화면(탭)마다 연결 하나를 열어 두고,
 * 알림이 저장된 트랜잭션이 커밋된 뒤에 그 사람의 연결로 새 알림과 안 읽은 수를 보낸다.
 * 연결이 끊기면 브라우저(EventSource)가 알아서 다시 연결한다. 서버를 여러 대 띄우면 같은 서버에 붙은 화면에만 간다.
 */
@Component
public class NotificationPush {

	/** 연결을 이 시간 동안 열어 두고, 끝나면 브라우저가 다시 연결한다 */
	static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
	/** 한 사람이 동시에 열 수 있는 연결 (탭) 수. 넘으면 오래된 것부터 닫는다 */
	static final int MAX_PER_USER = 8;

	private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
	private final NotificationRepository repository;

	public NotificationPush(NotificationRepository repository) {
		this.repository = repository;
	}

	/** 새 알림. url 은 누르면 읽음 처리하고 이동하는 주소 */
	public record Pushed(long unread, String category, String message, String url) {
	}

	/** NotificationService.send 가 저장할 때 낸다 */
	public record Created(Long id, String loginId, String category, String message) {
	}

	public SseEmitter subscribe(String loginId) {
		SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
		List<SseEmitter> mine = emitters.computeIfAbsent(loginId, k -> new CopyOnWriteArrayList<>());
		mine.add(emitter);
		while (mine.size() > MAX_PER_USER) {
			SseEmitter oldest = mine.remove(0);
			oldest.complete();
		}
		Runnable remove = () -> mine.remove(emitter);
		emitter.onCompletion(remove);
		emitter.onTimeout(remove);
		emitter.onError(e -> remove.run());
		// 처음 연결하면 지금 안 읽은 수를 보낸다 (다른 탭에서 읽었을 수 있다)
		send(emitter, mine, "count", Map.of("unread", repository.countUnread(loginId)));
		return emitter;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void onCreated(Created created) {
		List<SseEmitter> mine = emitters.get(created.loginId());
		if (mine == null || mine.isEmpty()) {
			return;
		}
		Pushed pushed = new Pushed(repository.countUnread(created.loginId()), created.category(), created.message(),
			"/notifications/" + created.id() + "/open");
		for (SseEmitter emitter : mine) {
			send(emitter, mine, "notification", pushed);
		}
	}

	/** 지금 열려 있는 연결 수 (테스트, 점검용) */
	int connections(String loginId) {
		List<SseEmitter> mine = emitters.get(loginId);
		return mine == null ? 0 : mine.size();
	}

	private static void send(SseEmitter emitter, List<SseEmitter> mine, String name, Object data) {
		try {
			emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
		} catch (IOException | IllegalStateException e) {
			// 브라우저가 이미 닫혔다
			mine.remove(emitter);
		}
	}
}
