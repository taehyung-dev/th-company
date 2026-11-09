package th_prj.th_company.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.leave.LeaveForm;
import th_prj.th_company.leave.LeaveService;
import th_prj.th_company.leave.LeaveType;

/**
 * 휴가 (근태관리 메뉴 안). 내 휴가와 신청은 누구나. 전체 휴가 현황은 LeaveManageController.
 */
@Controller
@RequestMapping("/attendance")
public class LeaveController {

	private final LeaveService leaveService;
	private final ApprovalService approvalService;

	public LeaveController(LeaveService leaveService, ApprovalService approvalService) {
		this.leaveService = leaveService;
		this.approvalService = approvalService;
	}

	@GetMapping("/leave")
	public String my(@RequestParam(required = false) Integer year, Authentication authentication, Model model) {
		int y = year == null ? leaveService.currentYear() : year;
		model.addAttribute("menu", "leave");
		model.addAttribute("year", y);
		model.addAttribute("summary", leaveService.summary(authentication.getName(), y));
		model.addAttribute("requests", leaveService.myRequests(authentication.getName(), y));
		return "attendance/leave";
	}

	@GetMapping("/leave/new")
	public String createForm(Authentication authentication, Model model) {
		model.addAttribute("form", leaveService.newForm());
		return renderForm(authentication, model);
	}

	@PostMapping("/leave")
	public String request(@Valid @ModelAttribute("form") LeaveForm form, BindingResult bindingResult,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(authentication, model);
		}
		try {
			leaveService.request(form, authentication.getName());
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(authentication, model);
		}
		redirect.addFlashAttribute("message", "휴가를 신청했습니다. 결재가 끝나면 확정됩니다.");
		return "redirect:/attendance/leave?year=" + form.getStartDate().getYear();
	}

	@PostMapping("/leave/{id}/cancel")
	public String cancel(@PathVariable Long id, @RequestParam(required = false) Integer year,
			Authentication authentication, RedirectAttributes redirect) {
		try {
			leaveService.cancel(id, authentication.getName());
			redirect.addFlashAttribute("message", "휴가를 취소했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/attendance/leave" + (year == null ? "" : "?year=" + year);
	}

	private String renderForm(Authentication authentication, Model model) {
		LeaveForm form = (LeaveForm) model.getAttribute("form");
		model.addAttribute("menu", "leave");
		model.addAttribute("types", LeaveType.values());
		model.addAttribute("summary", leaveService.summary(authentication.getName(), leaveService.currentYear()));
		model.addAttribute("approverGroups", approvalService.approverGroups(authentication.getName()));
		model.addAttribute("selectedApprovers", approvalService.approverLabels(form.getApproverIds()));
		model.addAttribute("maxApprovers", ApprovalService.MAX_APPROVERS);
		return "attendance/leave-form";
	}
}
