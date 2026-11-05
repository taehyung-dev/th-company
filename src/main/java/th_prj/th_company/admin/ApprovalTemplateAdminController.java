package th_prj.th_company.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.approval.ApprovalTemplateAdminService;
import th_prj.th_company.approval.ApprovalTemplateForm;
import th_prj.th_company.approval.FieldType;
import th_prj.th_company.common.BusinessException;

/** 관리자 > 결재 양식 관리 (관리자, 결재양식관리자) */
@Controller
@RequestMapping("/admin/approval-templates")
public class ApprovalTemplateAdminController {

	private static final String FORM_VIEW = "admin/approval-template-form";

	private final ApprovalTemplateAdminService service;

	public ApprovalTemplateAdminController(ApprovalTemplateAdminService service) {
		this.service = service;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("menu", "approval-templates");
		model.addAttribute("templates", service.list());
		return "admin/approval-templates";
	}

	@GetMapping("/new")
	public String createForm(Model model) {
		model.addAttribute("form", service.newForm());
		return renderForm(model);
	}

	@GetMapping("/{id}")
	public String editForm(@PathVariable Long id, Model model) {
		model.addAttribute("form", service.getForm(id));
		return renderForm(model);
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") ApprovalTemplateForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(model);
		}
		try {
			Long id = service.save(form);
			redirect.addFlashAttribute("message", "'" + form.getName().trim() + "' 양식을 저장했습니다.");
			return "redirect:/admin/approval-templates/" + id;
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(model);
		}
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			redirect.addFlashAttribute("message", "'" + service.delete(id) + "' 양식을 삭제했습니다.");
			return "redirect:/admin/approval-templates";
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/admin/approval-templates/" + id;
		}
	}

	private String renderForm(Model model) {
		model.addAttribute("menu", "approval-templates");
		model.addAttribute("fieldTypes", FieldType.values());
		model.addAttribute("maxFields", ApprovalTemplateAdminService.MAX_FIELDS);
		ApprovalTemplateForm form = (ApprovalTemplateForm) model.getAttribute("form");
		model.addAttribute("documentCount", form.getId() == null ? 0 : service.documentCount(form.getId()));
		return FORM_VIEW;
	}
}
