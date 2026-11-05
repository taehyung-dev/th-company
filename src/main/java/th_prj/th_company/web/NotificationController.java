package th_prj.th_company.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import th_prj.th_company.notification.NotificationService;

/**
 * 알림: 상단 종 아이콘의 목록(조각 화면), 전체 목록, 눌러서 이동.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
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
