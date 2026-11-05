package th_prj.th_company.admin;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.Permission;

@Controller
public class AdminController {

	/** 관리자 화면 첫 화면: 관리자는 사용자 관리, 관리 권한만 있으면 그 기능 화면 */
	@GetMapping("/admin")
	public String index(Authentication authentication) {
		if (SecurityUtils.isAdmin(authentication)) {
			return "redirect:/admin/members";
		}
		if (SecurityUtils.can(authentication, Permission.BOARD_MANAGER)) {
			return "redirect:/admin/boards";
		}
		if (SecurityUtils.can(authentication, Permission.APPROVAL_MANAGER)) {
			return "redirect:/admin/approval-templates";
		}
		if (SecurityUtils.can(authentication, Permission.ROOM_MANAGER)) {
			return "redirect:/admin/rooms";
		}
		if (SecurityUtils.can(authentication, Permission.RESOURCE_MANAGER)) {
			return "redirect:/admin/resources";
		}
		throw new AccessDeniedException("관리 권한이 없습니다.");
	}
}
