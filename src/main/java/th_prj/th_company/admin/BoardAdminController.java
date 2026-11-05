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
import th_prj.th_company.board.BoardForm;
import th_prj.th_company.board.BoardService;
import th_prj.th_company.board.BoardType;
import th_prj.th_company.common.BusinessException;

@Controller
@RequestMapping("/admin/boards")
public class BoardAdminController {

	private static final String VIEW = "admin/boards";

	private final BoardService boardService;

	public BoardAdminController(BoardService boardService) {
		this.boardService = boardService;
	}

	@GetMapping
	public String list(@RequestParam(required = false) Long edit, Model model) {
		model.addAttribute("form", edit == null ? new BoardForm() : boardService.getForm(edit));
		return render(model);
	}

	@PostMapping
	public String save(@Valid @ModelAttribute("form") BoardForm form, BindingResult bindingResult,
			Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return render(model);
		}
		try {
			boardService.save(form);
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return render(model);
		}
		redirect.addFlashAttribute("message", form.getType().getLabel() + "를 저장했습니다.");
		return "redirect:/admin/boards";
	}

	@PostMapping("/{id}/delete")
	public String delete(@PathVariable Long id, RedirectAttributes redirect) {
		try {
			boardService.delete(id);
			redirect.addFlashAttribute("message", "삭제했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/boards";
	}

	private String render(Model model) {
		model.addAttribute("menu", "boards");
		model.addAttribute("rows", boardService.tree());
		model.addAttribute("types", BoardType.values());
		return VIEW;
	}
}
