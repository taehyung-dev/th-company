package th_prj.th_company.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.Permission;
import th_prj.th_company.schedule.ScheduleForm;
import th_prj.th_company.schedule.ScheduleItem;
import th_prj.th_company.schedule.ScheduleService;
import th_prj.th_company.schedule.ScheduleType;

/**
 * 월간 달력 + 일정 등록/상세/수정.
 */
@Controller
@RequestMapping("/schedule")
public class ScheduleController {

	private final ScheduleService scheduleService;
	private final ApprovalService approvalService;

	public ScheduleController(ScheduleService scheduleService, ApprovalService approvalService) {
		this.scheduleService = scheduleService;
		this.approvalService = approvalService;
	}

	/** @param ym 예: 2026-10. 없거나 잘못되면 이번 달 */
	@GetMapping
	public String calendar(@RequestParam(required = false) String ym, Authentication authentication, Model model) {
		YearMonth month;
		try {
			month = ym == null ? YearMonth.now() : YearMonth.parse(ym);
		} catch (DateTimeParseException e) {
			month = YearMonth.now();
		}
		model.addAttribute("view", scheduleService.month(month, authentication.getName()));
		model.addAttribute("types", ScheduleType.values());
		model.addAttribute("thisMonth", YearMonth.now());
		return "schedule/calendar";
	}

	@GetMapping("/new")
	public String createForm(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			Authentication authentication, Model model) {
		model.addAttribute("form", scheduleService.newForm(date == null ? LocalDate.now() : date));
		return renderForm(authentication, model);
	}

	@GetMapping("/{id}")
	public String view(@PathVariable Long id, Authentication authentication, Model model) {
		ScheduleItem item = scheduleService.get(id, authentication.getName(), SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER));
		model.addAttribute("item", item);
		model.addAttribute("files", scheduleService.files(id));
		return "schedule/view";
	}

	@GetMapping("/{id}/edit")
	public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
		model.addAttribute("form", scheduleService.getForm(id, authentication.getName(), SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER)));
		return renderForm(authentication, model);
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") ScheduleForm form, BindingResult bindingResult,
			@RequestParam(required = false) List<MultipartFile> uploads,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(authentication, model);
		}
		try {
			scheduleService.save(form, authentication.getName(), SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER), uploads);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(authentication, model);
		}
		redirect.addFlashAttribute("message", "일정을 저장했습니다.");
		return "redirect:/schedule?ym=" + YearMonth.from(form.getStartDate());
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
		ScheduleItem item = scheduleService.get(id, authentication.getName(), SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER));
		scheduleService.delete(id, authentication.getName(), SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER));
		redirect.addFlashAttribute("message", "일정을 삭제했습니다.");
		return "redirect:/schedule?ym=" + YearMonth.from(item.startAt());
	}

	private String renderForm(Authentication authentication, Model model) {
		ScheduleForm form = (ScheduleForm) model.getAttribute("form");
		model.addAttribute("files", form.getId() == null ? List.of() : scheduleService.files(form.getId()));
		model.addAttribute("allowedTypes",
			scheduleService.allowedTypes(authentication.getName(), SecurityUtils.can(authentication, Permission.SCHEDULE_MANAGER)));
		model.addAttribute("approverGroups", approvalService.approverGroups(authentication.getName()));
		model.addAttribute("selectedParticipants", approvalService.approverLabels(form.getParticipantIds()));
		model.addAttribute("maxParticipants", ScheduleService.MAX_PARTICIPANTS);
		return "schedule/form";
	}
}
