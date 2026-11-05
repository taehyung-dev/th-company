package th_prj.th_company.admin;

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
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.org.DepartmentForm;
import th_prj.th_company.org.DepartmentService;

@Controller
@RequestMapping("/admin/departments")
public class DepartmentAdminController {

	private static final String VIEW = "admin/departments";

	private final DepartmentService departmentService;

	public DepartmentAdminController(DepartmentService departmentService) {
		this.departmentService = departmentService;
	}

	@GetMapping
	public String list(@RequestParam(required = false) Long edit, Model model) {
		model.addAttribute("form", edit == null ? new DepartmentForm() : departmentService.getForm(edit));
		return render(model);
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") DepartmentForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return render(model);
		}
		try {
			departmentService.save(form);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return render(model);
		}
		redirect.addFlashAttribute("message", "부서를 저장했습니다.");
		return "redirect:/admin/departments";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			departmentService.delete(id);
			redirect.addFlashAttribute("message", "부서를 삭제했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/departments";
	}

	private String render(Model model) {
		model.addAttribute("menu", "departments");
		model.addAttribute("rows", departmentService.tree());
		return VIEW;
	}
}
