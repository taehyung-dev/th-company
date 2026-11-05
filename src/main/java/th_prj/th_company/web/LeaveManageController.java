package th_prj.th_company.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.leave.LeaveService;

/**
 * 전체 휴가 현황. 근태 메뉴(근태관리자와 관리자)와 관리자 화면(관리자)에서 같이 쓴다 (SecurityConfig).
 */
@Controller
@RequestMapping({LeaveManageController.ATTENDANCE_PATH, LeaveManageController.ADMIN_PATH})
public class LeaveManageController {

	static final String ATTENDANCE_PATH = "/attendance/manage/leave";
	static final String ADMIN_PATH = "/admin/attendance/leave";

	private final LeaveService leaveService;

	public LeaveManageController(LeaveService leaveService) {
		this.leaveService = leaveService;
	}

	/** 관리자 화면 주소로 들어왔으면 관리자 화면 틀로 보여 준다 */
	static boolean fromAdmin(HttpServletRequest request) {
		return request.getRequestURI().substring(request.getContextPath().length()).startsWith("/admin/");
	}

	@GetMapping
	public String manage(@RequestParam(required = false) Integer year, HttpServletRequest request, Model model) {
		int y = year == null ? leaveService.currentYear() : year;
		model.addAttribute("menu", "leave-manage");
		model.addAttribute("year", y);
		model.addAttribute("members", leaveService.memberRows(y));
		model.addAttribute("requests", leaveService.allRequests(y));
		model.addAttribute("base", base(request));
		return fromAdmin(request) ? "admin/leave-manage" : "attendance/leave-manage";
	}

	/** days 를 비우면 입사일 기준으로 되돌린다 */
	@PostMapping("/grant")
	public String grant(@RequestParam Long member, @RequestParam int year, @RequestParam(required = false) Double days,
			HttpServletRequest request, RedirectAttributes redirect) {
		try {
			leaveService.setGranted(member, year, days);
			redirect.addFlashAttribute("message", "연차 일수를 저장했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + base(request) + "?year=" + year;
	}

	@PostMapping("/{id}/cancel")
	public String cancelByManager(@PathVariable Long id, @RequestParam int year, Authentication authentication,
			HttpServletRequest request, RedirectAttributes redirect) {
		try {
			leaveService.cancelByManager(id, authentication.getName());
			redirect.addFlashAttribute("message", "휴가를 취소했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + base(request) + "?year=" + year;
	}

	private static String base(HttpServletRequest request) {
		return fromAdmin(request) ? ADMIN_PATH : ATTENDANCE_PATH;
	}
}
