package th_prj.th_company.web;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import th_prj.th_company.notification.NotificationPush;
import th_prj.th_company.notification.NotificationService;

/**
 * 알림: 상단 종 아이콘의 목록(조각 화면), 전체 목록, 눌러서 이동, 실시간 연결.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notificationService;
	private final NotificationPush notificationPush;

	public NotificationController(NotificationService notificationService, NotificationPush notificationPush) {
		this.notificationService = notificationService;
		this.notificationPush = notificationPush;
	}

	/** 실시간 알림 연결 (Server-Sent Events). 상단 바의 스크립트가 연다 */
	@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@ResponseBody
	public SseEmitter stream(Authentication authentication) {
		return notificationPush.subscribe(authentication.getName());
	}

	/** @param page 1부터 */
	@GetMapping
	public String list(@RequestParam(defaultValue = "1") int page, Authentication authentication, Model model) {
		model.addAttribute("items", notificationService.list(authentication.getName(), page - 1));
		return "notification/list";
	}

	/** 종 아이콘 패널 안에 넣는 조각 */
	@GetMapping("/recent")
	public String recent(Authentication authentication, Model model) {
		model.addAttribute("items", notificationService.recent(authentication.getName()));
		return "notification/recent";
	}

	/** 읽음 처리 후 해당 화면으로 */
	@GetMapping("/{id:\\d+}/open")
	public String open(@PathVariable Long id, Authentication authentication) {
		return "redirect:" + notificationService.open(id, authentication.getName());
	}

	@PostMapping("/read-all")
	public String readAll(Authentication authentication) {
		notificationService.markAllRead(authentication.getName());
		return "redirect:/notifications";
	}
}
