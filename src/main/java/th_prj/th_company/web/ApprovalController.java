package th_prj.th_company.web;

import java.util.List;

import org.springframework.data.domain.Page;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import th_prj.th_company.approval.ApprovalBox;
import th_prj.th_company.approval.ApprovalService;
import th_prj.th_company.approval.ApprovalTemplate;
import th_prj.th_company.approval.DelegationForm;
import th_prj.th_company.approval.DelegationService;
import th_prj.th_company.approval.DocRow;
import th_prj.th_company.approval.DraftForm;
import th_prj.th_company.common.BusinessException;

/**
 * 전자결재: 좌측 문서함(미결/진행/완결/보관함) + 기안 작성 + 문서 상세/결재.
 */
@Controller
@RequestMapping("/approval")
public class ApprovalController {

	private final ApprovalService approvalService;
	private final DelegationService delegationService;

	public ApprovalController(ApprovalService approvalService, DelegationService delegationService) {
		this.approvalService = approvalService;
		this.delegationService = delegationService;
	}

	@GetMapping
	public String index() {
		return "redirect:/approval/box/pending";
	}

	/** @param page 1부터 */
	@GetMapping("/box/{path}")
	public String box(@PathVariable String path, @RequestParam(defaultValue = "1") int page,
			Authentication authentication, Model model) {
		ApprovalBox box = ApprovalBox.fromPath(path)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		Page<DocRow> docs = approvalService.box(box, authentication.getName(), page - 1);
		prepare(box.getPath(), authentication, model);
		model.addAttribute("box", box);
		model.addAttribute("docs", docs);
		return "approval/list";
	}

	/** 목록의 [결재상태 보기]가 창에 넣는 HTML 조각 */
	@GetMapping("/status")
	public String status(@RequestParam(required = false) List<Long> ids, Authentication authentication, Model model) {
		if (ids == null || ids.isEmpty() || ids.size() > ApprovalService.PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
		}
		model.addAttribute("docs", approvalService.statuses(ids, authentication.getName()));
		return "approval/status :: panel";
	}

	/** 미결문서 일괄결재. 문서마다 따로 처리하므로 일부가 실패해도 나머지는 결재된다 */
	@PostMapping("/bulk-approve")
	public String bulkApprove(@RequestParam(required = false) List<Long> ids, @RequestParam(required = false) String comment,
			Authentication authentication, RedirectAttributes redirect) {
		String back = "redirect:/approval/box/" + ApprovalBox.PENDING.getPath();
		if (ids == null || ids.isEmpty()) {
			redirect.addFlashAttribute("error", "결재할 문서를 선택하세요.");
			return back;
		}
		int approved = 0;
		for (Long id : ids) {
			try {
				approvalService.approve(id, authentication.getName(), comment);
				approved++;
			} catch (BusinessException | ResponseStatusException e) {
				// 그사이 다른 사람이 처리한 문서 등. 건수로만 알린다
			}
		}
		int failed = ids.size() - approved;
		if (approved > 0) {
			redirect.addFlashAttribute("message", approved + "건을 승인했습니다.");
		}
		if (failed > 0) {
			redirect.addFlashAttribute("error", failed + "건은 결재할 수 없어 건너뛰었습니다.");
		}
		return back;
	}

	// ---------- 기안 ----------

	/** 양식이 하나면 바로 작성 화면으로 */
	@GetMapping("/new")
	public String selectTemplate(Authentication authentication, Model model) {
		List<ApprovalTemplate> templates = approvalService.templates();
		if (templates.size() == 1) {
			return "redirect:/approval/new/" + templates.get(0).getId();
		}
		prepare("new", authentication, model);
		model.addAttribute("templates", templates);
		return "approval/select";
	}

	@GetMapping("/new/{templateId}")
	public String newDraft(@PathVariable Long templateId, Authentication authentication, Model model) {
		model.addAttribute("form", approvalService.newDraft(templateId));
		return renderForm(authentication, model);
	}

	@GetMapping("/docs/{id}/edit")
	public String editDraft(@PathVariable Long id, Authentication authentication, Model model) {
		model.addAttribute("form", approvalService.getDraft(id, authentication.getName()));
		return renderForm(authentication, model);
	}

