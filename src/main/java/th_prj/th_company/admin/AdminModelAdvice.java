package th_prj.th_company.admin;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import th_prj.th_company.web.HolidayController;
import th_prj.th_company.web.LeaveManageController;

/**
 * 관리자 화면 공통 모델 (상단·좌측 메뉴). 사용자명은 GlobalModelAdvice 가 넣는다.
 * 근태 화면은 근태 메뉴와 함께 쓰는 컨트롤러라서 따로 지정한다.
 */
@ControllerAdvice(basePackageClasses = AdminModelAdvice.class,
	assignableTypes = {HolidayController.class, LeaveManageController.class})
public class AdminModelAdvice {

	private final AdminNavService adminNavService;

	public AdminModelAdvice(AdminNavService adminNavService) {
		this.adminNavService = adminNavService;
	}

	@ModelAttribute("adminNav")
	public AdminNav adminNav(Authentication authentication) {
		return adminNavService.build(authentication);
	}
}
