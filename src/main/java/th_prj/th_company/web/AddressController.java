package th_prj.th_company.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import th_prj.th_company.address.AddressService;
import th_prj.th_company.address.AddressViews.Employee;
import th_prj.th_company.address.AddressViews.GroupItem;
import th_prj.th_company.address.AddressViews.PersonalNav;
import th_prj.th_company.address.ContactForm;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.org.DeptView;
import th_prj.th_company.org.OrgChartService;

/**
 * 주소록. 좌측 메뉴: 사내 주소록 | 개인 주소록(전체, 즐겨찾기, 그룹별) | 그룹 관리.
 */
@Controller
@RequestMapping("/address")
public class AddressController {

	private final AddressService addressService;
	private final OrgChartService orgChartService;

	public AddressController(AddressService addressService, OrgChartService orgChartService) {
		this.addressService = addressService;
		this.orgChartService = orgChartService;
	}

	@GetMapping
	public String index() {
		return "redirect:/address/company";
	}

	/** 사내 주소록. 조직도처럼 고른 부서 인원을 보여 주고, 검색어가 있으면 회사 전체에서 찾는다 */
	@GetMapping("/company")
	public String company(@RequestParam(required = false) String q, @RequestParam(required = false) Long dept,
			Authentication authentication, Model model) {
		DeptView<Employee> view = orgChartService.deptView(addressService.directory(null), Employee::deptId, dept,
			authentication.getName(), false);
		boolean searching = q != null && !q.isBlank();
		model.addAttribute("org", searching ? view.searchResult(addressService.directory(q)) : view);
		model.addAttribute("searching", searching);
		model.addAttribute("q", q);
		return nav("company", authentication, model, "address/company");
	}

	/** 개인 주소록. fav=true 면 즐겨찾기, g 면 그룹 */
	@GetMapping("/personal")
	public String personal(@RequestParam(required = false) String q, @RequestParam(required = false) Long g,
			@RequestParam(defaultValue = "false") boolean fav, @RequestParam(defaultValue = "1") int page,
			Authentication authentication, Model model) {
		String loginId = authentication.getName();
		model.addAttribute("contacts", addressService.contacts(loginId, fav, g, q, page - 1));
		model.addAttribute("q", q);
		model.addAttribute("g", g);
		model.addAttribute("fav", fav);
		String menu = fav ? "favorite" : (g != null ? "group-" + g : "personal");
		String view = nav(menu, authentication, model, "address/personal");
		PersonalNav nav = (PersonalNav) model.getAttribute("nav");
		model.addAttribute("title", fav ? "즐겨찾기" : g == null ? "개인 주소록" : nav.groups().stream()
			.filter(gr -> gr.id().equals(g)).map(GroupItem::name).findFirst().orElse("개인 주소록"));
		return view;
	}

	@GetMapping("/personal/new")
	public String createForm(@RequestParam(required = false) Long g, Authentication authentication, Model model) {
		model.addAttribute("form", addressService.newForm(g));
		return nav("personal", authentication, model, "address/form");
	}

	@GetMapping("/personal/{id}/edit")
	public String editForm(@PathVariable Long id, Authentication authentication, Model model) {
		model.addAttribute("form", addressService.getForm(id, authentication.getName()));
		return nav("personal", authentication, model, "address/form");
	}

	@PostMapping("/personal")
	public String save(@Valid @ModelAttribute("form") ContactForm form, BindingResult bindingResult,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return nav("personal", authentication, model, "address/form");
		}
		try {
			addressService.save(form, authentication.getName());
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return nav("personal", authentication, model, "address/form");
		}
		redirect.addFlashAttribute("message", form.getName().trim() + " 연락처를 저장했습니다.");
		return form.getGroupId() == null ? "redirect:/address/personal" : "redirect:/address/personal?g=" + form.getGroupId();
	}

	@PostMapping("/personal/{id}/delete")
	public String delete(@PathVariable Long id, @RequestParam(required = false) String back,
			Authentication authentication, RedirectAttributes redirect) {
		String name = addressService.delete(id, authentication.getName());
		redirect.addFlashAttribute("message", name + " 연락처를 삭제했습니다.");
		return "redirect:" + back(back, "/address/personal");
	}

	@PostMapping("/personal/{id}/favorite")
	public String favorite(@PathVariable Long id, @RequestParam(required = false) String back,
			Authentication authentication) {
		addressService.toggleFavorite(id, authentication.getName());
		return "redirect:" + back(back, "/address/personal");
	}

	/** 사내 주소록에서 [개인 주소록에 추가] */
	@PostMapping("/personal/add-employee")
	public String addEmployee(@RequestParam Long memberId, @RequestParam(required = false) String back,
			Authentication authentication, RedirectAttributes redirect) {
		try {
			String name = addressService.addEmployee(memberId, authentication.getName());
			redirect.addFlashAttribute("message", name + "님을 개인 주소록에 추가했습니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:" + back(back, "/address/company");
	}

	@GetMapping("/personal/export")
	public ResponseEntity<byte[]> export(Authentication authentication) {
		byte[] body = addressService.exportCsv(authentication.getName()).getBytes(StandardCharsets.UTF_8);
		String filename = "개인주소록_" + LocalDate.now() + ".csv";
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.body(body);
	}

	// ---- 그룹 관리 ----

	@GetMapping("/groups")
	public String groups(Authentication authentication, Model model) {
		return nav("groups", authentication, model, "address/groups");
	}

	@PostMapping("/groups")
	public String createGroup(@RequestParam String name, Authentication authentication, RedirectAttributes redirect) {
		return runGroup(redirect, () -> {
			addressService.createGroup(name, authentication.getName());
			return "'" + name.trim() + "' 그룹을 추가했습니다.";
		});
	}

	@PostMapping("/groups/{id}/rename")
	public String renameGroup(@PathVariable Long id, @RequestParam String name, Authentication authentication,
			RedirectAttributes redirect) {
		return runGroup(redirect, () -> {
			addressService.renameGroup(id, name, authentication.getName());
			return "그룹 이름을 '" + name.trim() + "'(으)로 바꿨습니다.";
		});
	}

	@PostMapping("/groups/{id}/delete")
	public String deleteGroup(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
		return runGroup(redirect, () -> "'" + addressService.deleteGroup(id, authentication.getName())
			+ "' 그룹을 삭제했습니다. 소속 연락처는 그룹 없음으로 남습니다.");
	}

	private String runGroup(RedirectAttributes redirect, java.util.function.Supplier<String> action) {
		try {
			redirect.addFlashAttribute("message", action.get());
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/address/groups";
	}

	/** 좌측 메뉴 데이터를 넣고 view 를 돌려준다 */
	private String nav(String menu, Authentication authentication, Model model, String view) {
		model.addAttribute("menu", menu);
		model.addAttribute("nav", addressService.personalNav(authentication.getName()));
		return view;
	}

	/** 목록으로 돌아갈 주소. 주소록 안의 경로만 허용한다 */
	private static String back(String back, String fallback) {
		if (back == null || !back.startsWith("/address/") || back.contains("//") || back.contains("\\")) {
			return fallback;
		}
		return back;
	}
}
