package th_prj.th_company.admin;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

	private final AdminNavService adminNavService;

	public AdminController(AdminNavService adminNavService) {
		this.adminNavService = adminNavService;
	}

	/** 관리자 화면 첫 화면: 상단 메뉴의 첫 구분. 관리 권한이 하나도 없으면 403 */
	@GetMapping("/admin")
	public String index(Authentication authentication) {
		AdminNav nav = adminNavService.build(authentication);
		if (nav.sections().isEmpty()) {
			throw new AccessDeniedException("관리 권한이 없습니다.");
		}
		return "redirect:" + nav.sections().get(0).url();
	}
}
