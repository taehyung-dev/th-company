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
import th_prj.th_company.reservation.ReservationKind;
import th_prj.th_company.reservation.ResourceAdminService;
import th_prj.th_company.reservation.ResourceForm;
import th_prj.th_company.reservation.ResourceType;

/**
 * 회의실 관리(/admin/rooms)와 자원 관리(/admin/resources). 화면은 같고 다루는 자원 종류와 권한이 다르다
 * (권한은 SecurityConfig 에서 경로별로 건다).
 */
@Controller
@RequestMapping("/admin/{kind:rooms|resources}")
public class ResourceAdminController {

	private static final String VIEW = "admin/resources";

	private final ResourceAdminService resourceAdminService;

	public ResourceAdminController(ResourceAdminService resourceAdminService) {
		this.resourceAdminService = resourceAdminService;
	}

	@GetMapping
	public String list(@PathVariable("kind") String path, @RequestParam(required = false) Long edit,
			@RequestParam(required = false) ResourceType type, Model model) {
		ReservationKind kind = kind(path);
		model.addAttribute("form", edit == null ? resourceAdminService.newForm(kind, type) : resourceAdminService.getForm(kind, edit));
		return render(kind, model);
	}

	@PostMapping
	public String save(@PathVariable("kind") String path, @Valid @ModelAttribute("form") ResourceForm form,
			BindingResult bindingResult, Model model, RedirectAttributes redirect) {
		ReservationKind kind = kind(path);
		if (bindingResult.hasErrors()) {
			return render(kind, model);
		}
		try {
			resourceAdminService.save(kind, form);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return render(kind, model);
		}
		redirect.addFlashAttribute("message", "'" + form.getName().trim() + "'을(를) 저장했습니다.");
		return "redirect:/admin/" + kind.getPath();
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable("kind") String path, @PathVariable Long id, RedirectAttributes redirect) {
		ReservationKind kind = kind(path);
		try {
			resourceAdminService.delete(kind, id);
			redirect.addFlashAttribute("message", "삭제했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/" + kind.getPath();
	}

	private String render(ReservationKind kind, Model model) {
		model.addAttribute("menu", kind.getPath());
		model.addAttribute("kind", kind);
		model.addAttribute("rows", resourceAdminService.rows(kind));
		return VIEW;
	}

	private static ReservationKind kind(String path) {
		ReservationKind kind = ReservationKind.fromPath(path);
		if (kind == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return kind;
	}
}
