package th_prj.th_company.admin;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.menu.MainMenu;
import th_prj.th_company.menu.MenuService;

/**
 * 상단 메뉴 순서 관리. 끌어서 놓거나 ▲▼ 로 순서를 바꾼 뒤 저장한다.
 */
@Controller
@RequestMapping("/admin/menus")
public class MenuAdminController {

	private final MenuService menuService;

	public MenuAdminController(MenuService menuService) {
		this.menuService = menuService;
	}

	@GetMapping
	public String list(Model model) {
		model.addAttribute("menu", "menus");
		model.addAttribute("menus", menuService.ordered());
		return "admin/menus";
	}

	/** @param menus 화면에 보이는 순서대로의 메뉴 이름 (HOME, BOARD ...) */
	@PostMapping
	public String save(@RequestParam(required = false) List<String> menus, RedirectAttributes redirect) {
		try {
			menuService.reorder(menus == null ? null : menus.stream().map(MenuAdminController::parse).toList());
			redirect.addFlashAttribute("message", "메뉴 순서를 저장했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/admin/menus";
	}

	@PostMapping("/reset")
	public String reset(RedirectAttributes redirect) {
		menuService.reset();
		redirect.addFlashAttribute("message", "메뉴 순서를 기본값으로 되돌렸습니다.");
		return "redirect:/admin/menus";
	}

	private static MainMenu parse(String name) {
		try {
			return MainMenu.valueOf(name);
		} catch (IllegalArgumentException e) {
			throw new BusinessException("메뉴 목록이 올바르지 않습니다. 화면을 새로고침한 뒤 다시 시도하세요.");
		}
	}
}
