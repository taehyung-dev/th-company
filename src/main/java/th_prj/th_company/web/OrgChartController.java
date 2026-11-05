package th_prj.th_company.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import th_prj.th_company.org.OrgChartService;

@Controller
public class OrgChartController {

	private final OrgChartService orgChartService;

	public OrgChartController(OrgChartService orgChartService) {
		this.orgChartService = orgChartService;
	}

	/**
	 * 부서를 지정하지 않으면 본인 부서에 본인이 선택된 상태로, 부서가 없는 사용자(sysadmin)는 회사(맨 위 부서)를 연다.
	 */
	@GetMapping("/org")
	public String org(@RequestParam(required = false) Long dept, Authentication authentication, Model model) {
		model.addAttribute("org", orgChartService.load(dept, authentication.getName()));
		return "org";
	}
}
