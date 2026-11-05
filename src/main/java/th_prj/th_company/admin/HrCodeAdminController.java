package th_prj.th_company.admin;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.org.HrCodeForm;
import th_prj.th_company.org.HrCodeService;
import th_prj.th_company.org.HrCodeType;

/**
 * 직급/직책 관리. /admin/codes/grade, /admin/codes/duty
 */
@Controller
@RequestMapping("/admin/codes/{typePath}")
public class HrCodeAdminController {

	private static final String VIEW = "admin/codes";

	private final HrCodeService hrCodeService;

	public HrCodeAdminController(HrCodeService hrCodeService) {
		this.hrCodeService = hrCodeService;
	}

	@GetMapping
	public String list(@PathVariable String typePath, @RequestParam(required = false) Long edit, Model model) {
		HrCodeType type = type(typePath);
		model.addAttribute("form", edit == null ? new HrCodeForm() : hrCodeService.getForm(type, edit));
		return render(type, model);
	}

	@PostMapping
	public String save(@PathVariable String typePath, @Valid @ModelAttribute("form") HrCodeForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirect) {
		HrCodeType type = type(typePath);
		if (bindingResult.hasErrors()) {
			return render(type, model);
		}
		try {
			hrCodeService.save(type, form);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return render(type, model);
		}
		redirect.addFlashAttribute("message", type.getLabel() + "을(를) 저장했습니다.");
		return "redirect:/admin/codes/" + type.getPath();
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable String typePath, @PathVariable Long id, RedirectAttributes redirect) {
		HrCodeType type = type(typePath);
		try {
			hrCodeService.delete(type, id);
			redirect.addFlashAttribute("message", type.getLabel() + "을(를) 삭제했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/codes/" + type.getPath();
	}

	private String render(HrCodeType type, Model model) {
		model.addAttribute("menu", "codes-" + type.getPath());
		model.addAttribute("type", type);
		model.addAttribute("codes", hrCodeService.list(type));
		return VIEW;
	}

	private static HrCodeType type(String path) {
		return HrCodeType.fromPath(path).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}
}
