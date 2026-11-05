package th_prj.th_company.admin;

import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.DuplicateValueException;
import th_prj.th_company.member.MemberAdminService;
import th_prj.th_company.member.MemberForm;
import th_prj.th_company.member.ProfilePhotoService;
import th_prj.th_company.member.Role;
import th_prj.th_company.org.DepartmentService;
import th_prj.th_company.org.HrCodeService;
import th_prj.th_company.org.HrCodeType;

@Controller
@RequestMapping("/admin/members")
public class MemberAdminController {

	private static final String FORM_VIEW = "admin/member-form";

	private final MemberAdminService memberAdminService;
	private final DepartmentService departmentService;
	private final HrCodeService hrCodeService;
	private final ProfilePhotoService profilePhotoService;

	public MemberAdminController(MemberAdminService memberAdminService, DepartmentService departmentService,
			HrCodeService hrCodeService, ProfilePhotoService profilePhotoService) {
		this.memberAdminService = memberAdminService;
		this.departmentService = departmentService;
		this.hrCodeService = hrCodeService;
		this.profilePhotoService = profilePhotoService;
	}

	/**
	 * @param status active(재직) / inactive(사용 중지) / 빈 값(전체)
	 */
	@GetMapping
	public String list(@RequestParam(required = false) String q,
			@RequestParam(required = false) Long dept,
			@RequestParam(required = false, defaultValue = "") String status,
			Model model) {
		Boolean enabled = switch (status) {
			case "active" -> true;
			case "inactive" -> false;
			default -> null;
		};
		model.addAttribute("menu", "members");
		model.addAttribute("members", memberAdminService.search(q, dept, enabled));
		model.addAttribute("departments", departmentService.tree());
		model.addAttribute("q", q);
		model.addAttribute("dept", dept);
		model.addAttribute("status", status);
		return "admin/members";
	}

	@GetMapping("/new")
	public String createForm(Model model) {
		model.addAttribute("form", new MemberForm());
		return renderForm(model);
	}

	/** 사진은 먼저 변환해 보고, 이미지가 아니면 사용자 정보도 저장하지 않는다 */
	@PostMapping
	public String create(@Valid @ModelAttribute("form") MemberForm form, BindingResult bindingResult,
			@RequestParam(required = false) MultipartFile photo, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(model);
		}
		try {
			byte[] jpeg = profilePhotoService.convert(photo);
			Long id = memberAdminService.create(form);
			profilePhotoService.apply(id, jpeg, false);
			redirect.addFlashAttribute("message", "사용자를 등록했습니다.");
			return "redirect:/admin/members/" + id;
		} catch (BusinessException e) {
			return renderError(e, bindingResult, model);
		}
	}

	/**
	 * [중복확인] 버튼: /admin/members/check?field=email&value=a@b.c&excludeId=3
	 */
	@GetMapping("/check")
	@ResponseBody
	public MemberAdminService.DuplicateCheck check(@RequestParam String field,
			@RequestParam(required = false) String value, @RequestParam(required = false) Long excludeId) {
		try {
			return memberAdminService.check(field, value, excludeId);
		} catch (BusinessException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
		}
	}

	@GetMapping("/{id}")
	public String editForm(@PathVariable Long id, Model model) {
		model.addAttribute("form", memberAdminService.getForm(id));
		return renderForm(model);
	}

	@PostMapping("/{id}")
	public String update(@PathVariable Long id, @Valid @ModelAttribute("form") MemberForm form,
			BindingResult bindingResult, @RequestParam(required = false) MultipartFile photo,
			@RequestParam(defaultValue = "false") boolean removePhoto,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		form.setId(id);
		if (bindingResult.hasErrors()) {
			return renderForm(model);
		}
		try {
			byte[] jpeg = profilePhotoService.convert(photo);
			memberAdminService.update(id, form, authentication.getName());
			profilePhotoService.apply(id, jpeg, removePhoto);
		} catch (BusinessException e) {
			return renderError(e, bindingResult, model);
		}
		redirect.addFlashAttribute("message", "사용자 정보를 저장했습니다.");
		return "redirect:/admin/members/" + id;
	}

	/** 중복 오류는 해당 입력칸 아래에, 그 밖의 오류는 상단에 표시 */
	private String renderError(BusinessException e, BindingResult bindingResult, Model model) {
		if (e instanceof DuplicateValueException dup) {
			bindingResult.rejectValue(dup.getField(), "duplicate", dup.getMessage());
		} else {
			model.addAttribute("error", e.getMessage());
		}
		return renderForm(model);
	}

	private String renderForm(Model model) {
		model.addAttribute("menu", "members");
		model.addAttribute("departments", departmentService.tree());
		model.addAttribute("grades", hrCodeService.list(HrCodeType.GRADE));
		model.addAttribute("duties", hrCodeService.list(HrCodeType.DUTY));
		model.addAttribute("roles", Role.values());
		Object form = model.getAttribute("form");
		if (form instanceof MemberForm f && f.getId() != null) {
			model.addAttribute("photoUrl", memberAdminService.photoUrl(f.getId()));
		}
		return FORM_VIEW;
	}
}
