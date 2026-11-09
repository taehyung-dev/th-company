package th_prj.th_company.admin;

import java.util.List;
import java.util.function.Function;

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
 * 메뉴 순서 관리. 사용자 화면 상단 메뉴와 관리자 화면 상단 메뉴의 순서를 같은 화면 틀로 바꾼다.
 * 끌어서 놓거나 ▲▼ 로 순서를 바꾼 뒤 저장한다.
 */
@Controller
@RequestMapping("/admin/menus")
public class MenuAdminController {

	private static final String USER_URL = "/admin/menus";
	private static final String ADMIN_URL = "/admin/menus/admin";

	private final MenuService menuService;
	private final AdminMenuService adminMenuService;

	public MenuAdminController(MenuService menuService, AdminMenuService adminMenuService) {
		this.menuService = menuService;
		this.adminMenuService = adminMenuService;
	}

	// ---------- 사용자 화면 메뉴 ----------

	@GetMapping
	public String list(Model model) {
		model.addAttribute("menu", "menus");
		model.addAttribute("title", "사용자 메뉴 순서");
		model.addAttribute("description", "사용자 화면 상단 메뉴의 순서입니다.");
		model.addAttribute("action", USER_URL);
		model.addAttribute("rows", menuService.ordered().stream()
			.map(m -> new MenuOrderRow(m.name(), m.getLabel(), m.getPath() == null ? "준비 중" : m.getPath()))
			.toList());
		return "admin/menus";
	}

	/** @param menus 화면에 보이는 순서대로의 메뉴 이름 (HOME, BOARD ...) */
	@PostMapping
	public String save(@RequestParam(required = false) List<String> menus, RedirectAttributes redirect) {
		try {
			menuService.reorder(parse(menus, MainMenu::valueOf));
			redirect.addFlashAttribute("message", "메뉴 순서를 저장했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + USER_URL;
	}

	@PostMapping("/reset")
	public String reset(RedirectAttributes redirect) {
		menuService.reset();
		redirect.addFlashAttribute("message", "메뉴 순서를 기본값으로 되돌렸습니다.");
		return "redirect:" + USER_URL;
	}

	// ---------- 관리자 화면 메뉴 ----------

	@GetMapping("/admin")
	public String adminList(Model model) {
		model.addAttribute("menu", "admin-menus");
		model.addAttribute("title", "관리자 메뉴 순서");
		model.addAttribute("description",
			"관리자 화면 상단 메뉴의 순서입니다. 관리 권한만 있는 사람에게는 그 권한의 메뉴만 이 순서로 보입니다.");
		model.addAttribute("action", ADMIN_URL);
		model.addAttribute("rows", adminMenuService.ordered().stream()
			.map(s -> new MenuOrderRow(s.name(), s.getLabel(), s.getDescription()))
			.toList());
		return "admin/menus";
	}

	/** @param menus 화면에 보이는 순서대로의 구분 이름 (HR, PERMISSION ...) */
	@PostMapping("/admin")
	public String adminSave(@RequestParam(required = false) List<String> menus, RedirectAttributes redirect) {
		try {
			adminMenuService.reorder(parse(menus, AdminSection::valueOf));
			redirect.addFlashAttribute("message", "관리자 메뉴 순서를 저장했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + ADMIN_URL;
	}

	@PostMapping("/admin/reset")
	public String adminReset(RedirectAttributes redirect) {
		adminMenuService.reset();
		redirect.addFlashAttribute("message", "관리자 메뉴 순서를 기본값으로 되돌렸습니다.");
		return "redirect:" + ADMIN_URL;
	}

	private static <E> List<E> parse(List<String> names, Function<String, E> valueOf) {
		if (names == null) {
			return null;
		}
		try {
			return names.stream().map(valueOf).toList();
		} catch (IllegalArgumentException e) {
			throw new BusinessException("메뉴 목록이 올바르지 않습니다. 화면을 새로고침한 뒤 다시 시도하세요.");
		}
	}
}
