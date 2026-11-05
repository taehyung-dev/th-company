package th_prj.th_company.admin;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import th_prj.th_company.org.HrCodeType;

/**
 * 관리자 화면 공통 모델 (좌측 메뉴용 코드 종류). 사용자명은 GlobalModelAdvice 가 넣는다.
 */
@ControllerAdvice(basePackageClasses = AdminModelAdvice.class)
public class AdminModelAdvice {

	@ModelAttribute("codeTypes")
	public HrCodeType[] codeTypes() {
		return HrCodeType.values();
	}
}
