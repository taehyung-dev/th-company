package th_prj.th_company.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import th_prj.th_company.admin.AdminNav.Item;
import th_prj.th_company.admin.AdminNav.Section;
import th_prj.th_company.common.SecurityUtils;
import th_prj.th_company.member.Permission;
import th_prj.th_company.org.HrCodeType;
import th_prj.th_company.web.HolidayController;
import th_prj.th_company.web.LeaveManageController;

/**
 * 관리자 화면 공통 모델 (상단·좌측 메뉴). 사용자명은 GlobalModelAdvice 가 넣는다.
 * 근태 화면은 근태 메뉴와 함께 쓰는 컨트롤러라서 따로 지정한다.
 */
@ControllerAdvice(basePackageClasses = AdminModelAdvice.class,
	assignableTypes = {HolidayController.class, LeaveManageController.class})
public class AdminModelAdvice {

	/** 권한이 있는 메뉴만 담는다. 메뉴가 하나도 없는 구분은 상단에 나오지 않는다 */
	@ModelAttribute("adminNav")
	public AdminNav adminNav(Authentication authentication) {
		boolean admin = SecurityUtils.isAdmin(authentication);
		List<Section> sections = new ArrayList<>();
		if (admin) {
			List<Item> hr = new ArrayList<>();
			hr.add(new Item("members", "사용자 관리", "/admin/members"));
			hr.add(new Item("departments", "부서 관리", "/admin/departments"));
			for (HrCodeType t : HrCodeType.values()) {
				hr.add(new Item("codes-" + t.getPath(), t.getLabel() + " 관리", "/admin/codes/" + t.getPath()));
			}
			sections.add(new Section("인사정보", hr));
			sections.add(new Section("권한", List.of(new Item("permissions", "권한 관리", "/admin/permissions"))));
			sections.add(new Section("근태", List.of(
				new Item("holidays", "공휴일 관리", "/admin/attendance/holidays"),
				new Item("leave-manage", "휴가 현황", "/admin/attendance/leave"))));
		}
		if (SecurityUtils.can(authentication, Permission.BOARD_MANAGER)) {
			sections.add(new Section("게시판", List.of(new Item("boards", "게시판 관리", "/admin/boards"))));
		}
		if (SecurityUtils.can(authentication, Permission.APPROVAL_MANAGER)) {
			sections.add(new Section("전자결재", List.of(new Item("approval-templates", "결재 양식 관리", "/admin/approval-templates"))));
		}
		List<Item> reservation = new ArrayList<>();
		if (SecurityUtils.can(authentication, Permission.ROOM_MANAGER)) {
			reservation.add(new Item("rooms", "회의실 관리", "/admin/rooms"));
		}
		if (SecurityUtils.can(authentication, Permission.RESOURCE_MANAGER)) {
			reservation.add(new Item("resources", "자원 관리", "/admin/resources"));
		}
		if (!reservation.isEmpty()) {
			sections.add(new Section("예약", reservation));
		}
		if (admin) {
			sections.add(new Section("화면", List.of(new Item("menus", "메뉴 순서", "/admin/menus"))));
		}
		return new AdminNav(sections);
	}
}
