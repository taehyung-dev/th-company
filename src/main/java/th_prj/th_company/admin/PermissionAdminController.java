package th_prj.th_company.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import th_prj.th_company.common.BusinessException;
import th_prj.th_company.member.Permission;
import th_prj.th_company.member.PermissionAdminService;
import th_prj.th_company.member.PermissionAdminService.GrantResult;
import th_prj.th_company.member.PermissionAdminService.PermissionGroup;

/**
 * 권한 관리: 좌측 권한 목록 | 가운데 조직도(부서/사용자 체크) | 우측 선택한 권한의 대상(부서, 사용자).
 * /admin/permissions?p=board-manager
 */
@Controller
@RequestMapping("/admin/permissions")
public class PermissionAdminController {

	private final PermissionAdminService permissionAdminService;

	public PermissionAdminController(PermissionAdminService permissionAdminService) {
		this.permissionAdminService = permissionAdminService;
	}

	/** @param p 선택한 권한 (없거나 잘못되면 첫 번째 권한) */
	@GetMapping
	public String list(@RequestParam(required = false) String p, Model model) {
		Permission selected = Permission.fromPath(p == null ? "" : p);
		if (selected == null) {
			selected = Permission.values()[0];
		}
		List<PermissionGroup> groups = permissionAdminService.overview();
		Permission current = selected;
		model.addAttribute("menu", "permissions");
		model.addAttribute("groups", groups);
		model.addAttribute("selected", groups.stream().filter(g -> g.permission() == current).findFirst().orElseThrow());
		model.addAttribute("orgTree", permissionAdminService.orgTree());
		return "admin/permissions";
	}

	/** 조직도에서 체크한 부서(deptIds)와 사용자(memberIds)를 대상으로 추가 */
	@PostMapping("/{path}/grant")
	public String grant(@PathVariable String path,
			@RequestParam(required = false) List<Long> deptIds,
			@RequestParam(required = false) List<Long> memberIds,
			@RequestParam(defaultValue = "false") boolean includeSub,
			RedirectAttributes redirect) {
		Permission p = permission(path);
		try {
			GrantResult r = permissionAdminService.grant(p, deptIds, memberIds, includeSub);
			List<String> parts = new ArrayList<>();
			if (r.departments() > 0) {
				parts.add("부서 " + r.departments() + "개" + (includeSub ? "(하위 부서 포함)" : ""));
			}
			if (r.members() > 0) {
				parts.add("사용자 " + r.members() + "명");
			}
			String message = parts.isEmpty()
				? "추가할 대상이 없습니다. 이미 대상이거나 사용 중지된 사용자입니다."
				: p.getLabel() + "에 " + String.join(", ", parts) + "을(를) 추가했습니다."
					+ (r.skipped() > 0 ? " (이미 대상인 " + r.skipped() + "건 제외)" : "")
					+ " 다음 로그인부터 적용됩니다.";
			redirect.addFlashAttribute(parts.isEmpty() ? "error" : "message", message);
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return redirectTo(p);
	}

	@PostMapping("/{path}/revoke")
	public String revoke(@PathVariable String path, @RequestParam Long memberId, RedirectAttributes redirect) {
		Permission p = permission(path);
		return run(p, redirect, () -> permissionAdminService.revoke(p, memberId) + "님의 " + p.getLabel() + " 권한을 해제했습니다.");
	}

	@PostMapping("/{path}/revoke-dept")
	public String revokeDept(@PathVariable String path, @RequestParam Long deptId, RedirectAttributes redirect) {
		Permission p = permission(path);
		return run(p, redirect, () -> permissionAdminService.revokeDepartment(p, deptId) + " 부서의 " + p.getLabel() + " 권한을 해제했습니다.");
	}

	@PostMapping("/{path}/dept-sub")
	public String changeIncludeSub(@PathVariable String path, @RequestParam Long deptId,
			@RequestParam boolean includeSub, RedirectAttributes redirect) {
		Permission p = permission(path);
		return run(p, redirect, () -> permissionAdminService.changeIncludeSub(p, deptId, includeSub)
			+ (includeSub ? " 부서에 하위 부서를 포함했습니다." : " 부서에서 하위 부서를 제외했습니다."));
	}

	private String run(Permission p, RedirectAttributes redirect, java.util.function.Supplier<String> action) {
		try {
			redirect.addFlashAttribute("message", action.get() + " 다음 로그인부터 적용됩니다.");
		} catch (BusinessException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return redirectTo(p);
	}

	private static String redirectTo(Permission p) {
		return "redirect:/admin/permissions?p=" + p.getPath();
	}

	private static Permission permission(String path) {
		Permission p = Permission.fromPath(path);
		if (p == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return p;
	}
}