	/** action=save: 임시저장, action=submit: 상신 */
	@PostMapping("/drafts")
	public String saveDraft(@Valid @ModelAttribute("form") DraftForm form, BindingResult bindingResult,
			@RequestParam(defaultValue = "save") String action,
			@RequestParam(required = false) List<MultipartFile> uploads,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderForm(authentication, model);
		}
		try {
			if ("submit".equals(action)) {
				Long id = approvalService.submit(form, authentication.getName(), uploads);
				redirect.addFlashAttribute("message", "상신했습니다.");
				return "redirect:/approval/docs/" + id;
			}
			Long id = approvalService.saveDraft(form, authentication.getName(), uploads);
			redirect.addFlashAttribute("message", "보관함에 임시저장했습니다.");
			return "redirect:/approval/docs/" + id + "/edit";
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderForm(authentication, model);
		}
	}

	@PostMapping("/docs/{id}/delete")
	public String deleteDraft(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
		try {
			approvalService.deleteDraft(id, authentication.getName());
			redirect.addFlashAttribute("message", "문서를 삭제했습니다.");
			return "redirect:/approval/box/drafts";
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/approval/docs/" + id + "/edit";
		}
	}

	// ---------- 상세 / 결재 ----------

	@GetMapping("/docs/{id}")
	public String view(@PathVariable Long id, @RequestParam(required = false) String from,
			Authentication authentication, Model model) {
		model.addAttribute("doc", approvalService.view(id, authentication.getName()));
		model.addAttribute("files", approvalService.files(id));
		approvalService.markCcRead(id, authentication.getName());
		String menu = ApprovalBox.fromPath(from == null ? "" : from).map(ApprovalBox::getPath).orElse(null);
		prepare(menu, authentication, model);
		return "approval/view";
	}

	@PostMapping("/docs/{id}/approve")
	public String approve(@PathVariable Long id, @RequestParam(required = false) String comment,
			Authentication authentication, RedirectAttributes redirect) {
		return decide(id, redirect, "승인했습니다.",
			() -> approvalService.approve(id, authentication.getName(), comment));
	}

	@PostMapping("/docs/{id}/final-approve")
	public String approveFinal(@PathVariable Long id, @RequestParam(required = false) String comment,
			Authentication authentication, RedirectAttributes redirect) {
		return decide(id, redirect, "전결했습니다.",
			() -> approvalService.approveFinal(id, authentication.getName(), comment));
	}

	@PostMapping("/docs/{id}/reject")
	public String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
			Authentication authentication, RedirectAttributes redirect) {
		return decide(id, redirect, "반려했습니다.",
			() -> approvalService.reject(id, authentication.getName(), comment));
	}

	@PostMapping("/docs/{id}/withdraw")
	public String withdraw(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
		try {
			approvalService.withdraw(id, authentication.getName());
			redirect.addFlashAttribute("message", "회수했습니다. 보관함에서 수정 후 다시 상신할 수 있습니다.");
			return "redirect:/approval/docs/" + id + "/edit";
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/approval/docs/" + id;
		}
	}

	@PostMapping("/docs/{id}/redraft")
	public String redraft(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
		try {
			Long newId = approvalService.redraft(id, authentication.getName());
			redirect.addFlashAttribute("message", "재기안 문서를 보관함에 만들었습니다. 내용을 고친 뒤 상신하세요.");
			return "redirect:/approval/docs/" + newId + "/edit";
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
			return "redirect:/approval/docs/" + id;
		}
	}

	// ---------- 대결 설정 ----------

	@GetMapping("/delegation")
	public String delegation(Authentication authentication, Model model) {
		model.addAttribute("form", delegationService.newForm());
		return renderDelegation(authentication, model);
	}

	@PostMapping("/delegation")
	public String saveDelegation(@Valid @ModelAttribute("form") DelegationForm form, BindingResult bindingResult,
			Authentication authentication, Model model, RedirectAttributes redirect) {
		if (bindingResult.hasErrors()) {
			return renderDelegation(authentication, model);
		}
		try {
			delegationService.save(form, authentication.getName());
		} catch (BusinessException e) {
			model.addAttribute("error", e.getMessage());
			return renderDelegation(authentication, model);
		}
		redirect.addFlashAttribute("message", "대결자를 지정했습니다.");
		return "redirect:/approval/delegation";
	}

	@PostMapping("/delegation/{id}/delete")
	public String deleteDelegation(@PathVariable Long id, Authentication authentication, RedirectAttributes redirect) {
		delegationService.delete(id, authentication.getName());
		redirect.addFlashAttribute("message", "대결 지정을 해제했습니다.");
		return "redirect:/approval/delegation";
	}

	private String renderDelegation(Authentication authentication, Model model) {
		prepare("delegation", authentication, model);
		model.addAttribute("mine", delegationService.mine(authentication.getName()));
		model.addAttribute("assigned", delegationService.assignedToMe(authentication.getName()));
		model.addAttribute("approverGroups", approvalService.approverGroups(authentication.getName()));
		return "approval/delegation";
	}

	// ---------- 내부 ----------

	private String decide(Long id, RedirectAttributes redirect, String message, Runnable action) {
		try {
			action.run();
			redirect.addFlashAttribute("message", message);
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/approval/docs/" + id;
	}

	private void prepare(String menu, Authentication authentication, Model model) {
		model.addAttribute("menu", menu);
		model.addAttribute("boxes", ApprovalBox.values());
		model.addAttribute("counts", approvalService.counts(authentication.getName()));
	}

	private String renderForm(Authentication authentication, Model model) {
		DraftForm form = (DraftForm) model.getAttribute("form");
		prepare(form.getId() == null ? "new" : ApprovalBox.DRAFTS.getPath(), authentication, model);
		model.addAttribute("template", approvalService.templateForForm(form));
		model.addAttribute("approverGroups", approvalService.approverGroups(authentication.getName()));
		model.addAttribute("selectedApprovers", approvalService.approverLabels(form.getApproverIds()));
		model.addAttribute("selectedCc", approvalService.approverLabels(form.getCcIds()));
		model.addAttribute("files", form.getId() == null ? List.of() : approvalService.files(form.getId()));
		model.addAttribute("maxCc", ApprovalService.MAX_CC);
		model.addAttribute("drafter", approvalService.drafterInfo(authentication.getName()));
		model.addAttribute("maxApprovers", ApprovalService.MAX_APPROVERS);
		return "approval/form";
	}
}
